package io.recebimento.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.recebimento.R
import io.recebimento.adapters.PastaFotosAdapter
import io.recebimento.utils.FotosRecebimentoStore
import io.recebimento.utils.PastaFotos
import java.util.Calendar

class ImagensRecebimentoActivity : AppCompatActivity() {

    private lateinit var rvPastas: RecyclerView
    private lateinit var layoutEmptyPastas: LinearLayout
    private lateinit var fabNovaPasta: FloatingActionButton
    private lateinit var adapter: PastaFotosAdapter

    private var dataSelecionada = ""
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imagens_recebimento)

        window.statusBarColor = Color.parseColor("#DF1B22")

        rvPastas = findViewById(R.id.rvPastas)
        layoutEmptyPastas = findViewById(R.id.layoutEmptyPastas)
        fabNovaPasta = findViewById(R.id.fabNovaPasta)

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }

        rvPastas.layoutManager = LinearLayoutManager(this)
        adapter = PastaFotosAdapter(
            onClick = { pasta ->
                abrirPasta(pasta.viagem, pasta.data)
            },
            onLongClick = { pasta ->
                mostrarOpcoesPasta(pasta)
            }
        )
        rvPastas.adapter = adapter

        fabNovaPasta.setOnClickListener {
            mostrarDialogNovaPasta()
        }

        carregarPastas()
    }

    private fun carregarPastas() {
        val pastas = FotosRecebimentoStore.getPastas(this)
        adapter.submitList(pastas)
        layoutEmptyPastas.visibility = if (pastas.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun abrirPasta(viagem: String, data: String) {
        val intent = Intent(this, FotosRecebimentoActivity::class.java)
        intent.putExtra(FotosRecebimentoActivity.EXTRA_VIAGEM, viagem)
        intent.putExtra(FotosRecebimentoActivity.EXTRA_DATA, data)
        startActivity(intent)
    }

    private fun mostrarOpcoesPasta(pasta: PastaFotos) {
        val opcoes = arrayOf("Renomear pasta", "Editar data", "Excluir")
        AlertDialog.Builder(this, R.style.Theme_Recebimento_DialogClaro)
            .setTitle("Viagem ${pasta.viagem}")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> mostrarEditarPasta(pasta, renomear = true)
                    1 -> mostrarEditarPasta(pasta, renomear = false)
                    2 -> confirmarExcluirPasta(pasta)
                }
            }
            .show()
    }

    private fun mostrarEditarPasta(pasta: PastaFotos, renomear: Boolean) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_editar_pasta)

        val tvTitulo = dialog.findViewById<TextView>(R.id.tvTituloEditar)
        val tvSubtitulo = dialog.findViewById<TextView>(R.id.tvSubtituloEditar)
        val etViagem = dialog.findViewById<AppCompatEditText>(R.id.etViagemEditar)
        val etData = dialog.findViewById<AppCompatEditText>(R.id.etDataEditar)

        tvTitulo.text = if (renomear) "Renomear Pasta" else "Editar Data"
        tvSubtitulo.text = if (renomear) "Informe o novo número da viagem" else "Selecione a nova data do recebimento"
        etViagem.visibility = if (renomear) View.VISIBLE else View.GONE
        etViagem.setText(pasta.viagem)
        etData.setText(pasta.data)
        etData.isEnabled = !renomear

        etData.setOnClickListener {
            if (!renomear) {
                mostrarDatePicker { dia, mes, ano ->
                    val data = String.format("%02d/%02d/%04d", dia, mes + 1, ano)
                    etData.setText(data)
                }
            }
        }

        dialog.findViewById<View>(R.id.btnCancelarEditar).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btnSalvarEditar).setOnClickListener {
            if (renomear) {
                val novaViagem = etViagem.text.toString().trim()
                if (novaViagem.isEmpty()) {
                    Toast.makeText(this, "Informe o novo número da viagem", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val ok = FotosRecebimentoStore.renomearPasta(this, pasta.viagem, novaViagem)
                if (ok) {
                    dialog.dismiss()
                    carregarPastas()
                    Toast.makeText(this, "Pasta renomeada para $novaViagem", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Não foi possível renomear (nome já existe?)", Toast.LENGTH_SHORT).show()
                }
            } else {
                val novaData = etData.text.toString().trim()
                if (novaData.isEmpty()) {
                    Toast.makeText(this, "Selecione a nova data", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (FotosRecebimentoStore.editarDataPasta(this, pasta.viagem, novaData)) {
                    dialog.dismiss()
                    carregarPastas()
                    Toast.makeText(this, "Data atualizada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Não foi possível atualizar a data", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.CENTER)
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            attributes = params
        }

        dialog.show()
    }

    private fun confirmarExcluirPasta(pasta: PastaFotos) {
        AlertDialog.Builder(this, R.style.Theme_Recebimento_DialogClaro)
            .setTitle("Excluir pasta")
            .setMessage("Excluir a pasta da viagem ${pasta.viagem} e todas as suas fotos?")
            .setPositiveButton("Excluir") { _, _ ->
                if (FotosRecebimentoStore.excluirPasta(this, pasta.viagem)) {
                    carregarPastas()
                    Toast.makeText(this, "Pasta excluída", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Não foi possível excluir", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogNovaPasta() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_nova_pasta)

        val etViagem = dialog.findViewById<AppCompatEditText>(R.id.etViagem)
        val etData = dialog.findViewById<AppCompatEditText>(R.id.etData)

        etData.setOnClickListener {
            mostrarDatePicker { dia, mes, ano ->
                dataSelecionada = String.format("%02d/%02d/%04d", dia, mes + 1, ano)
                etData.setText(dataSelecionada)
            }
        }

        dialog.findViewById<View>(R.id.btnCancelar).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btnCriarPasta).setOnClickListener {
            val viagem = etViagem.text.toString().trim()
            if (viagem.isEmpty()) {
                Toast.makeText(this, "Informe o número da viagem", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dataSelecionada.isEmpty()) {
                Toast.makeText(this, "Selecione a data do recebimento", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FotosRecebimentoStore.criarPasta(this, viagem, dataSelecionada)
            dialog.dismiss()
            carregarPastas()
            abrirPasta(viagem, dataSelecionada)
        }

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.CENTER)
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            attributes = params
        }

        dialog.show()
    }

    private fun mostrarDatePicker(onSelecionado: (dia: Int, mes: Int, ano: Int) -> Unit) {
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, ano, mes, dia -> onSelecionado(dia, mes, ano) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }
}
