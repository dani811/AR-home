package io.arhome.localizer.capture

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import io.arhome.capabilities.ActiveArCoreProbeView
import io.arhome.localizer.localization.OrbKeyframeLocalizationProvider
import io.arhome.localizer.localization.WorldAlignment
import io.arhome.localizer.map.PersistentMap
import io.arhome.localizer.map.PersistentMapStore
import java.io.File

class MapCaptureActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var mapStore: PersistentMapStore
    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var arView: ActiveArCoreProbeView? = null
    private var capture: MapCaptureSession? = null
    private var persistentMap: PersistentMap? = null
    private var relocalizer: OrbKeyframeLocalizationProvider? = null
    @Volatile private var alignment: WorldAlignment? = null
    @Volatile private var worldCameraPose: Pose? = null
    @Volatile private var relocalizationMs: Long? = null
    private var relocalizationStartedNs = 0L
    private var finalStatus = "Ready. No QR or marker is required."

    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            if (session != null) handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapStore = PersistentMapStore(File(filesDir, "persistent-map"))
        persistentMap = mapStore.currentOrNull()
        persistentMap?.let {
            finalStatus = "Persistent map ready: ${it.sessionId} · ${it.keyframes.size} keyframes."
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "AR Home Localizer"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Map a room from natural visual features, persist the ZIP, then relocalize from a fresh ARCore session without QR or markers."
            textSize = 14f
        })
        root.addView(button("Start mapping") { ensureCameraAndStart() })
        root.addView(button("Stop and export map") { stopAndExport() })
        root.addView(button("Import persistent map ZIP") { selectMapArchive() })
        root.addView(button("Start fresh-session relocalization") { ensureCameraAndRelocalize() })
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
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            finalStatus = "Camera permission is required."
            renderStatus()
            return
        }
        when (requestCode) {
            CAMERA_REQUEST -> startMapping()
            RELOCALIZATION_CAMERA_REQUEST -> startRelocalization()
        }
    }

    @Deprecated("Uses platform document picker for map import")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != MAP_IMPORT_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: run {
            finalStatus = "Map import failed: no file was returned."
            renderStatus()
            return
        }
        try {
            val input = contentResolver.openInputStream(uri) ?: error("Android could not open the selected ZIP")
            val map = input.use(mapStore::import)
            persistentMap = map
            finalStatus = "Persistent map imported: ${map.sessionId} · ${map.keyframes.size} keyframes. It will survive a full app restart."
        } catch (e: Exception) {
            finalStatus = "Map import failed: ${e.javaClass.simpleName}: ${e.message}"
        }
        renderStatus()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun ensureCameraAndStart() {
        if (session != null) {
            finalStatus = "An ARCore session is already running. Restart the app before starting another mode."
            renderStatus()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        } else {
            startMapping()
        }
    }

    private fun ensureCameraAndRelocalize() {
        if (persistentMap == null) {
            finalStatus = "Import a persistent map ZIP before relocalizing."
            renderStatus()
            return
        }
        if (session != null) {
            finalStatus = "A fresh ARCore session is required. Fully restart the app, then tap relocalization."
            renderStatus()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), RELOCALIZATION_CAMERA_REQUEST)
        } else {
            startRelocalization()
        }
    }

    private fun startMapping() {
        try {
            val newSession = createSession("Start mapping") ?: return
            val view = createView(newSession)
            val mapCapture = MapCaptureSession(this)
            view.setTrackedFrameConsumer(mapCapture::onFrame)
            attachSession(newSession, view)
            capture = mapCapture
            finalStatus = "Mapping started. Move naturally and inspect furniture from multiple angles."
        } catch (e: Exception) {
            resetFailedSession("Map capture failed", e)
        }
    }

    private fun startRelocalization() {
        val map = persistentMap ?: return
        try {
            val newSession = createSession("Start relocalization") ?: return
            val view = createView(newSession)
            val provider = OrbKeyframeLocalizationProvider()
            alignment = null
            worldCameraPose = null
            relocalizationMs = null
            relocalizationStartedNs = SystemClock.elapsedRealtimeNanos()
            view.setTrackedFrameConsumer { frame ->
                val currentAlignment = alignment
                if (currentAlignment == null) {
                    provider.localize(map, frame)?.let { result ->
                        val created = WorldAlignment.fromLocalization(result, frame.camera.pose)
                        alignment = created
                        worldCameraPose = created.worldCameraPose(frame.camera.pose)
                        relocalizationMs = (SystemClock.elapsedRealtimeNanos() - relocalizationStartedNs) / 1_000_000L
                    }
                } else {
                    worldCameraPose = currentAlignment.worldCameraPose(frame.camera.pose)
                }
            }
            relocalizer = provider
            attachSession(newSession, view)
            finalStatus = "Fresh-session geometric relocalization running. Look at the mapped furniture and surrounding room detail."
        } catch (e: Exception) {
            resetFailedSession("Relocalization failed", e)
        }
    }

    private fun createSession(action: String): Session? {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        check(!availability.isTransient && availability.isSupported) { "ARCore unavailable: ${availability.name}" }
        if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
            val install = ArCoreApk.getInstance().requestInstall(this, true)
            finalStatus = "ARCore install/update result: $install. Tap $action again after returning."
            renderStatus()
            return null
        }
        return Session(this).also { newSession ->
            val config = newSession.config
            if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
            newSession.configure(config)
        }
    }

    private fun createView(newSession: Session) = ActiveArCoreProbeView(
        this,
        newSession,
        newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC),
        newSession.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY),
    )

    private fun attachSession(newSession: Session, view: ActiveArCoreProbeView) {
        root.addView(view, 2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        session = newSession
        arView = view
        newSession.resume()
        view.onResume()
        handler.post(refresh)
    }

    private fun resetFailedSession(prefix: String, error: Exception) {
        session?.close()
        session = null
        arView = null
        capture = null
        relocalizer = null
        finalStatus = "$prefix: ${error.javaClass.simpleName}: ${error.message}"
        renderStatus()
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
            finalStatus = "Map complete: ${result.keyframeCount} keyframes\nSaved: $publicLocation\nFully restart the app before the fresh-session test."
            renderStatus()
        } catch (e: Exception) {
            finalStatus = "Map export failed: ${e.javaClass.simpleName}: ${e.message}"
            renderStatus()
        }
    }

    private fun selectMapArchive() {
        if (session != null) {
            finalStatus = "Restart the app before importing a map so no ARCore session is active."
            renderStatus()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            },
            MAP_IMPORT_REQUEST,
        )
    }

    private fun renderStatus() {
        val mapCapture = capture
        val tracking = arView?.snapshot()
        status.text = buildString {
            appendLine(finalStatus)
            if (mapCapture != null) appendLine("Keyframes: ${mapCapture.keyframeCount}")
            relocalizer?.latestStatus?.let { match ->
                appendLine("Matcher: ${match.message}")
                appendLine("Best keyframe: ${match.bestKeyframeId ?: "—"}")
                appendLine("ORB good matches: ${match.goodMatches} · RANSAC inliers: ${match.inliers} · ratio: %.2f".format(match.inlierRatio))
                appendLine("Stable hits: ${match.stableHits} · references: ${match.referenceCount}")
            }
            alignment?.let { accepted ->
                appendLine("RELOCALIZED · keyframe ${accepted.source.matchedKeyframeId} · confidence %.3f · ${relocalizationMs ?: 0} ms".format(accepted.source.confidence))
                worldCameraPose?.translation?.let { xyz ->
                    appendLine("World camera xyz: %.2f, %.2f, %.2f m".format(xyz[0], xyz[1], xyz[2]))
                }
                appendLine("Geometric inliers: ${accepted.source.inlierCount} (ORB + homography RANSAC; 2D→3D PnP is next)")
            }
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
        private const val MAP_IMPORT_REQUEST = 52
        private const val RELOCALIZATION_CAMERA_REQUEST = 53
    }
}
