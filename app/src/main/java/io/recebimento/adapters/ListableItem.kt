package io.recebimento.adapters

import io.recebimento.network.Recebimento

sealed class ListableItem {
    data class SectionHeader(val dateLabel: String) : ListableItem()
    data class RecebimentoItem(val recebimento: Recebimento) : ListableItem()
}
