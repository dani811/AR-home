package io.arhome.capabilities

import android.app.Activity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val runButton = Button(this).apply {
            text = "Run passive capability probe"
            setOnClickListener { runProbe() }
        }
        output = TextView(this).apply {
            textSize = 14f
            movementMethod = ScrollingMovementMethod()
        }
        root.addView(runButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(
            output,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
        runProbe()
    }

    private fun runProbe() {
        val report = CapabilityCollector(this).collect()
        val base = getExternalFilesDir(null) ?: filesDir
        val directory = File(base, "capability-reports").apply { mkdirs() }
        val file = File(directory, "capabilities-${System.currentTimeMillis()}.json")
        file.writeText(report.toString(2))

        val cameras = report.getJSONArray("cameras")
        val sensors = report.getJSONObject("sensors")
        val arCore = report.getJSONObject("arCore")

        output.text = buildString {
            appendLine("AR Home Android capability probe")
            appendLine()
            appendLine("Device: ${report.getJSONObject("device").optString("manufacturer")} ${report.getJSONObject("device").optString("model")}")
            appendLine("Android: ${report.getJSONObject("device").optString("androidRelease")} / API ${report.getJSONObject("device").optInt("sdkInt")}")
            appendLine("Rear cameras reported: ${cameras.length()}")
            appendLine("Accelerometer: ${sensors.has("accelerometer") && !sensors.isNull("accelerometer")}")
            appendLine("Gyroscope: ${sensors.has("gyroscope") && !sensors.isNull("gyroscope")}")
            appendLine("Rotation vector: ${sensors.has("rotationVector") && !sensors.isNull("rotationVector")}")
            appendLine("Magnetometer: ${sensors.has("magneticField") && !sensors.isNull("magneticField")}")
            appendLine("Wi-Fi RTT: ${report.optBoolean("wifiRtt")}")
            appendLine("ARCore availability: ${arCore.optString("availability")}")
            appendLine()
            appendLine("Machine-readable report:")
            appendLine(file.absolutePath)
            appendLine()
            appendLine("This passive probe does not open the camera or start an ARCore Session.")
        }
    }
}
