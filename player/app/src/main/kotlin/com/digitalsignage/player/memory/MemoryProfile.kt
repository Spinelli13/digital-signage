package com.digitalsignage.player.memory

import android.app.ActivityManager
import android.content.Context

/**
 * Como o Player se comporta conforme a memória do aparelho. TVs e pen drives de entrada têm 1 GB de RAM (ou menos livre
 * do que parece) e o Android mata o app que passa do limite: nesses aparelhos o Player gasta menos (imagens menores e
 * mais leves, um só vídeo ao mesmo tempo, páginas e decodificadores liberados assim que saem de cena).
 */
data class MemoryProfile(
    val low: Boolean,
    /** Maior largura/altura (px) em que uma imagem é decodificada; 0 = o tamanho da zona. */
    val maxImageWidth: Int,
    val maxImageHeight: Int,
    /** RGB_565 usa metade da memória do ARGB_8888 (sem transparência, que a tela não usa). */
    val rgb565: Boolean,
    /** Vídeos em zonas que não são a principal (cada vídeo reserva um decodificador de hardware). */
    val secondaryVideo: Boolean,
    /** Libera o WebView e o ExoPlayer assim que o item sai de cena (em vez de guardá-los para o próximo). */
    val releaseAfterUse: Boolean,
    /** Buffer do ExoPlayer, em ms (mínimo, máximo) e teto em bytes. */
    val bufferMinMs: Int,
    val bufferMaxMs: Int,
    val bufferBytes: Int,
) {

    companion object {
        val NORMAL = MemoryProfile(
            low = false, maxImageWidth = 0, maxImageHeight = 0, rgb565 = false,
            secondaryVideo = true, releaseAfterUse = false, bufferMinMs = 5_000, bufferMaxMs = 15_000, bufferBytes = 16 * 1024 * 1024,
        )

        val LOW = MemoryProfile(
            low = true, maxImageWidth = 1280, maxImageHeight = 720, rgb565 = true,
            secondaryVideo = false, releaseAfterUse = true, bufferMinMs = 1_500, bufferMaxMs = 3_000, bufferBytes = 4 * 1024 * 1024,
        )

        /** Aparelho com a marca "pouca RAM" do Android, heap pequeno por app ou 1 GB de RAM (ou menos). */
        const val LOW_MEMORY_CLASS_MB = 96
        const val LOW_TOTAL_MB = 1_200L

        fun choose(lowRamFlag: Boolean, memoryClassMb: Int, totalMb: Long): MemoryProfile =
            if (lowRamFlag || memoryClassMb in 1..LOW_MEMORY_CLASS_MB || totalMb in 1..LOW_TOTAL_MB) LOW else NORMAL

        fun detect(context: Context): MemoryProfile {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            return choose(manager.isLowRamDevice, manager.memoryClass, info.totalMem / (1024 * 1024))
        }
    }

}

/** O nível que o Android passa em onTrimMemory: a partir de "pouca memória rodando" o Player solta o que não está à vista. */
object TrimLevels {
    const val RUNNING_LOW = 10
    const val RUNNING_CRITICAL = 15
    const val UI_HIDDEN = 20

    fun shouldRelease(level: Int): Boolean = level >= RUNNING_LOW
}
