package io.recebimento.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.recebimento.R
import io.recebimento.utils.PastaFotos

class PastaFotosAdapter(
    private val onClick: (PastaFotos) -> Unit,
    private val onLongClick: (PastaFotos) -> Unit
) : RecyclerView.Adapter<PastaFotosAdapter.VH>() {

    private val itens = mutableListOf<PastaFotos>()

    fun submitList(list: List<PastaFotos>) {
        itens.clear()
        itens.addAll(list)
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvViagem: TextView = itemView.findViewById(R.id.tvViagemPasta)
        val tvData: TextView = itemView.findViewById(R.id.tvDataPasta)
        val tvQtd: TextView = itemView.findViewById(R.id.tvQtdFotos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pasta_foto, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pasta = itens[position]
        holder.tvViagem.text = "Viagem ${pasta.viagem}"
        holder.tvData.text = pasta.data.ifEmpty { "Data não informada" }
        holder.tvQtd.text = pasta.caminhoFotos.size.toString()
        holder.itemView.setOnClickListener { onClick(pasta) }
        holder.itemView.setOnLongClickListener {
            onLongClick(pasta)
            true
        }
    }
}
