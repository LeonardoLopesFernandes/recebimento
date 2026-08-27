package io.recebimento.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.recebimento.R
import io.recebimento.network.Guia
import io.recebimento.network.RecebimentoItem

class GuiaAdapter(
    private var items: List<Guia>
) : RecyclerView.Adapter<GuiaAdapter.GuiaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuiaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guia, parent, false)
        return GuiaViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuiaViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class GuiaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNumGuia: TextView = itemView.findViewById(R.id.tvNumGuia)
        private val tvTotalGuia: TextView = itemView.findViewById(R.id.tvTotalGuia)
        private val tvQtdImei: TextView = itemView.findViewById(R.id.tvQtdImei)
        private val rvItens: RecyclerView = itemView.findViewById(R.id.rvItens)

        fun bind(guia: Guia) {
            tvNumGuia.text = "Guia: ${guia.num}"
            tvTotalGuia.text = "Total: R$ ${String.format("%.2f", guia.valorTotal)}"
            tvQtdImei.text = "Imeis: ${guia.qtd_imei_guia}"
            
            // Configurar itens da nota
            val todosItens = mutableListOf<RecebimentoItem>()
            guia.recebimentoNota.forEach { nota ->
                todosItens.addAll(nota.recebimentoItem)
            }
            
            val adapter = ItemNotaAdapter(todosItens)
            rvItens.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
            rvItens.adapter = adapter
        }
    }
}