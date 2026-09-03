package io.arhome.localizer.validation

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.arhome.localizer.localization.CapturedLocalizationFrame
import io.arhome.localizer.localization.PnpLandmarkLocalizationProvider
import io.arhome.localizer.map.PersistentMapLoader
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** Runs only on a private, immutable snapshot; never reads the mutable current map. */
class MapValidationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val started = System.nanoTime()

    private fun checkpoint() {
        if (isStopped) throw CancellationException("Validation stopped")
        check((System.nanoTime() - started) / 1_000_000_000L < 240) {
            "La comprobación superó cuatro minutos. Divide la captura en mapas más pequeños."
        }
    }

    private fun progress(percent: Int, message: String) {
        checkpoint()
        setProgressAsync(Data.Builder().putInt("percent", percent).putString("message", message).build()).get()
    }

    override fun doWork(): Result {
        val directory = File(applicationContext.filesDir, "map-validations/$id")
        val report = JSONObject().put("schemaVersion", ValidationPolicy.VERSION).put("jobId", id.toString())
            .put("scope", "INTERNAL_SAME_SESSION_HOLDOUT")
        try {
            check(OpenCVLoader.initDebug()) { "No se pudo iniciar el motor de visión." }
            progress(1, "Leyendo captura")
            val map = PersistentMapLoader().load(File(directory, "map"))
            report.put("mapSessionId", map.sessionId).put("frameCount", map.keyframes.size)
            report.put("inputFingerprint", File(directory, "fingerprint.txt").readText())
            require(map.keyframes.size in ValidationPolicy.MIN_FRAMES..ValidationPolicy.MAX_FRAMES) {
                "Esta comprobación admite entre ${ValidationPolicy.MIN_FRAMES} y ${ValidationPolicy.MAX_FRAMES} fotos; recibidas: ${map.keyframes.size}."
            }
            val images = JSONArray()
            val weak = ArrayList<String>()
            val orb = ORB.create(1800)
            try {
                map.keyframes.forEachIndexed { index, frame ->
                    progress(5 + index * 25 / map.keyframes.size, "Revisando foto ${index + 1}/${map.keyframes.size}")
                    val gray = Imgcodecs.imread(frame.image.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
                    val laplacian = Mat()
                    val mean = MatOfDouble()
                    val deviation = MatOfDouble()
                    val keypoints = MatOfKeyPoint()
                    val mask = Mat()
                    try {
                        require(!gray.empty()) { "No se puede leer la foto ${frame.id}." }
                        require(frame.imageDimensionsPixels.size == 2 &&
                            gray.cols() == frame.imageDimensionsPixels[0] && gray.rows() == frame.imageDimensionsPixels[1]) {
                            "Dimensiones de imagen y calibración diferentes en ${frame.id}."
                        }
                        require(frame.focalLengthPixels.size == 2 && frame.focalLengthPixels.all { it.isFinite() && it > 0 } &&
                            frame.principalPointPixels.size == 2 && frame.principalPointPixels.all { it.isFinite() } &&
                            frame.pose.translation.all { it.isFinite() } && frame.pose.rotationQuaternion.all { it.isFinite() }) {
                            "Calibración o posición inválida en ${frame.id}."
                        }
                        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
                        Core.meanStdDev(laplacian, mean, deviation)
                        val sharpness = deviation.toArray()[0].let { it * it }
                        orb.detect(gray, keypoints, mask)
                        val count = keypoints.rows()
                        val brightness = Core.mean(gray).`val`[0]
                        // Sharpness is reported, not classified by an uncalibrated universal threshold.
                        if (count < 100) weak += frame.id
                        images.put(JSONObject().put("id", frame.id).put("features", count)
                            .put("laplacianVariance", sharpness).put("meanBrightness", brightness))
                    } finally {
                        gray.release(); laplacian.release(); mean.release(); deviation.release(); keypoints.release(); mask.release()
                    }
                }
            } finally { orb.clear() }
            report.put("images", images).put("weakImages", JSONArray(weak))
            val heldOut = ValidationPolicy.heldOutIndices(map.keyframes.size).toSet()
            val training = map.copy(keyframes = map.keyframes.filterIndexed { index, _ -> index !in heldOut })
            val probes = map.keyframes.filterIndexed { index, _ -> index in heldOut }
            report.put("trainingIds", JSONArray(training.keyframes.map { it.id }))
            report.put("heldOutIds", JSONArray(probes.map { it.id }))
            val trials = JSONArray()
            var recovered = 0
            val failed = ArrayList<String>()
            PnpLandmarkLocalizationProvider().use { provider ->
                progress(35, "Comprobando geometría con fotos de entrenamiento")
                provider.prepare(training, ::checkpoint)
                report.put("trainingLandmarks", provider.latestStatus.landmarks)
                probes.forEachIndexed { index, frame ->
                    progress(55 + index * 40 / probes.size, "Probando vista reservada ${index + 1}/${probes.size}")
                    val result = CapturedLocalizationFrame.fromKeyframe(frame, index).use { provider.localize(training, it) }
                    val trial = JSONObject().put("id", frame.id).put("matches", provider.latestStatus.goodMatches)
                        .put("inliers", provider.latestStatus.pnpInliers)
                    val good = if (result == null) false else {
                        val p = result.worldCameraPose.translation
                        val expected = frame.pose.translation
                        val distance = sqrt(p.indices.sumOf { (p[it] - expected[it]).toDouble().let { d -> d * d } })
                        val q = result.worldCameraPose.rotationQuaternion
                        val eq = frame.pose.rotationQuaternion
                        val dot = abs(q.indices.sumOf { q[it].toDouble() * eq[it] }).coerceIn(0.0, 1.0)
                        val angle = Math.toDegrees(2 * acos(dot))
                        trial.put("positionErrorMeters", distance).put("rotationErrorDegrees", angle)
                        ValidationPolicy.poseConsistent(distance, angle)
                    }
                    if (good) recovered++ else failed += frame.id
                    trials.put(trial.put("recovered", good))
                }
            }
            val passes = ValidationPolicy.passes(probes.size, recovered, weak.size)
            val message = if (passes) {
                "Supera la comprobación interna: $recovered/${probes.size} vistas recuperadas. Falta probar una sesión nueva."
            } else {
                "Necesita más captura o revisión: $recovered/${probes.size} vistas recuperadas. Revisa las fotos marcadas; no repitas toda la habitación a ciegas."
            }
            report.put("trials", trials).put("failedViewIds", JSONArray(failed)).put("recovered", recovered)
                .put("tested", probes.size).put("outcome", if (passes) "INTERNAL_PASS" else "NEEDS_REVIEW")
                .put("message", message).put("policy", JSONObject().put("minimumRecoveryFraction", 0.9)
                    .put("maxPositionErrorMeters", 0.20).put("maxRotationErrorDegrees", 5.0)
                    .put("provisional", true))
            return finish(directory, report, message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return finish(directory, report.put("outcome", "UNABLE_TO_VALIDATE"),
                "No se pudo validar: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: LinkageError) {
            return finish(directory, report.put("outcome", "UNABLE_TO_VALIDATE"), "No se pudo cargar el motor de visión.")
        }
    }

    private fun finish(directory: File, report: JSONObject, message: String): Result {
        if (isStopped) throw CancellationException("Validation stopped")
        report.put("message", message).put("completedAtEpochMs", System.currentTimeMillis())
        val pending = File(directory, "report.json.tmp")
        pending.writeText(report.toString(2))
        check(pending.renameTo(File(directory, "report.json"))) { "No se pudo guardar el informe." }
        return Result.success(Data.Builder().putString("message", message)
            .putString("outcome", report.getString("outcome")).build())
    }
}
