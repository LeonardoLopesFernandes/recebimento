package io.recebimento.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.recebimento.R
import io.recebimento.adapters.ItemRecebimentoAdapter
import io.recebimento.network.RecebimentoItem
import io.recebimento.utils.CurrencyFormatter
import io.recebimento.utils.ExcelDownloader
import kotlinx.coroutines.launch

class ItensGuiaActivity : AppCompatActivity() {

    private lateinit var tvTitulo: TextView
    private lateinit var tvTotal: TextView
    private lateinit var rvItens: RecyclerView
    private lateinit var etBuscarItem: AppCompatEditText
    private lateinit var btnLimparBusca: ImageButton
    private lateinit var btnGerarExcel: TextView
    private lateinit var btnGerarPdf: TextView

    private lateinit var adapter: ItemRecebimentoAdapter
    private var todosItens = mutableListOf<RecebimentoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_itens_guia)

        tvTitulo = findViewById(R.id.tvTitulo)
        tvTotal = findViewById(R.id.tvTotal)
        rvItens = findViewById(R.id.rvItens)
        etBuscarItem = findViewById(R.id.etBuscarItem)
        btnLimparBusca = findViewById(R.id.btnLimparBusca)
        btnGerarExcel = findViewById(R.id.btnGerarExcel)
        btnGerarPdf = findViewById(R.id.btnGerarPdf)

        // Pegar dados da intent
        val titulo = intent.getStringExtra("TITULO") ?: "Itens"
        val itens = intent.getSerializableExtra("ITENS") as? List<RecebimentoItem> ?: emptyList()

        tvTitulo.text = titulo
        val soma = itens.sumOf { it.preco }
        tvTotal.text = "Total: ${CurrencyFormatter.formatarMoedaComSimbolo(soma)}"

        // Salvar todos os itens
        todosItens.clear()
        todosItens.addAll(itens)

        // Configurar adapter
        adapter = ItemRecebimentoAdapter(todosItens)
        rvItens.layoutManager = LinearLayoutManager(this)
        rvItens.adapter = adapter

        // Busca
        etBuscarItem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isEmpty()) {
                    adapter.updateItems(todosItens)
                } else {
                    filtrarItens(query)
                }
            }
        })

        btnLimparBusca.setOnClickListener {
            etBuscarItem.text?.clear()
            adapter.updateItems(todosItens)
        }

        btnGerarExcel.setOnClickListener {
            if (todosItens.isEmpty()) {
                Toast.makeText(this, "Nenhum item para gerar Excel.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "📥 Gerando Excel...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val result = ExcelDownloader.gerarXlsxItens(
                    context = this@ItensGuiaActivity,
                    viagemId = titulo,
                    prefixo = "itens",
                    itens = todosItens
                )
                result.onSuccess { caminho ->
                    Toast.makeText(this@ItensGuiaActivity, "✅ Excel salvo em: $caminho", Toast.LENGTH_LONG).show()
                }.onFailure { erro ->
                    Toast.makeText(this@ItensGuiaActivity, "❌ ${erro.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnGerarPdf.setOnClickListener {
            if (todosItens.isEmpty()) {
                Toast.makeText(this, "Nenhum item para gerar PDF.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "📥 Gerando PDF...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val result = ExcelDownloader.gerarPdfItens(
                    context = this@ItensGuiaActivity,
                    titulo = titulo,
                    prefixo = "itens",
                    itens = todosItens,
                    total = soma
                )
                result.onSuccess { caminho ->
                    Toast.makeText(this@ItensGuiaActivity, "✅ PDF salvo em: $caminho", Toast.LENGTH_LONG).show()
                }.onFailure { erro ->
                    Toast.makeText(this@ItensGuiaActivity, "❌ ${erro.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun filtrarItens(query: String) {
        val queryLower = query.lowercase()
        val filtrados = todosItens.filter { item ->
            item.descricao.lowercase().contains(queryLower) ||
            item.id_sap.contains(queryLower) ||
            item.departamento.contains(queryLower) ||
            item.id_ean.contains(queryLower)
        }
        adapter.updateItems(filtrados)
    }
}