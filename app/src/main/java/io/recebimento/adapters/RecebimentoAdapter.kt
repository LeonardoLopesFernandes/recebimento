package io.recebimento.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.recebimento.R
import io.recebimento.network.Recebimento
import io.recebimento.utils.CurrencyFormatter
import io.recebimento.utils.LogHelper
import java.text.SimpleDateFormat
import java.util.*

class RecebimentoAdapter : ListAdapter<ListableItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private var onItemClickListener: ((Recebimento) -> Unit)? = null
    private var onGerarExcelClickListener: ((Recebimento) -> Unit)? = null
    private var onReceberClickListener: ((Recebimento) -> Unit)? = null

    private var progressoPorViagem: Map<String, Double> = emptyMap()

    fun setProgressoPorViagem(progresso: Map<String, Double>) {
        if (progressoPorViagem == progresso) return
        progressoPorViagem = progresso
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (Recebimento) -> Unit) {
        this.onItemClickListener = listener
    }

    fun setOnGerarExcelClickListener(listener: (Recebimento) -> Unit) {
        this.onGerarExcelClickListener = listener
    }

    fun setOnReceberClickListener(listener: (Recebimento) -> Unit) {
        this.onReceberClickListener = listener
    }

    var modoGrid: Boolean = false
        private set

    private var itensOriginais: List<Recebimento> = emptyList()

    fun updateItems(newItems: List<Recebimento>) {
        itensOriginais = newItems
        val lista = groupByDate(newItems)
        submitList(if (modoGrid) lista.filterNot { it is ListableItem.SectionHeader } else lista)
    }

    fun setModoGrid(grid: Boolean) {
        if (modoGrid == grid) return
        modoGrid = grid
        val lista = groupByDate(itensOriginais)
        submitList(if (grid) lista.filterNot { it is ListableItem.SectionHeader } else lista)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListableItem.SectionHeader -> TYPE_HEADER
            is ListableItem.RecebimentoItem -> if (modoGrid) TYPE_ITEM_GRID else TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section_header, parent, false)
                SectionViewHolder(view)
            }
            TYPE_ITEM_GRID -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_recebimento_grid, parent, false)
                RecebimentoViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_recebimento, parent, false)
                RecebimentoViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListableItem.SectionHeader -> {
                (holder as SectionViewHolder).bind(item.dateLabel)
            }
            is ListableItem.RecebimentoItem -> {
                val vh = holder as RecebimentoViewHolder
                val numeroViagem = item.recebimento.id.takeLast(7)
                vh.bind(item.recebimento, progressoPorViagem[numeroViagem])

                vh.btnVisualizar.setOnClickListener {
                    onItemClickListener?.invoke(item.recebimento)
                }

                vh.btnImprimir?.setOnClickListener {
                    onGerarExcelClickListener?.invoke(item.recebimento)
                }

                vh.btnReceber?.setOnClickListener {
                    LogHelper.i("CLICK btnReceber: ${item.recebimento.id}")
                    onReceberClickListener?.invoke(item.recebimento)
                }
            }
        }
    }

    private fun groupByDate(items: List<Recebimento>): List<ListableItem> {
        if (items.isEmpty()) return emptyList()

        val formatadorEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val formatadorDia = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val formatadorChave = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val grouped = items.groupBy { recebimento ->
            try {
                val data = formatadorEntrada.parse(recebimento.viagem_data)
                formatadorChave.format(data!!)
            } catch (e: Exception) {
                "0000-00-00"
            }
        }

        return grouped.entries
            .sortedByDescending { it.key }
            .flatMap { (chave, recebimentos) ->
                val label = try {
                    val data = formatadorChave.parse(chave)
                    formatadorDia.format(data!!)
                } catch (e: Exception) {
                    chave
                }
                listOf(ListableItem.SectionHeader(label)) + recebimentos.map { ListableItem.RecebimentoItem(it) }
            }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ListableItem>() {
        override fun areItemsTheSame(oldItem: ListableItem, newItem: ListableItem): Boolean {
            return when {
                oldItem is ListableItem.SectionHeader && newItem is ListableItem.SectionHeader ->
                    oldItem.dateLabel == newItem.dateLabel
                oldItem is ListableItem.RecebimentoItem && newItem is ListableItem.RecebimentoItem ->
                    oldItem.recebimento.id == newItem.recebimento.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ListableItem, newItem: ListableItem): Boolean {
            return oldItem == newItem
        }
    }

    class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSectionDate: TextView = itemView.findViewById(R.id.tvSectionDate)
        fun bind(dateLabel: String) {
            tvSectionDate.text = dateLabel
        }
    }

    class RecebimentoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIdViagem: TextView = itemView.findViewById(R.id.tvIdViagem)
        private val tvProtocoloValue: TextView = itemView.findViewById(R.id.tvProtocoloValue)
        private val tvData: TextView = itemView.findViewById(R.id.tvData)
        private val tvOrigem: TextView = itemView.findViewById(R.id.tvOrigem)
        private val tvPlaca: TextView = itemView.findViewById(R.id.tvPlaca)
        private val tvQuantidade: TextView = itemView.findViewById(R.id.tvQuantidade)
        val btnVisualizar: View = itemView.findViewById(R.id.btnVisualizar)
        val btnImprimir: View? = itemView.findViewById(R.id.btnImprimir)
        val btnReceber: View? = itemView.findViewById(R.id.btnReceber)

        fun bind(recebimento: Recebimento, progresso: Double? = null) {
            val formatadorEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

            val ano = try {
                val data = formatadorEntrada.parse(recebimento.viagem_data)
                val calendar = Calendar.getInstance()
                calendar.time = data
                calendar.get(Calendar.YEAR)
            } catch (e: Exception) {
                2025
            }

            val is2026OrLater = ano >= 2026

            var numeroExibicao = if (recebimento.id.length > 7) {
                recebimento.id.takeLast(7)
            } else {
                recebimento.id
            }

            tvIdViagem.text = numeroExibicao

            val temProtocolo = !recebimento.protocolo.isNullOrEmpty()
            tvProtocoloValue.visibility = if (temProtocolo) View.VISIBLE else View.GONE
            if (temProtocolo) tvProtocoloValue.text = "Protocolo: ${recebimento.protocolo}"

            val formatadorSaida = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            try {
                val dataViagem = formatadorEntrada.parse(recebimento.viagem_data) ?: throw Exception("Data nula")
                tvData.text = formatadorSaida.format(dataViagem)
            } catch (e: Exception) {
                tvData.text = recebimento.viagem_data.take(10)
            }

            tvOrigem.text = recebimento.codigo_origem
            tvPlaca.text = recebimento.placa_veiculo

            if (is2026OrLater) {
                val qtdRolls = if (recebimento.qtd_rolls > 0) {
                    "${CurrencyFormatter.formatarInteiro(recebimento.qtd_rolls)} Rolls"
                } else {
                    "0 Rolls"
                }
                tvQuantidade.text = qtdRolls
                tvQuantidade.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_main))
            } else {
                val qtdGuias = if (recebimento.qtd_guias > 0) {
                    "${CurrencyFormatter.formatarInteiro(recebimento.qtd_guias)} Guias"
                } else {
                    "0 Guias"
                }
                tvQuantidade.text = qtdGuias
                if (recebimento.qtd_guias == 0) {
                    tvQuantidade.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_gray))
                } else {
                    tvQuantidade.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_main))
                }
            }

            // Show RECEBER button only for pending trips
            val isPendente = recebimento.status.lowercase() == "pendente"
            btnReceber?.visibility = if (isPendente) View.VISIBLE else View.GONE

            // Progresso da viagem (API BRLog) - só para pendentes com progresso conhecido
            val layoutProgresso: View? = itemView.findViewById(R.id.layoutProgressoViagem)
            val tvProgresso: TextView? = itemView.findViewById(R.id.tvProgressoViagem)
            if (layoutProgresso != null && tvProgresso != null) {
                if (isPendente && progresso != null) {
                    var p = progresso
                    if (p > 1.0) p /= 100.0
                    p = p.coerceIn(0.0, 1.0)
                    tvProgresso.text = "${(p * 100).toInt()}%"
                    layoutProgresso.visibility = View.VISIBLE
                } else {
                    layoutProgresso.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        const val TYPE_ITEM = 0
        const val TYPE_HEADER = 1
        const val TYPE_ITEM_GRID = 2
    }
}