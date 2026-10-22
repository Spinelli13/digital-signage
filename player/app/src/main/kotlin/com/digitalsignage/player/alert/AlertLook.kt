package com.digitalsignage.player.alert

/**
 * Como o aviso em tela cheia ("Alertar agora") aparece: a cor de fundo pela urgência. Uma urgência que a TV não conhece (um
 * servidor mais novo) cai no vermelho: o aviso nunca fica sem cor nem passa por menos urgente do que é.
 */
object AlertLook {

    private const val URGENT = 0xFFB3261E.toInt()
    private const val WARNING = 0xFFB45309.toInt()
    private const val INFO = 0xFF5B21B6.toInt()

    /** A cor de fundo (ARGB) da urgência: URGENT vermelho, WARNING âmbar, INFO violeta. */
    fun background(tone: String): Int = when (tone.uppercase()) {
        "WARNING" -> WARNING
        "INFO" -> INFO
        else -> URGENT
    }

    /** O título aparece em caixa alta, como numa sirene; sem título, a mensagem ocupa a tela toda. */
    fun heading(title: String?): String? = title?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
}
