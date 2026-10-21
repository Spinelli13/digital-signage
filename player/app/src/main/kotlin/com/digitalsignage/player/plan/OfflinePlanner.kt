package com.digitalsignage.player.plan

import com.digitalsignage.player.data.PlayerConfig

/**
 * Transforma "que horas são" em "o que exibir", usando só a agenda que o servidor mandou. A configuração resultante é igual
 * à que o servidor mandaria naquele instante (mesma playlist, versão, campanha e zonas), então, quando a internet voltar e
 * o servidor confirmar, a tela não precisa recomeçar.
 */
object OfflinePlanner {

    /**
     * A configuração do intervalo em que [nowMs] cai, partindo de [base] (a última configuração sincronizada: ela traz o layout
     * e as zonas fixas). Nula se não há agenda ou se a hora está fora dela (agenda vencida): nesse caso fica o que já está no ar.
     */
    fun configAt(base: PlayerConfig, nowMs: Long): PlayerConfig? {
        val plan = base.plan ?: return null
        val segment = plan.segmentAt(nowMs) ?: return null
        val items = plan.itemsOf(segment.playlistId)
        return base.copy(
            playlistId = segment.playlistId,
            configVersion = segment.configVersion,
            items = items,
            zones = base.zones.map { if (it.main) it.copy(items = items) else it },
            campaignId = segment.campaignId,
            campaignName = segment.campaignName,
        )
    }
}
