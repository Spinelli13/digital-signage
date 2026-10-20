package com.digitalsignage.player.update

/**
 * Janela de horário em que o Player pode instalar atualizações, no formato "HH:mm-HH:mm" (ex.: "02:00-05:00").
 * Janelas que viram a meia-noite ("22:00-06:00") são aceitas. Vazio ou inválido = a qualquer hora.
 *
 * Existe porque, quando o Android exige confirmação, a janela do instalador aparece sobre as propagandas
 * até alguém apertar OK: numa loja é melhor que isso aconteça fora do expediente.
 *
 * Trabalha em minutos do dia (0..1439) em vez de java.time, que só existe a partir do Android 8 (minSdk é 21).
 */
object UpdateWindow {

    fun isOpen(window: String?, minuteOfDay: Int): Boolean {
        val (start, end) = parse(window) ?: return true
        return if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    fun parse(window: String?): Pair<Int, Int>? {
        if (window.isNullOrBlank()) return null
        val parts = window.trim().split("-")
        if (parts.size != 2) return null
        val start = minutes(parts[0]) ?: return null
        val end = minutes(parts[1]) ?: return null
        return start to end
    }

    private fun minutes(text: String): Int? {
        val match = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").matchEntire(text) ?: return null
        val hours = match.groupValues[1].toInt()
        val mins = match.groupValues[2].toInt()
        return if (hours in 0..23 && mins in 0..59) hours * 60 + mins else null
    }

}
