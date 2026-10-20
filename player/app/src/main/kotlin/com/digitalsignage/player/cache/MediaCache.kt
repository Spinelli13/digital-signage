package com.digitalsignage.player.cache

import com.digitalsignage.player.data.ConfigItem
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ChecksumMismatchException(item: ConfigItem) :
    IOException("Checksum divergente ao baixar \"${item.name}\" (mídia ${item.mediaId})")

/**
 * Cache em disco das mídias. O nome do arquivo inclui o checksum, então uma mídia nunca é confundida
 * com outra versão; o download vai para um .part e só vira o arquivo final depois de validado.
 */
class MediaCache(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun fileFor(item: ConfigItem): File {
        val hash = item.checksum?.take(16) ?: "sem-checksum"
        return File(dir, "${item.mediaId}-$hash.${extensionOf(item)}")
    }

    fun isCached(item: ConfigItem): Boolean {
        val file = fileFor(item)
        return file.isFile && (item.fileSize <= 0 || file.length() == item.fileSize)
    }

    /** Grava o stream no cache validando tamanho e SHA-256. Em caso de erro nada fica no cache. */
    fun store(item: ConfigItem, input: InputStream): File {
        val target = fileFor(item)
        val part = File(dir, target.name + PART_SUFFIX)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L

        try {
            part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    total += read
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val checksumOk = item.checksum == null || item.checksum.equals(actual, ignoreCase = true)
            val sizeOk = item.fileSize <= 0 || item.fileSize == total
            if (!checksumOk || !sizeOk) throw ChecksumMismatchException(item)

            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("Não foi possível gravar ${target.name}")
            return target
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    /** Apaga tudo que não pertence à configuração atual (mídias antigas e downloads interrompidos). */
    fun retainOnly(items: List<ConfigItem>) {
        val keep = items.map { fileFor(it).name }.toSet()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) file.delete()
        }
    }

    private fun extensionOf(item: ConfigItem): String {
        val fromName = item.name.substringAfterLast('.', "").lowercase()
        return fromName.takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?: if (item.isVideo) "mp4" else "img"
    }

    companion object {
        const val PART_SUFFIX = ".part"
    }

}
