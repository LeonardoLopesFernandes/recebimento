package io.recebimento.utils

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PastaFotos(
    val viagem: String,
    val data: String,
    val caminhoFotos: List<String>
)

object FotosRecebimentoStore {

    private const val PASTA_ROOT = "fotos_recebimento"
    private const val PASTA_CAPTURADAS = "capturadas"
    private const val METADATA = "metadata.json"

    fun raiz(context: Context): File =
        File(context.filesDir, PASTA_ROOT).apply { mkdirs() }

    fun pastaCapturadas(context: Context): File =
        File(raiz(context), PASTA_CAPTURADAS).apply { mkdirs() }

    fun pastaDir(context: Context, viagem: String): File =
        File(raiz(context), sanitizar(viagem)).apply { mkdirs() }

    fun getPastas(context: Context): List<PastaFotos> {
        val raiz = raiz(context)
        return raiz.listFiles()?.filter { it.isDirectory && it.name != PASTA_CAPTURADAS }
            ?.mapNotNull { dir ->
                val viagem = dir.name
                val data = lerData(dir)
                val fotos = dir.listFiles()
                    ?.filter { it.isFile && it.extension == "jpg" }
                    ?.sortedByDescending { it.name }
                    ?.map { it.absolutePath }
                    ?: emptyList()
                PastaFotos(viagem, data, fotos)
            }
            ?.sortedByDescending { it.data }
            ?: emptyList()
    }

    fun criarPasta(context: Context, viagem: String, data: String): File {
        val dir = pastaDir(context, viagem)
        dir.mkdirs()
        val meta = File(dir, METADATA)
        if (!meta.exists()) {
            meta.writeText(
                JSONObject()
                    .put("data", data)
                    .put("viagem", viagem)
                    .toString()
            )
        }
        return dir
    }

    fun getFotos(context: Context, viagem: String): List<String> {
        val dir = pastaDir(context, viagem)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "jpg" }
            ?.sortedByDescending { it.name }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    fun getCapturadas(context: Context): List<String> {
        val dir = pastaCapturadas(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "jpg" }
            ?.sortedByDescending { it.name }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    fun novoArquivoCaptura(context: Context): File {
        val dir = pastaCapturadas(context)
        val nome = "foto_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_${System.currentTimeMillis()}.jpg"
        return File(dir, nome)
    }

    fun moverParaPasta(context: Context, viagem: String, caminhoFoto: String): Boolean {
        val origem = File(caminhoFoto)
        if (!origem.exists()) return false
        val destino = File(pastaDir(context, viagem), origem.name)
        return try {
            if (destino.exists()) destino.delete()
            origem.renameTo(destino)
        } catch (e: Exception) {
            false
        }
    }

    fun excluirFoto(context: Context, viagem: String, caminhoFoto: String): Boolean {
        val dir = pastaDir(context, viagem)
        val destino = File(dir, File(caminhoFoto).name)
        return try {
            if (destino.exists()) {
                destino.delete()
                val restantes = dir.listFiles()?.filter { it.isFile && it.extension == "jpg" }?.size ?: 0
                if (restantes == 0) {
                    dir.delete()
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun renomearFoto(context: Context, caminhoFoto: String, novoNome: String): Boolean {
        val origem = File(caminhoFoto)
        if (!origem.exists()) return false
        val nomeLimpo = sanitizar(novoNome.trim()).ifEmpty { return false }
        val destino = File(origem.parentFile, "$nomeLimpo.${origem.extension}")
        if (destino.exists()) return false
        return origem.renameTo(destino)
    }

    fun renomearPasta(context: Context, viagemAtual: String, novaViagem: String): Boolean {
        val dir = pastaDir(context, viagemAtual)
        if (!dir.exists()) return false
        val novoDir = File(raiz(context), sanitizar(novaViagem.trim()))
        if (novoDir.exists()) return false
        val renomeou = dir.renameTo(novoDir)
        if (renomeou) {
            try {
                val meta = File(novoDir, METADATA)
                if (meta.exists()) {
                    meta.writeText(
                        JSONObject()
                            .put("data", lerData(novoDir))
                            .put("viagem", novaViagem.trim())
                            .toString()
                    )
                }
            } catch (e: Exception) {
            }
        }
        return renomeou
    }

    fun editarDataPasta(context: Context, viagem: String, novaData: String): Boolean {
        val meta = File(pastaDir(context, viagem), METADATA)
        return try {
            meta.writeText(
                JSONObject()
                    .put("data", novaData)
                    .put("viagem", viagem)
                    .toString()
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun excluirPasta(context: Context, viagem: String): Boolean {
        val dir = pastaDir(context, viagem)
        return dir.exists() && dir.deleteRecursively()
    }

    private fun lerData(dir: File): String {
        val meta = File(dir, METADATA)
        return if (meta.exists()) {
            try {
                JSONObject(meta.readText()).optString("data", "")
            } catch (e: Exception) {
                ""
            }
        } else ""
    }

    private fun sanitizar(viagem: String): String =
        viagem.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
