package io.arhome.capabilities

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import com.google.ar.core.ArCoreApk
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class CapabilityCollector(private val context: Context) {

    fun collect(): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("capturedAt", Instant.now().toString())
        put("device", device())
        put("cameras", cameras())
        put("sensors", sensors())
        put("wifiRtt", context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT))
        put("arCore", arCore())
        put("runtime", runtime())
    }

    private fun device() = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER)
        put("brand", Build.BRAND)
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("product", Build.PRODUCT)
        put("hardware", Build.HARDWARE)
        put("androidRelease", Build.VERSION.RELEASE)
        put("sdkInt", Build.VERSION.SDK_INT)
        put("securityPatch", Build.VERSION.SECURITY_PATCH)
    }

    private fun cameras(): JSONArray {
        val manager = context.getSystemService(CameraManager::class.java)
        return JSONArray().apply {
            manager.cameraIdList.forEach { id ->
                try {
                    val c = manager.getCameraCharacteristics(id)
                    val facing = c[CameraCharacteristics.LENS_FACING]
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        put(JSONObject().apply {
                            put("id", id)
                            put("lensFacing", "BACK")
                            put("hardwareLevel", hardwareLevel(c[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]))
                            put("sensorOrientationDegrees", c[CameraCharacteristics.SENSOR_ORIENTATION])
                            put("focalLengthsMm", floats(c[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]))
                            put("physicalSizeMm", c[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.let {
                                JSONObject().put("width", it.width).put("height", it.height)
                            } ?: JSONObject.NULL)
                            put("intrinsicCalibration", if (Build.VERSION.SDK_INT >= 28) {
                                floats(c[CameraCharacteristics.LENS_INTRINSIC_CALIBRATION])
                            } else JSONObject.NULL)
                            put("aeFpsRanges", JSONArray().apply {
                                c[CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]
                                    ?.forEach { put(JSONObject().put("min", it.lower).put("max", it.upper)) }
                            })
                            put("yuv420Sizes", JSONArray().apply {
                                c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                                    ?.getOutputSizes(ImageFormat.YUV_420_888)
                                    ?.sortedByDescending { it.width.toLong() * it.height }
                                    ?.forEach { put("${it.width}x${it.height}") }
                            })
                        })
                    }
                } catch (e: Exception) {
                    put(JSONObject().put("id", id).put("error", "${e.javaClass.simpleName}: ${e.message}"))
                }
            }
        }
    }

    private fun sensors(): JSONObject {
        val manager = context.getSystemService(SensorManager::class.java)
        return JSONObject().apply {
            listOf(
                "accelerometer" to Sensor.TYPE_ACCELEROMETER,
                "gyroscope" to Sensor.TYPE_GYROSCOPE,
                "rotationVector" to Sensor.TYPE_ROTATION_VECTOR,
                "magneticField" to Sensor.TYPE_MAGNETIC_FIELD,
            ).forEach { (name, type) ->
                val sensor = manager.getDefaultSensor(type)
                put(name, sensor?.let { sensorJson(it) } ?: JSONObject.NULL)
            }
        }
    }

    private fun sensorJson(sensor: Sensor) = JSONObject().apply {
        put("name", sensor.name)
        put("vendor", sensor.vendor)
        put("version", sensor.version)
        put("resolution", sensor.resolution)
        put("maximumRange", sensor.maximumRange)
        put("minDelayUs", sensor.minDelay)
        put("powerMa", sensor.power)
        put("reportingMode", sensor.reportingMode)
        put("wakeUp", sensor.isWakeUpSensor)
    }

    private fun arCore() = JSONObject().apply {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            put("availability", availability.name)
            put("supported", availability.isSupported)
            put("transient", availability.isTransient)
        } catch (e: Exception) {
            put("availability", "ERROR")
            put("error", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun runtime() = JSONObject().apply {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperatureTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (temperatureTenths != null && temperatureTenths != Int.MIN_VALUE) {
            put("batteryTemperatureC", temperatureTenths / 10.0)
        } else {
            put("batteryTemperatureC", JSONObject.NULL)
        }
        val runtime = Runtime.getRuntime()
        put("memoryMaxBytes", runtime.maxMemory())
        put("memoryTotalBytes", runtime.totalMemory())
        put("memoryFreeBytes", runtime.freeMemory())
        put("availableProcessors", runtime.availableProcessors())
    }

    private fun floats(values: FloatArray?): Any =
        values?.let { JSONArray().apply { it.forEach(::put) } } ?: JSONObject.NULL

    private fun hardwareLevel(value: Int?): Any = when (value) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        null -> JSONObject.NULL
        else -> "UNKNOWN($value)"
    }
}
