package com.digitalsignage.player.proof

/** O que se sabe sobre a configuração em uso quando uma mídia vai ao ar (entra em cada registro de exibição). */
data class PlayContext(
    val deviceId: Long,
    val playlistId: Long?,
    val campaignId: Long?,
    val campaignName: String?,
) {
    companion object {
        val NONE = PlayContext(0, null, null, null)
    }
}
