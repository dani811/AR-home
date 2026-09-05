package io.arhome.localizer.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.arhome.localizer.localization.CapturedLocalizationFrame
import io.arhome.localizer.localization.PnpLandmarkLocalizationProvider
import io.arhome.localizer.map.PersistentMapLoader
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyntheticDepthValidationInstrumentedTest {
    @Test
    fun testGeneratedMapPassesProductionDepthAndPnpPipeline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "synthetic-depth-${System.nanoTime()}")
        try {
            unzipAsset("synthetic-depth-map.zip", root)
            val map = PersistentMapLoader().load(root)
            val heldOut = ValidationPolicy.heldOutIndices(map.keyframes.size).toSet()
            val training = map.copy(keyframes = map.keyframes.filterIndexed { index, _ -> index !in heldOut })
            val probes = map.keyframes.filterIndexed { index, _ -> index in heldOut }
            var recovered = 0
            PnpLandmarkLocalizationProvider().use { provider ->
                provider.prepare(training)
                assertTrue("Synthetic map produced too few landmarks", provider.latestStatus.landmarks >= 24)
                probes.forEachIndexed { index, frame ->
                    val result = CapturedLocalizationFrame.fromKeyframe(frame, index).use { provider.localize(training, it) }
                    assertNotNull("PnP rejected held-out frame ${frame.id}: ${provider.latestStatus}", result)
                    val accepted = checkNotNull(result)
                    val position = accepted.worldCameraPose.translation
                    val expected = frame.pose.translation
                    val distance = sqrt(position.indices.sumOf { (position[it] - expected[it]).toDouble().let { d -> d * d } })
                    val rotation = accepted.worldCameraPose.rotationQuaternion
                    val expectedRotation = frame.pose.rotationQuaternion
                    val dot = abs(rotation.indices.sumOf { rotation[it].toDouble() * expectedRotation[it] }).coerceIn(0.0, 1.0)
                    val angle = Math.toDegrees(2.0 * acos(dot))
                    assertTrue("Frame ${frame.id} position error $distance m", distance <= 0.20)
                    assertTrue("Frame ${frame.id} rotation error $angle degrees", angle <= 5.0)
                    assertTrue("Frame ${frame.id} has ${accepted.inlierCount} inliers", accepted.inlierCount >= 18)
                    recovered++
                }
            }
            assertEquals("Every held-out synthetic view must recover", probes.size, recovered)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun unzipAsset(name: String, root: File) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(root.mkdirs())
        val canonicalRoot = root.canonicalFile.toPath()
        ZipInputStream(instrumentation.context.assets.open(name)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(root, entry.name)
                check(target.canonicalFile.toPath().startsWith(canonicalRoot))
                if (entry.isDirectory) target.mkdirs() else {
                    check(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true)
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }
}
