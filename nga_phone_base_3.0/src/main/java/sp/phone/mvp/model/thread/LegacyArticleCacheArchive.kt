package sp.phone.mvp.model.thread

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Old exports contain cache/<tid>/<positive-number>.json, and may not write any other root. */
object LegacyArticleCacheArchive {
    private val filePath = Regex("cache/([1-9][0-9]{0,9})/([1-9][0-9]{0,9})\\.json")
    private val directoryPath = Regex("cache(?:/([1-9][0-9]{0,9}))?/")

    @JvmStatic fun isAllowedPath(name: String, directory: Boolean): Boolean {
        val match = (if (directory) directoryPath else filePath).matchEntire(name) ?: return false
        return match.groupValues.drop(1).all { it.isEmpty() || it.toIntOrNull()?.let { n -> n > 0 } == true }
    }

    /** Export the same validated legacy entries the reader lists; omit temporary files and owned roots. */
    @JvmStatic fun exportArchive(filesDir: File, output: OutputStream): Int {
        val store = ArticleCacheStore(filesDir)
        var count = 0
        ZipOutputStream(output).use { zip ->
            store.list(null).forEach { record ->
                val prefix = "cache/${record.entry.tid}/"
                zip.putNextEntry(ZipEntry("$prefix${record.entry.tid}.json"))
                zip.write(record.topicInfo.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                record.pages.forEach { page ->
                    val stored = store.read(record.entry, page, null)
                    zip.putNextEntry(ZipEntry("$prefix$page.json"))
                    zip.write(stored.raw.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                count++
            }
        }
        return count
    }

    @JvmStatic fun importArchive(input: InputStream, filesDir: File): Int {
        val root = filesDir.canonicalFile
        val staging = File(root, ".legacy-cache-import-${UUID.randomUUID()}")
        if (!staging.mkdir()) throw IOException("Cannot stage cache import")
        val paths = linkedSetOf<String>()
        var bytes = 0L
        var count = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > 10000 || !isAllowedPath(entry.name, entry.isDirectory)) throw IOException("Invalid legacy cache archive")
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    if (!paths.add(entry.name)) throw IOException("Duplicate cache archive path")
                    if (entry.size > ArticleCacheStore.MAX_PAGE_BYTES) throw IOException("Cache archive entry too large")
                    val temporary = File(staging, entry.name)
                    if (!temporary.parentFile!!.isDirectory && !temporary.parentFile!!.mkdirs()) throw IOException("Cannot stage cache page")
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read == -1) break
                            entryBytes += read; bytes += read
                            if (entryBytes > ArticleCacheStore.MAX_PAGE_BYTES || bytes > 256L * 1024 * 1024) throw IOException("Cache archive too large")
                            output.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (paths.isEmpty()) throw IOException("Empty legacy cache archive")
            // Validate every destination before committing any staged file (including symlinks).
            paths.forEach { destination(root, it) }
            paths.forEach { path ->
                val target = destination(root, path)
                if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw IOException("Cannot create cache directory")
                if (!File(staging, path).renameTo(target)) throw IOException("Cannot import cache page")
            }
            return paths.size
        } finally { staging.deleteRecursively() }
    }

    private fun destination(root: File, path: String): File {
        val target = File(root, path)
        if (target.canonicalFile != target.absoluteFile || !target.path.startsWith(File(root, "cache").path + File.separator)) {
            throw IOException("Invalid cache destination")
        }
        return target
    }
}
