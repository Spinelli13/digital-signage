package com.digitalsignage.player

import com.digitalsignage.player.pairing.QrCode
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O QR Code de pareamento tem de ser lido de volta como o mesmo endereço, com a margem que os leitores pedem. */
class QrCodeTest {

    private fun read(text: String): String {
        val matrix = QrCode.encode(text)
        val scale = 6
        val side = matrix.width * scale
        val pixels = IntArray(side * side) { index ->
            val x = (index % side) / scale
            val y = (index / side) / scale
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(image).text
    }

    @Test
    fun `the pairing address is read back exactly`() {
        val url = "https://signage-painel.pages.dev/parear/K7M2QX"

        assertEquals(url, read(url))
    }

    @Test
    fun `a long address with accents and symbols also survives`() {
        val url = "https://painel.exemplo.com.br/parear/ABC234?origem=tv&nome=Recepção"

        assertEquals(url, read(url))
    }

    @Test
    fun `the quiet zone around the code is white, as phone cameras require`() {
        val matrix = QrCode.encode("https://painel.test/parear/ABC234")

        for (i in 0 until matrix.width) {
            for (edge in 0 until 4) {
                assertFalse("margem de cima", matrix[i, edge])
                assertFalse("margem de baixo", matrix[i, matrix.height - 1 - edge])
                assertFalse("margem da esquerda", matrix[edge, i])
                assertFalse("margem da direita", matrix[matrix.width - 1 - edge, i])
            }
        }
        assertTrue("o código tem módulos pretos", (0 until matrix.width).any { x -> (0 until matrix.height).any { y -> matrix[x, y] } })
    }
}
