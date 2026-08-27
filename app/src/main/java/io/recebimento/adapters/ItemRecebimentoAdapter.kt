package io.recebimento.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.recebimento.R
import io.recebimento.network.RecebimentoItem
import io.recebimento.utils.CurrencyFormatter

class ItemRecebimentoAdapter(
    private var items: List<RecebimentoItem>
) : RecyclerView.Adapter<ItemRecebimentoAdapter.ItemViewHolder>() {

    fun updateItems(newItems: List<RecebimentoItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nota, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvGuiaRoll: TextView = itemView.findViewById(R.id.tvGuiaRoll)
        private val tvDescricao: TextView = itemView.findViewById(R.id.tvDescricao)
        private val tvQuantidade: TextView = itemView.findViewById(R.id.tvQuantidade)
        private val tvPreco: TextView = itemView.findViewById(R.id.tvPreco)
        private val tvDepartamento: TextView = itemView.findViewById(R.id.tvDepartamento)
        private val tvSap: TextView = itemView.findViewById(R.id.tvSap)

        fun bind(item: RecebimentoItem) {
            if (item.guiaOuRoll.isNullOrBlank()) {
                tvGuiaRoll.visibility = View.GONE
            } else {
                tvGuiaRoll.text = item.guiaOuRoll
                tvGuiaRoll.visibility = View.VISIBLE
            }
            tvDescricao.text = item.descricao
            tvQuantidade.text = "Qtd: ${CurrencyFormatter.formatarInteiro(item.quantidade)}"
            tvPreco.text = CurrencyFormatter.formatarMoedaComSimbolo(item.preco)
            tvDepartamento.text = "Dep: ${item.departamento}"
            tvSap.text = "SAP: ${item.id_sap.toLongOrNull()?.toString() ?: item.id_sap}"
        }
    }
}