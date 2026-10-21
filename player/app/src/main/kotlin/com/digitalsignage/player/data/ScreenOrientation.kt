package com.digitalsignage.player.data

/**
 * Como a TV foi instalada, dito no painel. Uma TV presa na parede na vertical continua recebendo um sinal de vídeo
 * horizontal (a saída da caixa de TV não gira sozinha): quem gira o conteúdo é o Player.
 *
 * Os nomes dizem para onde aponta o TOPO DA TV: com o topo para a direita, o conteúdo precisa ser girado 90° no sentido
 * anti-horário (270° no Android, que conta no sentido horário) para ficar de pé para quem olha.
 */
object ScreenOrientation {
    const val AUTO = "AUTO"
    const val LANDSCAPE = "LANDSCAPE"
    const val LANDSCAPE_FLIPPED = "LANDSCAPE_FLIPPED"
    const val PORTRAIT_TOP_RIGHT = "PORTRAIT_TOP_RIGHT"
    const val PORTRAIT_TOP_LEFT = "PORTRAIT_TOP_LEFT"

    private val ALL = setOf(AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT, PORTRAIT_TOP_LEFT)

    /** Um valor que o Player não conhece (servidor mais novo) volta ao automático: nunca derruba a exibição. */
    fun normalize(value: String?): String = if (value != null && value in ALL) value else AUTO

    /**
     * Quantos graus (no sentido horário) o conteúdo precisa girar para ficar como [orientation] pede, sabendo se o que o
     * aparelho informa como tela já é em pé ([displayPortrait], por exemplo um tablet ou uma caixa configurada para girar
     * a saída): quando o aparelho já entrega a tela na forma pedida, não se gira nada.
     */
    fun rotationDegrees(orientation: String, displayPortrait: Boolean): Int = when (normalize(orientation)) {
        PORTRAIT_TOP_RIGHT -> if (displayPortrait) 0 else 270
        PORTRAIT_TOP_LEFT -> if (displayPortrait) 0 else 90
        LANDSCAPE -> if (displayPortrait) 90 else 0
        LANDSCAPE_FLIPPED -> if (displayPortrait) 270 else 180
        else -> 0
    }

    /** O conteúdo deve ser desenhado como tela em pé (versão em pé do layout, painel de preços em uma coluna...)? */
    fun contentPortrait(orientation: String, displayPortrait: Boolean): Boolean = when (normalize(orientation)) {
        PORTRAIT_TOP_RIGHT, PORTRAIT_TOP_LEFT -> true
        LANDSCAPE, LANDSCAPE_FLIPPED -> false
        else -> displayPortrait
    }

    /** Em 90° e 270° a largura e a altura do conteúdo trocam de lugar. */
    fun swapsAxes(degrees: Int): Boolean = degrees == 90 || degrees == 270
}
