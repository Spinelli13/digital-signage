package com.digitalsignage.player.data

/** Posição e tamanho de uma zona em pixels. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

object ZoneGeometry {

    /**
     * Converte a zona (em % da tela) para pixels. Cada borda é arredondada sozinha (e a largura é a diferença entre
     * as bordas), então duas zonas vizinhas, como 75% + 25%, se encostam sem fresta e sem sobreposição de 1 pixel
     * qualquer que seja a resolução.
     */
    fun rect(zone: ConfigZone, screenWidth: Int, screenHeight: Int): PixelRect {
        val left = edge(zone.x, screenWidth)
        val right = edge(zone.x + zone.width, screenWidth)
        val top = edge(zone.y, screenHeight)
        val bottom = edge(zone.y + zone.height, screenHeight)
        return PixelRect(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    private fun edge(percent: Int, total: Int): Int = Math.round(percent * total / 100.0).toInt()

}
