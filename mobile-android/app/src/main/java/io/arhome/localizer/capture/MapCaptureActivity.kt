package io.arhome.localizer.capture

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import io.arhome.capabilities.ActiveArCoreProbeView
import java.io.File

class MapCaptureActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var arView: ActiveArCoreProbeView? = null
    private var capture: MapCaptureSession? = null
    private var finalStatus = "Ready. No QR or marker is required."

    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            if (session != null) handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "AR Home Localizer · Map Capture"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Walk through the room and look at each piece of furniture from different viewpoints. The capture uses natural visual features, ARCore pose and camera intrinsics."
            textSize = 14f
        })
        root.addView(button("Start mapping") { ensureCameraAndStart() })
        root.addView(button("Stop and export map") { stopAndExport() })
        status = TextView(this).apply { textSize = 14f }
        root.addView(status)
        setContentView(root)
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        val current = session ?: return
        try {
            current.resume()
            arView?.onResume()
            handler.post(refresh)
        } catch (e: Exception) {
            finalStatus = "ARCore resume failed: ${e.javaClass.simpleName}: ${e.message}"
            renderStatus()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        arView?.onPause()
        session?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        arView?.setTrackedFrameConsumer(null)
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == CAMERA_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startMapping()
        } else if (requestCode == CAMERA_REQUEST) {
            finalStatus = "Camera permission is required for visual mapping."
            renderStatus()
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun ensureCameraAndStart() {
        if (capture != null) {
            finalStatus = "A map capture is already running."
            renderStatus()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        } else {
            startMapping()
        }
    }

    private fun startMapping() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            check(!availability.isTransient && availability.isSupported) { "ARCore unavailable: ${availability.name}" }
            if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                val install = ArCoreApk.getInstance().requestInstall(this, true)
                finalStatus = "ARCore install/update result: $install. Tap Start mapping again after returning."
                renderStatus()
                return
            }

            val newSession = Session(this)
            val config = newSession.config
            if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
            newSession.configure(config)

            val view = ActiveArCoreProbeView(
                this,
                newSession,
                newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC),
                newSession.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY),
            )
            val mapCapture = MapCaptureSession(this)
            view.setTrackedFrameConsumer(mapCapture::onFrame)
            root.addView(
                view,
                2,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            session = newSession
            arView = view
            capture = mapCapture
            finalStatus = "Mapping started. Move naturally and inspect furniture from multiple angles."
            newSession.resume()
            view.onResume()
            handler.post(refresh)
        } catch (e: Exception) {
            session?.close()
            session = null
            arView = null
            capture = null
            finalStatus = "Map capture failed: ${e.javaClass.simpleName}: ${e.message}"
            renderStatus()
        }
    }

    private fun stopAndExport() {
        val current = capture ?: run {
            finalStatus = "No map capture is running."
            renderStatus()
            return
        }
        try {
            arView?.setTrackedFrameConsumer(null)
            val result = current.finish()
            capture = null
            val publicLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishArchive(result.archive)
                "Downloads/ARHome/${result.archive.name}"
            } else {
                result.archive.absolutePath
            }
            finalStatus = "Map complete: ${result.keyframeCount} keyframes\nSaved: $publicLocation"
            renderStatus()
        } catch (e: Exception) {
            finalStatus = "Map export failed: ${e.javaClass.simpleName}: ${e.message}"
            renderStatus()
        }
    }

    private fun renderStatus() {
        val map = capture
        val tracking = arView?.snapshot()
        status.text = buildString {
            appendLine(finalStatus)
            if (map != null) appendLine("Keyframes: ${map.keyframeCount}")
            if (tracking != null) {
                appendLine("ARCore tracking frames: ${tracking.optLong("trackingFrames")}")
                appendLine("Tracking failure: ${tracking.optString("trackingFailureReason")}")
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun publishArchive(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ARHome")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values) ?: error("Android refused to create the map archive")
        try {
            contentResolver.openOutputStream(uri, "w")!!.use { output ->
                file.inputStream().use { it.copyTo(output) }
            }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    companion object {
        private const val CAMERA_REQUEST = 51
    }
}
