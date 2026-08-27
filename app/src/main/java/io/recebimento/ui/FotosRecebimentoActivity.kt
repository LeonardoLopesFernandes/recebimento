package io.recebimento.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.recebimento.R
import io.recebimento.adapters.FotosAdapter
import io.recebimento.utils.FotosRecebimentoStore
import io.recebimento.utils.LogHelper
import java.io.File

class FotosRecebimentoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIAGEM = "VIAGEM"
        const val EXTRA_DATA = "DATA"
        private const val REQ_CAMERA = 2001
    }

    private var viagem = ""
    private var data = ""
    private lateinit var rvFotos: RecyclerView
    private lateinit var layoutEmptyFotos: LinearLayout
    private lateinit var fabCamera: FloatingActionButton
    private lateinit var fabMover: FloatingActionButton
    private lateinit var fabAdicionar: FloatingActionButton
    private lateinit var btnAdicionarGaleria: TextView
    private lateinit var adapter: FotosAdapter

    private val selecionarGaleria =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            adicionarImagensDaGaleria(uris)
        }

    private enum class OrdemFotos { DATA_RECENTES, DATA_ANTIGAS, NOME_AZ, NOME_ZA }

    private var ordemAtual = OrdemFotos.DATA_RECENTES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fotos_recebimento)

        viagem = intent.getStringExtra(EXTRA_VIAGEM) ?: ""
        data = intent.getStringExtra(EXTRA_DATA) ?: ""

        window.statusBarColor = Color.parseColor("#DF1B22")

        findViewById<TextView>(R.id.tvViagemFotos).text = "Viagem $viagem"
        findViewById<TextView>(R.id.tvDataFotos).text = data.ifEmpty { "Data não informada" }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }

        rvFotos = findViewById(R.id.rvFotos)
        layoutEmptyFotos = findViewById(R.id.layoutEmptyFotos)
        fabCamera = findViewById(R.id.fabCamera)
        fabMover = findViewById(R.id.fabMoverFotos)
        fabAdicionar = findViewById(R.id.fabAdicionarFotos)
        btnAdicionarGaleria = findViewById(R.id.btnAdicionarGaleria)

        rvFotos.layoutManager = GridLayoutManager(this, 3)
        adapter = FotosAdapter(
            onClick = { caminho ->
                mostrarFoto(caminho)
            },
            onLongClick = { caminho ->
                mostrarOpcoesFoto(caminho)
            }
        )
        rvFotos.adapter = adapter

        fabCamera.setOnClickListener {
            abrirCameraDiretoNaPasta()
        }

        fabMover.setOnClickListener {
            mostrarSeletorCapturadas()
        }

        fabAdicionar.setOnClickListener {
            selecionarGaleria.launch("image/*")
        }

        btnAdicionarGaleria.setOnClickListener {
            selecionarGaleria.launch("image/*")
        }

        findViewById<View>(R.id.btnOrdenarFotos).setOnClickListener {
            mostrarOpcoesOrdenacao()
        }

        carregarFotos()
    }

    private fun mostrarOpcoesOrdenacao() {
        val opcoes = arrayOf(
            "Data (recentes primeiro)",
            "Data (antigas primeiro)",
            "Nome (A-Z)",
            "Nome (Z-A)"
        )
        AlertDialog.Builder(this, R.style.Theme_Recebimento_DialogClaro)
            .setTitle("Ordenar imagens")
            .setItems(opcoes) { _, which ->
                ordemAtual = when (which) {
                    0 -> OrdemFotos.DATA_RECENTES
                    1 -> OrdemFotos.DATA_ANTIGAS
                    2 -> OrdemFotos.NOME_AZ
                    else -> OrdemFotos.NOME_ZA
                }
                carregarFotos()
            }
            .show()
    }

    private fun mostrarOpcoesFoto(caminho: String) {
        val nome = File(caminho).name
        val opcoes = arrayOf("Renomear imagem", "Excluir")
        AlertDialog.Builder(this, R.style.Theme_Recebimento_DialogClaro)
            .setTitle(nome)
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> mostrarRenomearFoto(caminho)
                    1 -> confirmarExcluirFoto(caminho)
                }
            }
            .show()
    }

    private fun mostrarRenomearFoto(caminho: String) {
        val atual = File(caminho).name.removeSuffix(".${File(caminho).extension}")
        val et = AppCompatEditText(this).apply {
            setText(atual)
            hint = "Novo nome da imagem"
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this, R.style.Theme_Recebimento_DialogClaro)
            .setTitle("Renomear imagem")
            .setView(et)
            .setPositiveButton("Salvar") { _, _ ->
                val novoNome = et.text.toString().trim()
                if (novoNome.isEmpty()) {
                    Toast.makeText(this, "Informe um nome válido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (FotosRecebimentoStore.renomearFoto(this, caminho, novoNome)) {
                    carregarFotos()
                    Toast.makeText(this, "Imagem renomeada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Não foi possível renomear (nome já existe?)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarExcluirFoto(caminho: String) {
        AlertDialog.Builder(this, R.style.Theme_Recebimento_DialogClaro)
            .setTitle("Excluir imagem")
            .setMessage("Excluir a imagem ${File(caminho).name}?")
            .setPositiveButton("Excluir") { _, _ ->
                if (FotosRecebimentoStore.excluirFoto(this, viagem, caminho)) {
                    carregarFotos()
                    Toast.makeText(this, "Imagem excluída", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Não foi possível excluir", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun adicionarImagensDaGaleria(uris: List<Uri>) {
        if (uris.isEmpty()) return

        var copiadas = 0
        uris.forEachIndexed { index, uri ->
            try {
                val nome = "galeria_${System.currentTimeMillis()}_$index.jpg"
                val destino = File(FotosRecebimentoStore.pastaDir(this, viagem), nome)
                contentResolver.openInputStream(uri)?.use { input ->
                    destino.outputStream().use { output -> input.copyTo(output) }
                }
                copiadas++
            } catch (e: Exception) {
                LogHelper.e("adicionarImagensDaGaleria: erro ao copiar", e)
            }
        }

        if (copiadas > 0) {
            carregarFotos()
            Toast.makeText(this, "$copiadas imagem(ns) adicionada(s) à viagem $viagem", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Não foi possível adicionar as imagens", Toast.LENGTH_SHORT).show()
        }
    }

    private fun carregarFotos() {
        val fotos = FotosRecebimentoStore.getFotos(this, viagem)
        val ordenadas = when (ordemAtual) {
            OrdemFotos.DATA_RECENTES -> fotos.sortedByDescending { File(it).lastModified() }
            OrdemFotos.DATA_ANTIGAS -> fotos.sortedBy { File(it).lastModified() }
            OrdemFotos.NOME_AZ -> fotos.sortedBy { File(it).name.lowercase() }
            OrdemFotos.NOME_ZA -> fotos.sortedByDescending { File(it).name.lowercase() }
        }
        adapter.submitList(ordenadas)
        layoutEmptyFotos.visibility = if (fotos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun abrirCameraDiretoNaPasta() {
        try {
            val dir = FotosRecebimentoStore.pastaDir(this, viagem)
            dir.mkdirs()
            val foto = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", foto)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            Toast.makeText(this, "Câmera não disponível", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQ_CAMERA) {
                carregarFotos()
                Toast.makeText(this, "📸 Foto salva na viagem $viagem", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarSeletorCapturadas() {
        val capturadas = FotosRecebimentoStore.getCapturadas(this)
        if (capturadas.isEmpty()) {
            Toast.makeText(this, "Nenhuma foto capturada. Tire fotos pelo botão da câmera na tela inicial.", Toast.LENGTH_LONG).show()
            return
        }

        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_selecionar_fotos, null)
        bottomSheet.setContentView(view)

        val rv = view.findViewById<RecyclerView>(R.id.rvCapturadas)
        val tvContador = view.findViewById<TextView>(R.id.tvContador)
        val tvSemFotos = view.findViewById<TextView>(R.id.tvSemFotos)
        val btnMover = view.findViewById<TextView>(R.id.btnMoverSelecionadas)

        tvSemFotos.visibility = View.GONE

        val selecionadas = mutableSetOf<String>()
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = CapturadasAdapter(capturadas, selecionadas) {
            tvContador.text = "${selecionadas.size} selecionada(s)"
        }

        btnMover.setOnClickListener {
            if (selecionadas.isEmpty()) {
                Toast.makeText(this, "Selecione ao menos uma foto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            var movidas = 0
            selecionadas.forEach { caminho ->
                if (FotosRecebimentoStore.moverParaPasta(this, viagem, caminho)) movidas++
            }
            bottomSheet.dismiss()
            carregarFotos()
            Toast.makeText(this, "$movidas foto(s) movida(s) para a viagem $viagem", Toast.LENGTH_SHORT).show()
        }

        bottomSheet.show()
    }

    private fun mostrarFoto(caminho: String) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_visualizar_foto)
        dialog.findViewById<ImageView>(R.id.ivFotoView).setImageBitmap(carregarFotoGrande(caminho))
        dialog.findViewById<View>(R.id.btnFecharFoto).setOnClickListener { dialog.dismiss() }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.CENTER)
            val params = attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            attributes = params
        }
        dialog.show()
    }

    private fun carregarFotoGrande(caminho: String): android.graphics.Bitmap? {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(caminho, opts)
            var sample = 1
            while (opts.outWidth / sample > 1600 && opts.outHeight / sample > 1600) {
                sample *= 2
            }
            android.graphics.BitmapFactory.decodeFile(caminho, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    private inner class CapturadasAdapter(
        private val itens: List<String>,
        private val selecionadas: MutableSet<String>,
        private val onMudanca: () -> Unit
    ) : RecyclerView.Adapter<CapturadaVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapturadaVH {
            val view = layoutInflater.inflate(R.layout.item_foto_selecionar, parent, false)
            return CapturadaVH(view)
        }

        override fun getItemCount(): Int = itens.size

        override fun onBindViewHolder(holder: CapturadaVH, position: Int) {
            val caminho = itens[position]
            holder.ivFoto.setImageBitmap(carregarThumb(caminho))
            holder.tvNome.text = File(caminho).name
            holder.cb.isChecked = selecionadas.contains(caminho)
            holder.itemView.setOnClickListener {
                if (selecionadas.contains(caminho)) selecionadas.remove(caminho) else selecionadas.add(caminho)
                holder.cb.isChecked = selecionadas.contains(caminho)
                onMudanca()
            }
            holder.cb.setOnClickListener {
                if (holder.cb.isChecked) selecionadas.add(caminho) else selecionadas.remove(caminho)
                onMudanca()
            }
        }

        private fun carregarThumb(caminho: String): android.graphics.Bitmap? {
            return try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(caminho, opts)
                var sample = 1
                while (opts.outWidth / sample > 300 && opts.outHeight / sample > 300) {
                    sample *= 2
                }
                android.graphics.BitmapFactory.decodeFile(caminho, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (e: Exception) {
                null
            }
        }
    }
}

private class CapturadaVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val ivFoto: ImageView = itemView.findViewById(R.id.ivFotoSel)
    val tvNome: TextView = itemView.findViewById(R.id.tvNomeFoto)
    val cb: android.widget.CheckBox = itemView.findViewById(R.id.cbSelecionar)
}
