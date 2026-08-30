package io.arhome.localizer.map

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class PersistentMapStore(private val baseDirectory: File) {

    private val currentRoot = File(baseDirectory, "current")
    private val loader = PersistentMapLoader()

    fun currentOrNull(): PersistentMap? =
        runCatching { loader.load(currentRoot) }.getOrNull()

    fun import(input: InputStream): PersistentMap {
        if (!baseDirectory.exists() && !baseDirectory.mkdirs()) {
            error("Could not create map store: ${baseDirectory.absolutePath}")
        }
        val staging = File(baseDirectory, "staging-${System.currentTimeMillis()}")
        check(staging.mkdirs()) { "Could not create staging map directory" }
        try {
            unzip(input, staging)
            loader.load(staging)
            currentRoot.deleteRecursively()
            check(staging.renameTo(currentRoot)) { "Could not activate imported map" }
            return loader.load(currentRoot)
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    private fun unzip(input: InputStream, destination: File) {
        val destinationPath = destination.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destination, entry.name).canonicalFile
                require(target.path.startsWith(destinationPath)) {
                    "Unsafe ZIP entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    check(target.exists() || target.mkdirs()) { "Could not create ${entry.name}" }
                } else {
                    val parent = target.parentFile ?: error("Invalid ZIP entry: ${entry.name}")
                    check(parent.exists() || parent.mkdirs()) { "Could not create ${parent.absolutePath}" }
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
