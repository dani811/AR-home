package io.arhome.localizer.capture

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.widget.ScrollView
import android.widget.ImageView
import android.graphics.BitmapFactory
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.arhome.localizer.validation.MapValidationJobs
import org.json.JSONObject
import java.util.UUID
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
import io.arhome.localizer.localization.CapturedLocalizationFrame
import io.arhome.localizer.localization.OrbKeyframeLocalizationProvider
import io.arhome.localizer.localization.PnpLandmarkLocalizationProvider
import io.arhome.localizer.localization.WorldAlignment
import io.arhome.localizer.map.PersistentMap
import io.arhome.localizer.map.PersistentMapStore
import java.io.File
import java.util.concurrent.Executors

class MapCaptureActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var validationStatus: TextView
    private var validationId: UUID? = null
    private var validationLiveData: androidx.lifecycle.LiveData<WorkInfo?>? = null
    private var validationObserver: Observer<WorkInfo?>? = null
    private var importing = false
    private var validationPending = false
    private var validationOutcome: String? = null
    private lateinit var mapStore: PersistentMapStore
    private val handler = Handler(Looper.getMainLooper())
    private val localizationExecutor = Executors.newSingleThreadExecutor()
    private var session: Session? = null
    private var arView: ActiveArCoreProbeView? = null
    private var capture: MapCaptureSession? = null
    private var persistentMap: PersistentMap? = null
    private var orbRelocalizer: OrbKeyframeLocalizationProvider? = null
    private var pnpRelocalizer: PnpLandmarkLocalizationProvider? = null
    @Volatile private var pnpEnabled = false
    @Volatile private var pnpDisabledReason: String? = null
    @Volatile private var relocalizationPreparing = false
    @Volatile private var localizationInFlight = false
    @Volatile private var localizationRuntimeError: String? = null
    @Volatile private var acceptedPoseSource: String? = null
    @Volatile private var alignment: WorldAlignment? = null
    @Volatile private var worldCameraPose: Pose? = null
    @Volatile private var relocalizationMs: Long? = null
    @Volatile private var preparationMs: Long? = null
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
            finalStatus = "Mapa guardado (consulta su validación): ${it.sessionId} · ${it.keyframes.size} keyframes."
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
        validationStatus = TextView(this).apply { textSize = 15f; text = "Sin mapa validado." }
        root.addView(validationStatus)
        root.addView(button("Ver informe y fotos a revisar") { showValidationReport() })
        status = TextView(this).apply { textSize = 14f }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
        renderStatus()
        MapValidationJobs.currentId(this, persistentMap)?.let(::observeValidation)
        if (validationId == null && persistentMap != null) {
            queueValidation(persistentMap!!)
        }
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
        validationObserver?.let { validationLiveData?.removeObserver(it) }
        handler.removeCallbacks(refresh)
        arView?.setTrackedFrameConsumer(null)
        localizationExecutor.shutdownNow()
        capture?.close()
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
        if (requestCode == REPORT_EXPORT_REQUEST && resultCode == RESULT_OK) {
            try {
                val id = validationId ?: return
                val uri = data?.data ?: return
                contentResolver.openOutputStream(uri)?.use { output ->
                    File(MapValidationJobs.directory(this, id), "report.json").inputStream().use { it.copyTo(output) }
                } ?: error("No se pudo abrir el destino")
                validationStatus.append("\nInforme exportado.")
            } catch (e: Exception) { validationStatus.append("\nError de exportación: ${e.message}") }
            return
        }
        if (requestCode != MAP_IMPORT_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: run {
            finalStatus = "Map import failed: no file was returned."
            renderStatus()
            return
        }
        validationObserver?.let { validationLiveData?.removeObserver(it) }
        validationObserver = null
        validationId = null
        importing = true
        validationPending = true
        validationOutcome = null
        validationStatus.text = "Importando mapa y preparando comprobación…"
        localizationExecutor.execute {
            try {
                val input = contentResolver.openInputStream(uri) ?: error("No se pudo abrir el ZIP")
                val map = input.use(mapStore::import)
                val jobId = MapValidationJobs.enqueue(applicationContext, map)
                handler.post {
                    if (isDestroyed) return@post
                    importing = false
                    persistentMap = map
                    finalStatus = "Mapa importado. La comprobación continúa aunque salgas de la app."
                    observeValidation(jobId)
                    renderStatus()
                }
            } catch (e: Exception) {
                handler.post {
                    if (isDestroyed) return@post
                    importing = false
                    validationPending = false
                    validationStatus.text = "No se pudo preparar la validación: ${e.message}"
                    finalStatus = "Importación o preparación fallida. Vuelve a importar el ZIP."
                    renderStatus()
                }
            }
        }
    }

    private fun queueValidation(map: PersistentMap) {
        validationPending = true
        validationOutcome = null
        importing = true
        validationStatus.text = "Preparando comprobación del mapa…"
        localizationExecutor.execute {
            try {
                val jobId = MapValidationJobs.enqueue(applicationContext, map)
                handler.post { if (!isDestroyed) { importing = false; observeValidation(jobId) } }
            } catch (e: Exception) {
                handler.post { if (!isDestroyed) {
                    importing = false
                    validationPending = false
                    validationStatus.text = "No se pudo preparar la validación: ${e.message}"
                } }
            }
        }
    }

    private fun observeValidation(id: UUID) {
        validationObserver?.let { validationLiveData?.removeObserver(it) }
        validationId = id
        val data = WorkManager.getInstance(this).getWorkInfoByIdLiveData(id)
        val observer = Observer<WorkInfo?> { info ->
            if (info == null) {
                val saved = File(MapValidationJobs.directory(this, id), "report.json")
                val report = runCatching { JSONObject(saved.readText()) }.getOrNull()
                validationPending = false
                validationOutcome = report?.optString("outcome")
                validationStatus.text = report?.optString("message") ?: "No hay una comprobación disponible. Vuelve a importar el mapa."
                return@Observer
            }
            validationPending = !info.state.isFinished
            validationOutcome = info.outputData.getString("outcome")
            validationStatus.text = when (info.state) {
                WorkInfo.State.SUCCEEDED -> info.outputData.getString("message") ?: "Consulta el informe."
                WorkInfo.State.FAILED -> "No se pudo completar la comprobación. Vuelve a importar el mapa."
                WorkInfo.State.CANCELLED -> "Comprobación cancelada. Vuelve a importar el mapa."
                WorkInfo.State.RUNNING -> "Comprobando mapa: ${info.progress.getInt("percent", 0)}% · ${info.progress.getString("message") ?: "Preparando"}"
                else -> "Comprobación pendiente. Android la ejecutará cuando pueda; puedes salir de la app."
            }
        }
        validationLiveData = data
        validationObserver = observer
        data.observeForever(observer)
    }

    private fun showValidationReport() {
        val id = validationId ?: return
        val directory = MapValidationJobs.directory(this, id)
        val file = File(directory, "report.json")
        if (!file.isFile) {
            AlertDialog.Builder(this).setMessage("El informe estará disponible al terminar la comprobación.").setPositiveButton("Aceptar", null).show()
            return
        }
        try {
            val report = JSONObject(file.readText())
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
            panel.addView(TextView(this).apply {
                val depthFrames = report.optInt("depthFrameCount", 0)
                val frameCount = report.optInt("frameCount", 0)
                val attempts = report.optInt("depthAttemptCount", 0)
                val outcomes = report.optJSONObject("depthAttemptStatusCounts")?.toString() ?: "{}"
                text = report.optString("message") +
                    "\nFotos: $frameCount · con profundidad: $depthFrames · fuente 3D: ${report.optString("landmarkSource", "NO_REGISTRADA")}" +
                    "\nDepth: modo ${report.optString("depthMode", "NO_REGISTRADO")} · intentos $attempts · resultados $outcomes" +
                    "\nComprobación interna con fotos de la misma sesión. No garantiza recuperación al volver otro día." +
                    "\nLos criterios son provisionales: 90% de vistas, error ≤20 cm y ≤5° respecto a ARCore."
            })
            val failed = linkedSetOf<String>()
            listOf("failedViewIds", "weakImages").forEach { key -> report.optJSONArray(key)?.let { a ->
                for (i in 0 until a.length()) failed += a.getString(i)
            } }
            if (failed.isNotEmpty()) {
                panel.addView(TextView(this).apply { text = "Estas vistas necesitan revisión. Para ampliar la captura, mantén detalles fijos visibles desde varias posiciones; evita giros rápidos y paredes lisas." })
                val map = io.arhome.localizer.map.PersistentMapLoader().load(File(directory, "map"))
                map.keyframes.filter { it.id in failed }.forEach { frame ->
                    panel.addView(TextView(this).apply { text = "Vista ${frame.id}" })
                    panel.addView(ImageView(this).apply {
                        adjustViewBounds = true
                        setImageBitmap(BitmapFactory.decodeFile(frame.image.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 }))
                    })
                }
            }
            AlertDialog.Builder(this).setTitle("Resultado de la comprobación")
                .setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Exportar informe") { _, _ ->
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "map-validation-$id.json")
                    }, REPORT_EXPORT_REQUEST)
                }.show()
        } catch (e: Exception) {
            validationStatus.text = "No se pudo abrir el informe: ${e.message}"
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun ensureCameraAndStart() {
        if (importing || validationPending) { finalStatus = "Espera a que termine la comprobación del mapa."; renderStatus(); return }
        if (relocalizationPreparing) {
            finalStatus = "Wait for relocalization map preparation to finish before starting mapping."
            renderStatus()
            return
        }
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
        if (importing || validationPending) { finalStatus = "Espera a que termine la comprobación del mapa."; renderStatus(); return }
        if (validationOutcome != "INTERNAL_PASS") {
            finalStatus = "El mapa no ha superado la comprobación interna. Abre el informe antes de probar la recuperación."
            renderStatus()
            return
        }
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
        if (relocalizationPreparing) {
            finalStatus = "Relocalization map preparation is already running."
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
            val mapCapture = MapCaptureSession(this, newSession)
            view.setTrackedFrameConsumer(mapCapture::onFrame)
            attachSession(newSession, view)
            capture = mapCapture
            finalStatus = "Captura iniciada. Mantén el mueble y su entorno visibles mientras te desplazas despacio."
        } catch (e: Exception) {
            resetFailedSession("Map capture failed", e)
        }
    }

    private fun startRelocalization() {
        val map = persistentMap ?: return
        relocalizationPreparing = true
        preparationMs = null
        finalStatus = "Preparing PnP landmarks and ORB fallback in background (${map.keyframes.size} keyframes)…"
        renderStatus()
        val preparationStartedNs = SystemClock.elapsedRealtimeNanos()
        localizationExecutor.execute {
            try {
                // Both providers prewarm before ARCore starts, away from the UI thread.
                val pnpProvider = PnpLandmarkLocalizationProvider()
                var pnpFailure: String? = null
                val pnpReady = try {
                    pnpProvider.prepare(map)
                    true
                } catch (e: Exception) {
                    pnpFailure = "PnP unavailable: ${e.message}"
                    false
                }
                val orbProvider = OrbKeyframeLocalizationProvider().also { it.prepare(map) }
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - preparationStartedNs) / 1_000_000L
                handler.post {
                    if (isDestroyed) return@post
                    relocalizationPreparing = false
                    preparationMs = elapsedMs
                    pnpRelocalizer = pnpProvider
                    orbRelocalizer = orbProvider
                    pnpEnabled = pnpReady
                    pnpDisabledReason = pnpFailure
                    startPreparedRelocalization(map, pnpProvider, orbProvider)
                }
            } catch (e: LinkageError) {
                reportPreparationFailure("Relocalization native runtime failed", e)
            } catch (e: Exception) {
                reportPreparationFailure("Relocalization preparation failed", e)
            }
        }
    }

    private fun startPreparedRelocalization(
        map: PersistentMap,
        pnpProvider: PnpLandmarkLocalizationProvider,
        orbProvider: OrbKeyframeLocalizationProvider,
    ) {
        try {
            val newSession = createSession("Start relocalization") ?: return
            val view = createView(newSession)
            alignment = null
            worldCameraPose = null
            relocalizationMs = null
            localizationRuntimeError = null
            acceptedPoseSource = null
            relocalizationStartedNs = SystemClock.elapsedRealtimeNanos()
            view.setTrackedFrameConsumer { frame ->
                val currentAlignment = alignment
                if (currentAlignment != null) {
                    worldCameraPose = currentAlignment.worldCameraPose(frame.camera.pose)
                } else if (!localizationInFlight) {
                    CapturedLocalizationFrame.capture(frame)?.let { captured ->
                        localizationInFlight = true
                        localizationExecutor.execute {
                            try {
                                val pnpResult = if (pnpEnabled) {
                                    try {
                                        pnpProvider.localize(map, captured)
                                    } catch (e: Exception) {
                                        pnpEnabled = false
                                        pnpDisabledReason = "PnP disabled after runtime error: ${e.message}"
                                        null
                                    }
                                } else {
                                    null
                                }
                                val sourceAndResult = pnpResult?.let { "PnP" to it }
                                    ?: orbProvider.localize(map, captured)?.let { "ORB/homography fallback" to it }
                                sourceAndResult?.let { (source, result) ->
                                    val created = WorldAlignment.fromLocalization(result, captured.cameraPose)
                                    alignment = created
                                    worldCameraPose = created.worldCameraPose(captured.cameraPose)
                                    acceptedPoseSource = source
                                    relocalizationMs = (SystemClock.elapsedRealtimeNanos() - relocalizationStartedNs) / 1_000_000L
                                }
                            } catch (e: LinkageError) {
                                localizationRuntimeError = "Native localization error: ${e.message}"
                            } catch (e: Exception) {
                                localizationRuntimeError = "Localization error: ${e.javaClass.simpleName}: ${e.message}"
                            } finally {
                                captured.close()
                                localizationInFlight = false
                            }
                        }
                    }
                }
            }
            attachSession(newSession, view)
            finalStatus = "Fresh-session geometric relocalization running. Look at the mapped furniture and surrounding room detail."
        } catch (e: LinkageError) {
            resetFailedSession("Relocalization native runtime failed", e)
        } catch (e: Exception) {
            resetFailedSession("Relocalization failed", e)
        }
    }

    private fun reportPreparationFailure(prefix: String, error: Throwable) {
        handler.post {
            if (isDestroyed) return@post
            relocalizationPreparing = false
            resetFailedSession(prefix, error)
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
            config.focusMode = Config.FocusMode.AUTO
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
        root.addView(view, 2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (300 * resources.displayMetrics.density).toInt()))
        session = newSession
        arView = view
        newSession.resume()
        view.onResume()
        handler.post(refresh)
    }

    private fun resetFailedSession(prefix: String, error: Throwable) {
        arView?.setTrackedFrameConsumer(null)
        arView?.let { root.removeView(it) }
        capture?.close()
        session?.close()
        session = null
        arView = null
        capture = null
        orbRelocalizer = null
        pnpRelocalizer = null
        pnpEnabled = false
        pnpDisabledReason = null
        relocalizationPreparing = false
        localizationInFlight = false
        localizationRuntimeError = null
        acceptedPoseSource = null
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
            arView?.onPause()
            session?.pause()
            session?.close()
            session = null
            arView?.let { root.removeView(it) }
            arView = null
            finalStatus = "Captura guardada: ${result.keyframeCount} fotos.\nArchivo: $publicLocation\nPendiente de comprobación interna."
            importing = true
            validationPending = true
            validationOutcome = null
            validationStatus.text = "Preparando comprobación del mapa…"
            localizationExecutor.execute {
                try {
                    val map = result.archive.inputStream().use(mapStore::import)
                    val jobId = MapValidationJobs.enqueue(applicationContext, map)
                    handler.post { if (!isDestroyed) { importing = false; persistentMap = map; observeValidation(jobId) } }
                } catch (e: Exception) {
                    handler.post { if (!isDestroyed) {
                        importing = false; validationPending = false
                        validationStatus.text = "No se pudo preparar la comprobación: ${e.message}. Importa el ZIP guardado."
                    } }
                }
            }
            renderStatus()
        } catch (e: Exception) {
            if (!current.isClosed) arView?.setTrackedFrameConsumer(current::onFrame)
            finalStatus = "Map export failed: ${e.javaClass.simpleName}: ${e.message}"
            renderStatus()
        }
    }

    private fun selectMapArchive() {
        if (importing) return
        if (relocalizationPreparing) {
            finalStatus = "Wait for relocalization map preparation to finish before importing another map."
            renderStatus()
            return
        }
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
            if (mapCapture != null) {
                appendLine("Fotos: ${mapCapture.keyframeCount}")
                appendLine("Estado de captura: ${mapCapture.captureState}")
                appendLine(mapCapture.guidance)
                appendLine(mapCapture.depthStatus)
            }
            preparationMs?.let { appendLine("Map preparation: $it ms (background)") }
            pnpRelocalizer?.latestStatus?.let { match ->
                appendLine("PnP: ${match.message}")
                appendLine("Landmarks: ${match.landmarks} · good matches: ${match.goodMatches} · PnP inliers: ${match.pnpInliers}")
            }
            pnpDisabledReason?.let { appendLine(it) }
            localizationRuntimeError?.let { appendLine(it) }
            orbRelocalizer?.latestStatus?.let { match ->
                appendLine("ORB fallback: ${match.message}")
                appendLine("Best keyframe: ${match.bestKeyframeId ?: "—"}")
                appendLine("ORB good matches: ${match.goodMatches} · RANSAC inliers: ${match.inliers} · ratio: %.2f".format(match.inlierRatio))
                appendLine("Stable hits: ${match.stableHits} · evaluated references: ${match.evaluatedReferences}/${match.referenceCount}")
            }
            alignment?.let { accepted ->
                appendLine("POSE ACCEPTED FROM: ${acceptedPoseSource ?: "unknown"}")
                appendLine("RELOCALIZED · keyframe ${accepted.source.matchedKeyframeId} · confidence %.3f · ${relocalizationMs ?: 0} ms".format(accepted.source.confidence))
                worldCameraPose?.translation?.let { xyz ->
                    appendLine("World camera xyz: %.2f, %.2f, %.2f m".format(xyz[0], xyz[1], xyz[2]))
                }
                appendLine("Accepted-pose inliers: ${accepted.source.inlierCount}")
            }
            if (tracking != null) {
                appendLine("ARCore tracking frames: ${tracking.optLong("trackingFrames")}")
                appendLine("Max ARCore frame gap: %.1f ms".format(tracking.optDouble("maxFrameGapMs")))
                appendLine("Tracking failure: ${tracking.optString("trackingFailureReason")}")
                tracking.opt("lastError")?.let { error ->
                    if (error.toString() != "null") appendLine("Runtime error: $error")
                }
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
        private const val REPORT_EXPORT_REQUEST = 54
        private const val MAP_IMPORT_REQUEST = 52
        private const val RELOCALIZATION_CAMERA_REQUEST = 53
    }
}
