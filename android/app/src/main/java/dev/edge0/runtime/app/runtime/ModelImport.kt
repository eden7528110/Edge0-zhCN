// runtime/ModelImport.kt - copy a caller-supplied .gguf stream into files/models.
// The document picker lives in the UI; this class only accepts a flat file name
// and a stream, so a release install can import without adb or storage permission.
// Bytes land in a sibling "*.gguf.partial" and are renamed after the copy, so a
// killed transfer is never scanned as a model.
package dev.edge0.runtime.app.runtime

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

object ModelImport {
    const val PARTIAL_SUFFIX = ".partial"
    private const val REPORT_EVERY = 32L * 1024 * 1024

    class ImportException(message: String) : Exception(message)

    /** Flat `*.gguf` basename, or null when the display name is not one. */
    fun fileName(displayName: String?): String? {
        if (displayName.isNullOrBlank()) return null
        val base = displayName.trim().substringAfterLast('/').substringAfterLast('\\')
        if (base.isEmpty() || base == "." || base == "..") return null
        if (base.any { it == '/' || it == '\\' || it == '\u0000' }) return null
        if (!base.endsWith(".gguf", ignoreCase = true)) return null
        if (base.length > 180) return null
        val stem = base.dropLast(".gguf".length)
        if (stem.isEmpty()) return null
        return stem + ".gguf"
    }

    /** Drop a partial left behind when the process died mid-copy. */
    fun deleteStalePartials(modelsDir: File) {
        modelsDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".gguf$PARTIAL_SUFFIX")) f.delete()
        }
    }

    /**
     * Copy [input] into [modelsDir]. [sizeBytes] is the picker-reported length
     * when known; a mismatch or a short disk is an [ImportException] and leaves
     * no partial file behind. [availableBytes] defaults to the volume free space.
     */
    suspend fun copy(
        input: InputStream,
        modelsDir: File,
        displayName: String,
        sizeBytes: Long?,
        availableBytes: Long = modelsDir.usableSpace,
        onProgress: suspend (copied: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = input.use { inp ->
        val safe = fileName(displayName)
            ?: throw ImportException("请选择文件名以 .gguf 结尾的文件")
        if (sizeBytes != null && sizeBytes < 0) throw ImportException("大小无效")
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            throw ImportException("无法创建目录 ${modelsDir.path}")
        }
        if (sizeBytes != null && sizeBytes > availableBytes) {
            throw ImportException("剩余空间不足，无法导入 $safe")
        }
        val dest = File(modelsDir, safe)
        val partial = File(modelsDir, safe + PARTIAL_SUFFIX)
        if (partial.exists() && !partial.delete()) {
            throw ImportException("无法清除 $safe 之前未完成的副本")
        }
        var finished = false
        try {
            FileOutputStream(partial).use { out ->
                    val buf = ByteArray(1024 * 1024)
                    var copied = 0L
                    var lastReport = -1L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (lastReport < 0 || copied - lastReport >= REPORT_EVERY) {
                            lastReport = copied
                            onProgress(copied, sizeBytes)
                        }
                    }
                    out.fd.sync()
                    if (copied != lastReport) onProgress(copied, sizeBytes)
                    if (copied == 0L) throw ImportException("$safe 是空文件")
                    if (sizeBytes != null && copied != sizeBytes) {
                        throw ImportException("$safe 不完整（应为 $sizeBytes 字节，实际 $copied 字节）")
                    }
            }
            if (dest.exists() && !dest.delete()) {
                throw ImportException("无法替换 $safe")
            }
            if (!partial.renameTo(dest)) throw ImportException("无法完成 $safe 的导入")
            finished = true
            return dest
        } finally {
            if (!finished) partial.delete()
        }
    }
}
