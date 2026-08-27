package io.recebimento.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.recebimento.R
import io.recebimento.network.Guia
import io.recebimento.network.RecebimentoItem
import io.recebimento.network.Roll
import io.recebimento.ui.ItensGuiaActivity
import io.recebimento.utils.CurrencyFormatter

class GuiaRollList(
    private val container: LinearLayout,
    private val items: List<Any> // Pode ser Guia ou Roll
) {

    private var expandedIndex = -1

    fun render() {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)

        items.forEachIndexed { index, item ->
            val view = inflater.inflate(R.layout.item_guia_roll, container, false)
            val isExpanded = index == expandedIndex

            when (item) {
                is Guia -> bindGuia(view, item, isExpanded)
                is Roll -> bindRoll(view, item, isExpanded)
            }

            view.setOnClickListener {
                toggleExpand(index)
            }
            view.findViewById<View>(R.id.tvExpandir).setOnClickListener {
                toggleExpand(index)
            }

            container.addView(view)
        }
    }

    private fun toggleExpand(index: Int) {
        expandedIndex = if (expandedIndex == index) -1 else index
        render()
    }

    private fun bindGuia(view: View, guia: Guia, expanded: Boolean) {
        // Número da GUIA
        view.findViewById<TextView>(R.id.tvNumero).text = "GUIA: ${guia.num}"

        // Valor Total da Guia
        view.findViewById<TextView>(R.id.tvValorTotal).text =
            CurrencyFormatter.formatarMoedaComSimbolo(guia.valorTotal)

        // Detalhes: Notas + NFs em linha única
        val detalhesStr = buildString {
            append("Notas: ${guia.recebimentoNota.size}")
            guia.recebimentoNota.forEach { nota ->
                append(" • NF ${nota.nota_numero} (Série ${nota.nota_serie})")
            }
        }
        view.findViewById<TextView>(R.id.tvNotas).text = detalhesStr

        // Coletar produtos e ordenar por departamento
        val produtos = mutableListOf<RecebimentoItem>()
        guia.recebimentoNota.forEach { nota ->
            produtos.addAll(nota.recebimentoItem)
        }
        produtos.sortBy { it.departamento }

        configurarExpansao(view, produtos, expanded)
        configurarVerTudo(view, produtos, "GUIA: ${guia.num}", guia.valorTotal)
    }

    private fun bindRoll(view: View, roll: Roll, expanded: Boolean) {
        // Número do ROLL
        val numGuia = roll.numGuia.ifBlank { roll.num }
        view.findViewById<TextView>(R.id.tvNumero).text = "GUIA: $numGuia"

        // Valor Total do Roll
        view.findViewById<TextView>(R.id.tvValorTotal).text =
            CurrencyFormatter.formatarMoedaComSimbolo(roll.valorTotal)

        // Detalhes: Notas + NFs em linha única
        val detalhesStr = buildString {
            append("Notas: ${roll.recebimentoNota.size}")
            roll.recebimentoNota.forEach { nota ->
                append(" • NF ${nota.nota_numero} (Série ${nota.nota_serie})")
            }
        }
        view.findViewById<TextView>(R.id.tvNotas).text = detalhesStr

        // Coletar produtos e ordenar por departamento
        val produtos = mutableListOf<RecebimentoItem>()
        roll.recebimentoNota.forEach { nota ->
            produtos.addAll(nota.recebimentoItem)
        }
        produtos.sortBy { it.departamento }

        configurarExpansao(view, produtos, expanded)
        configurarVerTudo(view, produtos, "GUIA: $numGuia", roll.valorTotal)
    }

    private fun configurarExpansao(view: View, produtos: List<RecebimentoItem>, expanded: Boolean) {
        val llProdutos = view.findViewById<LinearLayout>(R.id.llProdutos)
        val tvExpandirText = view.findViewById<TextView>(R.id.tvExpandirText)

        if (expanded) {
            llProdutos.removeAllViews()
            val inflater = LayoutInflater.from(view.context)
            produtos.forEach { produto ->
                val itemView = inflater.inflate(R.layout.item_produto_simples, llProdutos, false)
                itemView.findViewById<TextView>(R.id.tvDescricao).text = produto.descricao
                itemView.findViewById<TextView>(R.id.tvQuantidade).text = "Qtd: ${produto.quantidade}"
                itemView.findViewById<TextView>(R.id.tvDepartamento).text = "Dep: ${produto.departamento}"
                itemView.findViewById<TextView>(R.id.tvSap).text =
                    "SAP: ${produto.id_sap.toLongOrNull()?.toString() ?: produto.id_sap}"
                llProdutos.addView(itemView)
            }
            llProdutos.visibility = View.VISIBLE
            tvExpandirText.text = "Ocultar produtos (${produtos.size})"
        } else {
            llProdutos.visibility = View.GONE
            tvExpandirText.text = "Ver produtos (${produtos.size})"
        }
    }

    private fun configurarVerTudo(view: View, produtos: List<RecebimentoItem>, titulo: String, valorTotal: Double) {
        val context = view.context
        view.findViewById<TextView>(R.id.tvVerTudo).setOnClickListener {
            Toast.makeText(context, "Carregando detalhes da guia...", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, ItensGuiaActivity::class.java)
            intent.putExtra("TITULO", titulo)
            intent.putParcelableArrayListExtra("ITENS", ArrayList(produtos))
            intent.putExtra("VALOR_TOTAL", valorTotal)
            context.startActivity(intent)
        }
    }
}
