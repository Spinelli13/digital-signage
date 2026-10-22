package com.digitalsignage.player.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * O QR Code de pareamento: o celular de quem instala escaneia, abre o painel já com o código da TV e escolhe cliente, local e
 * nome. Módulos pretos sobre fundo branco com a margem (zona de silêncio) de 4 módulos que os leitores exigem, mesmo com o
 * resto da tela escura.
 */
object QrCode {

    private const val QUIET_ZONE_MODULES = 4

    /** A matriz do QR Code (a margem já inclusa). Correção de erro média: o endereço é curto e a tela é nítida. */
    fun encode(text: String): BitMatrix =
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to QUIET_ZONE_MODULES),
        )

    /** A imagem para a tela, ampliada em módulos inteiros para as bordas ficarem nítidas (sem suavizar). */
    fun bitmap(text: String, sizePx: Int): Bitmap {
        val matrix = encode(text)
        val scale = (sizePx / matrix.width).coerceAtLeast(1)
        val side = matrix.width * scale
        val pixels = IntArray(side * side)
        for (y in 0 until side) {
            for (x in 0 until side) {
                pixels[y * side + x] = if (matrix[x / scale, y / scale]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}
