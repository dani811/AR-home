package io.arhome.capabilities

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import io.arhome.localizer.rendering.CameraBackgroundRenderer
import org.json.JSONArray
import org.json.JSONObject
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ActiveArCoreProbeView(
    context: Context,
    private val session: Session,
    private val automaticDepthSupported: Boolean,
    private val rawDepthSupported: Boolean,
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private val lock = Any()
    private val backgroundRenderer = CameraBackgroundRenderer()
    private var frameCount = 0L
    private var trackingFrames = 0L
    private var pausedFrames = 0L
    private var firstFrameNanos = 0L
    private var lastFrameNanos = 0L
    private var maxFrameGapMs = 0.0
    private var trackingFailureReason = "NONE"
    private var translation = floatArrayOf()
    private var rotation = floatArrayOf()
    private var focalLength = floatArrayOf()
    private var principalPoint = floatArrayOf()
    private var imageDimensions = intArrayOf()
    private var cpuImage: JSONObject? = null
    private var rawDepthImage: JSONObject? = null
    private var confidenceImage: JSONObject? = null
    private var lastError: String? = null

    @Volatile
    private var trackedFrameConsumer: ((Frame) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        backgroundRenderer.create(session)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session.setDisplayGeometry(display?.rotation ?: 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val now = SystemClock.elapsedRealtimeNanos()
        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            synchronized(lock) { lastError = "CameraNotAvailableException: ${e.message}" }
            return
        } catch (e: Exception) {
            synchronized(lock) { lastError = "${e.javaClass.simpleName}: ${e.message}" }
            return
        }

        backgroundRenderer.draw(frame)
        val camera = frame.camera
        synchronized(lock) {
            frameCount++
            if (firstFrameNanos == 0L) firstFrameNanos = now
            if (lastFrameNanos != 0L) {
                maxFrameGapMs = maxOf(maxFrameGapMs, (now - lastFrameNanos) / 1_000_000.0)
            }
            lastFrameNanos = now
            trackingFailureReason = camera.trackingFailureReason.name
            if (camera.trackingState == TrackingState.TRACKING) {
                trackingFrames++
                translation = camera.pose.translation
                rotation = camera.pose.rotationQuaternion
                val intrinsics = camera.imageIntrinsics
                focalLength = intrinsics.focalLength
                principalPoint = intrinsics.principalPoint
                imageDimensions = intrinsics.imageDimensions
            } else {
                pausedFrames++
            }
        }

        if (camera.trackingState == TrackingState.TRACKING) {
            acquireCpuImage(frame)
            if (automaticDepthSupported) acquireRawDepth(frame)
            try {
                trackedFrameConsumer?.invoke(frame)
            } catch (e: Exception) {
                synchronized(lock) { lastError = "Frame consumer: ${e.javaClass.simpleName}: ${e.message}" }
            }
        }
    }

    private fun acquireCpuImage(frame: Frame) {
        synchronized(lock) { if (cpuImage != null) return }
        try {
            frame.acquireCameraImage().use { image ->
                synchronized(lock) {
                    cpuImage = JSONObject()
                        .put("width", image.width)
                        .put("height", image.height)
                        .put("format", image.format)
                        .put("timestampNs", image.timestamp)
                }
            }
        } catch (_: NotYetAvailableException) {
        } catch (e: Exception) {
            synchronized(lock) { lastError = "CPU image: ${e.javaClass.simpleName}: ${e.message}" }
        }
    }

    private fun acquireRawDepth(frame: Frame) {
        val needDepth = synchronized(lock) { rawDepthImage == null || confidenceImage == null }
        if (!needDepth) return
        try {
            if (synchronized(lock) { rawDepthImage == null }) {
                frame.acquireRawDepthImage16Bits().use { image ->
                    synchronized(lock) {
                        rawDepthImage = JSONObject()
                            .put("width", image.width)
                            .put("height", image.height)
                            .put("timestampNs", image.timestamp)
                    }
                }
            }
            if (synchronized(lock) { confidenceImage == null }) {
                frame.acquireRawDepthConfidenceImage().use { image ->
                    synchronized(lock) {
                        confidenceImage = JSONObject()
                            .put("width", image.width)
                            .put("height", image.height)
                            .put("timestampNs", image.timestamp)
                    }
                }
            }
        } catch (_: NotYetAvailableException) {
        } catch (e: Exception) {
            synchronized(lock) { lastError = "Raw depth: ${e.javaClass.simpleName}: ${e.message}" }
        }
    }

    fun setTrackedFrameConsumer(consumer: ((Frame) -> Unit)?) {
        trackedFrameConsumer = consumer
    }

    fun snapshot(): JSONObject = synchronized(lock) {
        val durationSeconds = if (firstFrameNanos > 0L && lastFrameNanos > firstFrameNanos) {
            (lastFrameNanos - firstFrameNanos) / 1_000_000_000.0
        } else 0.0
        JSONObject().apply {
            put("automaticDepthSupported", automaticDepthSupported)
            put("rawDepthModeSupported", rawDepthSupported)
            put("frameCount", frameCount)
            put("trackingFrames", trackingFrames)
            put("pausedFrames", pausedFrames)
            put("trackingFailureReason", trackingFailureReason)
            put("durationSeconds", durationSeconds)
            put("averageUpdateHz", if (durationSeconds > 0) (frameCount - 1) / durationSeconds else 0.0)
            put("maxFrameGapMs", maxFrameGapMs)
            put("poseTranslationMeters", JSONArray(translation.toList()))
            put("poseRotationQuaternion", JSONArray(rotation.toList()))
            put("imageFocalLengthPixels", JSONArray(focalLength.toList()))
            put("imagePrincipalPointPixels", JSONArray(principalPoint.toList()))
            put("imageDimensionsPixels", JSONArray(imageDimensions.toList()))
            put("cpuCameraImage", cpuImage ?: JSONObject.NULL)
            put("rawDepthImage", rawDepthImage ?: JSONObject.NULL)
            put("rawDepthConfidenceImage", confidenceImage ?: JSONObject.NULL)
            put("lastError", lastError ?: JSONObject.NULL)
        }
    }
}
