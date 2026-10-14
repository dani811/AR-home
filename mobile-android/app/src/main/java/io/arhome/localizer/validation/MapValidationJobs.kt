package io.arhome.localizer.validation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.arhome.localizer.map.PersistentMap
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object MapValidationJobs {
    fun currentId(context: Context, map: PersistentMap?): UUID? {
        val prefs = context.getSharedPreferences("map-validation", Context.MODE_PRIVATE)
        if (map == null || prefs.getString("mapRoot", null) != map.root.absolutePath) return null
        return prefs.getString("job", null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    /** Called on the serialized import executor. Snapshot completes before work is enqueued. */
    fun enqueue(context: Context, map: PersistentMap): UUID {
        val request = OneTimeWorkRequest.Builder(MapValidationWorker::class.java).build()
        val directory = directory(context, request.id)
        val snapshot = File(directory, "map")
        check(snapshot.mkdirs())
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val files = listOf(File(map.root, "manifest.json")) + map.keyframes.map { it.image }
            val root = map.root.canonicalFile.toPath()
            files.forEach { source ->
                val path = source.canonicalFile.toPath()
                require(path.startsWith(root)) { "Map image is outside its map directory" }
                val relative = root.relativize(path).toString()
                val target = File(snapshot, relative)
                check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                digest.update(relative.toByteArray())
                target.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var size = input.read(buffer)
                    while (size != -1) { digest.update(buffer, 0, size); size = input.read(buffer) }
                }
            }
            File(directory, "fingerprint.txt").writeText(digest.digest().joinToString("") { "%02x".format(it) })
            WorkManager.getInstance(context).enqueueUniqueWork("map-validation-${request.id}", ExistingWorkPolicy.KEEP, request).result.get()
            check(context.getSharedPreferences("map-validation", Context.MODE_PRIVATE).edit()
                .putString("job", request.id.toString()).putString("mapRoot", map.root.absolutePath).commit())
            return request.id
        } catch (e: Exception) {
            WorkManager.getInstance(context).cancelWorkById(request.id)
            // Keep the snapshot if enqueue succeeded: cancellation is cooperative.
            throw e
        }
    }

    fun directory(context: Context, id: UUID) = File(context.filesDir, "map-validations/$id")
}
