package io.arhome.capabilities

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.io.File

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var output: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var activeProbe: ActiveArCoreProbeView? = null
    private var latestReportName: String? = null
    private var latestReportContent: String? = null

    private val refresh = object : Runnable {
        override fun run() {
            activeProbe?.let { showActiveSummary(it.snapshot()) }
            if (activeProbe != null) handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(button("Run passive capability probe") { runPassiveProbe() })
        root.addView(button("Start active ARCore probe") { ensureCameraThenStart() })
        root.addView(button("Save active report") { saveActiveReport() })
        root.addView(button("Export latest report") { exportLatestReport() })
        root.addView(button("Copy latest JSON") { copyLatestReport() })
        output = TextView(this).apply {
            textSize = 14f
            movementMethod = ScrollingMovementMethod()
        }
        root.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        runPassiveProbe()
    }

    override fun onResume() {
        super.onResume()
        val currentSession = session ?: return
        try {
            currentSession.resume()
            activeProbe?.onResume()
            handler.post(refresh)
        } catch (e: Exception) {
            output.text = "ARCore resume failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        activeProbe?.onPause()
        session?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == CAMERA_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startActiveProbe()
        } else if (requestCode == CAMERA_REQUEST) {
            output.text = "Camera permission denied; active ARCore probe cannot run."
        }
    }

    @Deprecated("Fallback document picker for Android 9 and older")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT_REQUEST) return
        if (resultCode != RESULT_OK) {
            output.append("\nReport export cancelled. The app-private copy is still available.")
            return
        }
        val uri = data?.data
        val content = latestReportContent
        if (uri == null || content == null) {
            output.append("\nReport export failed: no destination or report content returned.")
            return
        }
        try {
            val stream = contentResolver.openOutputStream(uri, "w")
                ?: error("Android could not open the selected destination")
            stream.bufferedWriter().use { it.write(content) }
            output.append("\nExported report: $uri")
        } catch (e: Exception) {
            output.append("\nReport export failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun ensureCameraThenStart() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        } else {
            startActiveProbe()
        }
    }

    private fun startActiveProbe() {
        if (session != null) {
            output.text = "Active ARCore probe is already running."
            return
        }
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            if (availability.isTransient) {
                output.text = "ARCore availability is still being resolved: ${availability.name}. Retry the active probe."
                return
            }
            if (!availability.isSupported) {
                output.text = "ARCore is not supported on this device: ${availability.name}."
                return
            }
            if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                val install = ArCoreApk.getInstance().requestInstall(this, true)
                output.text = "ARCore install/update result: $install. Run the active probe again after returning."
                return
            }

            val newSession = Session(this)
            val automaticDepth = newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            val rawDepth = newSession.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
            val config = newSession.config
            if (automaticDepth) config.depthMode = Config.DepthMode.AUTOMATIC
            newSession.configure(config)

            val probeView = ActiveArCoreProbeView(this, newSession, automaticDepth, rawDepth)
            val heightPx = (220 * resources.displayMetrics.density).toInt()
            root.addView(
                probeView,
                root.childCount - 1,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx),
            )
            session = newSession
            activeProbe = probeView
            newSession.resume()
            probeView.onResume()
            handler.post(refresh)
        } catch (e: Exception) {
            session?.close()
            session = null
            activeProbe = null
            output.text = "Active ARCore probe failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun runPassiveProbe() {
        try {
            val report = CapabilityCollector(this).collect()
            val content = report.toString(2)
            val file = writeReport("capabilities", content)
            rememberReport(file.name, content)
            output.text = buildString {
                val device = report.getJSONObject("device")
                val sensors = report.getJSONObject("sensors")
                appendLine("AR Home Android capability probe")
                appendLine("Device: ${device.optString("manufacturer")} ${device.optString("model")}")
                appendLine("Android: ${device.optString("androidRelease")} / API ${device.optInt("sdkInt")}")
                appendLine("Rear cameras: ${report.getJSONArray("cameras").length()}")
                appendLine("Accelerometer: ${!sensors.isNull("accelerometer")}")
                appendLine("Gyroscope: ${!sensors.isNull("gyroscope")}")
                appendLine("Rotation vector: ${!sensors.isNull("rotationVector")}")
                appendLine("Magnetometer: ${!sensors.isNull("magneticField")}")
                appendLine("Wi-Fi RTT: ${report.optBoolean("wifiRtt")}")
                appendLine("ARCore: ${report.getJSONObject("arCore").optString("availability")}")
                appendLine("Private copy: ${file.absolutePath}")
                appendLine("Tap Export latest report to create a public copy in Downloads/ARHome.")
            }
        } catch (e: Exception) {
            output.text = "Passive report failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun showActiveSummary(active: org.json.JSONObject) {
        output.text = buildString {
            appendLine("ACTIVE ARCORE PROBE")
            appendLine("Frames: ${active.optLong("frameCount")}")
            appendLine("Tracking: ${active.optLong("trackingFrames")} / paused: ${active.optLong("pausedFrames")}")
            appendLine("Failure reason: ${active.optString("trackingFailureReason")}")
            appendLine("Average update Hz: %.1f".format(active.optDouble("averageUpdateHz")))
            appendLine("Max frame gap ms: %.1f".format(active.optDouble("maxFrameGapMs")))
            appendLine("Depth AUTOMATIC: ${active.optBoolean("automaticDepthSupported")}")
            appendLine("Raw depth mode: ${active.optBoolean("rawDepthModeSupported")}")
            appendLine("CPU camera image acquired: ${!active.isNull("cpuCameraImage")}")
            appendLine("Raw depth acquired: ${!active.isNull("rawDepthImage")}")
            appendLine("Confidence image acquired: ${!active.isNull("rawDepthConfidenceImage")}")
            appendLine("Last error: ${active.opt("lastError")}")
            appendLine()
            appendLine("Walk normally for a representative sample, then tap Save active report.")
        }
    }

    private fun saveActiveReport() {
        val active = activeProbe?.snapshot()
        if (active == null) {
            output.text = "Start the active ARCore probe before saving an active report."
            return
        }
        try {
            val report = CapabilityCollector(this).collect().put("activeArCore", active)
            val content = report.toString(2)
            val file = writeReport("active-arcore", content)
            rememberReport(file.name, content)
            showActiveSummary(active)
            output.append("\nPrivate copy saved: ${file.absolutePath}")
            exportLatestReport()
        } catch (e: Exception) {
            output.text = "Active report save failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    @Suppress("DEPRECATION")
    private fun exportLatestReport() {
        val name = latestReportName
        val content = latestReportContent
        if (name == null || content == null) {
            output.text = "No report is available to export yet."
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val uri = writePublicDownload(name, content)
                output.append("\nPublic copy saved: Downloads/ARHome/$name\nURI: $uri")
            } catch (e: Exception) {
                output.append("\nPublic export failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(intent, EXPORT_REQUEST)
    }

    private fun copyLatestReport() {
        val content = latestReportContent
        if (content == null) {
            output.text = "No report is available to copy yet."
            return
        }
        try {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("AR Home capability report", content))
            output.append("\nLatest JSON copied to clipboard.")
        } catch (e: Exception) {
            output.append("\nClipboard copy failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun writePublicDownload(name: String, content: String): android.net.Uri {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ARHome")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values)
            ?: error("Android refused to create the Downloads entry")
        try {
            val stream = contentResolver.openOutputStream(uri, "w")
                ?: error("Android could not open the Downloads entry")
            stream.bufferedWriter().use { it.write(content) }
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            contentResolver.update(uri, publish, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun rememberReport(name: String, content: String) {
        latestReportName = name
        latestReportContent = content
    }

    private fun writeReport(prefix: String, content: String): File {
        val base = getExternalFilesDir(null) ?: filesDir
        val directory = File(base, "capability-reports")
        if (!directory.exists() && !directory.mkdirs()) {
            error("Could not create report directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory) {
            error("Report path is not a directory: ${directory.absolutePath}")
        }
        val file = File(directory, "$prefix-${System.currentTimeMillis()}.json")
        file.writeText(content)
        if (!file.isFile || file.length() == 0L) {
            error("Report file was not written correctly: ${file.absolutePath}")
        }
        return file
    }

    companion object {
        private const val CAMERA_REQUEST = 41
        private const val EXPORT_REQUEST = 42
    }
}
