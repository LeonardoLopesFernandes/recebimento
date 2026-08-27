package io.recebimento.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import io.recebimento.R
import io.recebimento.adapters.GuiaRollList
import io.recebimento.network.ApiClient
import io.recebimento.network.AutorizarAberturaBauRequest
import io.recebimento.network.BrasilRiskClient
import io.recebimento.network.BrasilRiskNota
import io.recebimento.network.BrasilRiskNotaDetalhe
import io.recebimento.network.DetalhesViagemResponse
import io.recebimento.network.RecebimentoItem
import io.recebimento.network.Guia
import io.recebimento.network.ProtocoloRequest
import io.recebimento.network.ProtocoloResponse
import io.recebimento.network.SessionManager
import io.recebimento.utils.Constants
import io.recebimento.utils.CurrencyFormatter
import io.recebimento.utils.ExcelDownloader
import io.recebimento.utils.LogHelper
import io.recebimento.utils.SessionExpiredHandler
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DetalhesViagemActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: io.recebimento.network.ApiService
    
    // Views
    private lateinit var tvTitle: TextView
    private lateinit var tvViagemData: TextView
    private lateinit var tvOrigem: TextView
    private lateinit var tvPlaca: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvDataRecebimento: TextView
    private lateinit var llGuiasRolls: LinearLayout
    private lateinit var tvLabelGuiasRolls: TextView
    private lateinit var layoutLabelGuias: LinearLayout
    private lateinit var btnGerarExcel: View
    private lateinit var btnItensRisco: View
    private lateinit var btnGerarProtocolo: View
    private lateinit var tvProtocolo: TextView
    private lateinit var ivProtocoloIcon: ImageView
    private lateinit var etBuscarSap: AppCompatEditText
    
    // BRLog (rastreio + liberação)
    private lateinit var layoutBrlog: View
    private lateinit var tvBrlogProgresso: TextView
    private lateinit var tvBrlogMotorista: TextView
    private lateinit var tvBrlogSaiuEntrega: TextView
    private lateinit var tvBrlogChegadaPrevista: TextView
    private lateinit var tvBrlogDistancia: TextView
    private lateinit var tvBrlogEntregaConcluida: TextView
    private lateinit var layoutBrlogAvisoBaJaAutorizado: View
    private lateinit var tvBrlogBauAutorizado: TextView
    private lateinit var btnSolicitarLiberacao: View
    
    private var brlogCodPedido: Int? = null
    private var brlogCodEmpresaUsuario: Int? = null
    private var brlogCodPedidoDestino: Int? = null
    private var brlogCodPedidoOcorrenciaTipo: Int? = null
    
    // Dados
    private var viagemId: String = ""
    private var detalhesViagem: DetalhesViagemResponse? = null
    
    // Estado dos botões
    private var isExcelSelecionado = false
    private var isProtocoloSelecionado = false
    private var isItensRiscoSelecionado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_viagem)

        viagemId = intent.getStringExtra("VIAGEM_ID") ?: ""
        
        if (viagemId.isEmpty()) {
            Toast.makeText(this, "ID da viagem não informado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)
        
        if (!sessionManager.isLoggedIn()) {
            SessionExpiredHandler.handleSessionExpired(this)
            return
        }

        apiService = ApiClient.getInstance(this).getApiService()

        initViews()
        setupListeners()
        carregarDetalhes()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvViagemData = findViewById(R.id.tvViagemData)
        tvOrigem = findViewById(R.id.tvOrigem)
        tvPlaca = findViewById(R.id.tvPlaca)
        tvStatus = findViewById(R.id.tvStatus)
        tvTotal = findViewById(R.id.tvTotal)
        tvDataRecebimento = findViewById(R.id.tvDataRecebimento)
        llGuiasRolls = findViewById(R.id.llGuiasRolls)
        tvLabelGuiasRolls = findViewById(R.id.tvLabelGuiasRolls)
        layoutLabelGuias = findViewById(R.id.layoutLabelGuias)
        btnGerarExcel = findViewById(R.id.btnGerarExcel)
        btnItensRisco = findViewById(R.id.btnItensRisco)
        btnGerarProtocolo = findViewById(R.id.btnGerarProtocolo)
        tvProtocolo = findViewById(R.id.tvProtocolo)
        ivProtocoloIcon = findViewById(R.id.ivProtocoloIcon)
        etBuscarSap = findViewById(R.id.etBuscarSap)

        layoutBrlog = findViewById(R.id.layoutBrlog)
        tvBrlogProgresso = findViewById(R.id.tvBrlogProgresso)
        tvBrlogMotorista = findViewById(R.id.tvBrlogMotorista)
        tvBrlogSaiuEntrega = findViewById(R.id.tvBrlogSaiuEntrega)
        tvBrlogChegadaPrevista = findViewById(R.id.tvBrlogChegadaPrevista)
        tvBrlogDistancia = findViewById(R.id.tvBrlogDistancia)
        tvBrlogEntregaConcluida = findViewById(R.id.tvBrlogEntregaConcluida)
        layoutBrlogAvisoBaJaAutorizado = findViewById(R.id.layoutBrlogAvisoBaJaAutorizado)
        tvBrlogBauAutorizado = findViewById(R.id.tvBrlogBauAutorizado)
        btnSolicitarLiberacao = findViewById(R.id.btnSolicitarLiberacao)
    }

    private fun setupListeners() {
        btnGerarExcel.setOnClickListener {
            toggleExcel()
            gerarExcel()
        }
        
        btnGerarProtocolo.setOnClickListener {
            LogHelper.i("CLICK btnGerarProtocolo")
            toggleProtocolo()
            gerarProtocolo()
        }

        btnItensRisco.setOnClickListener {
            toggleItensRisco()
            abrirItensRisco()
        }

        btnSolicitarLiberacao.setOnClickListener {
            solicitarLiberacaoBau()
        }

        etBuscarSap.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executarBuscaGeral()
                true
            } else {
                false
            }
        }
    }

    private fun executarBuscaGeral() {
        val detalhes = detalhesViagem
        val query = etBuscarSap.text?.toString()?.trim().orEmpty()
        if (detalhes == null) {
            Toast.makeText(this, "⚠️ Dados da viagem não carregados", Toast.LENGTH_SHORT).show()
            return
        }
        if (query.isEmpty()) {
            Toast.makeText(this, "Digite um SAP ou descrição de item", Toast.LENGTH_SHORT).show()
            return
        }

        val todosItens = mutableListOf<RecebimentoItem>()
        detalhes.guias.forEach { guia ->
            guia.recebimentoNota.forEach { nota ->
                nota.recebimentoItem.forEach { item ->
                    todosItens.add(item.copy(guiaOuRoll = "GUIA: ${guia.num}"))
                }
            }
        }
        detalhes.rolls.forEach { roll ->
            roll.recebimentoNota.forEach { nota ->
                nota.recebimentoItem.forEach { item ->
                    val guiaRef = roll.numGuia.ifBlank { roll.num }
                    todosItens.add(item.copy(guiaOuRoll = "GUIA: $guiaRef"))
                }
            }
        }

        val queryLower = query.lowercase()
        val filtrados = todosItens.filter { item ->
            item.id_sap.contains(queryLower) ||
                item.descricao.lowercase().contains(queryLower)
        }

        if (filtrados.isEmpty()) {
            Toast.makeText(this, "Nenhum item encontrado para '$query'", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ItensGuiaActivity::class.java)
        intent.putExtra("TITULO", "BUSCA GERAL")
        intent.putParcelableArrayListExtra("ITENS", ArrayList(filtrados))
        startActivity(intent)
    }

    private fun toggleExcel() {
        isExcelSelecionado = !isExcelSelecionado
    }

    private fun toggleProtocolo() {
        isProtocoloSelecionado = !isProtocoloSelecionado
    }

    private fun toggleItensRisco() {
        isItensRiscoSelecionado = !isItensRiscoSelecionado
    }

private fun gerarExcel() {
    detalhesViagem?.let { detalhes ->
        val storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
        
        Toast.makeText(this, "Baixando relatório...", Toast.LENGTH_LONG).show()
        
        lifecycleScope.launch {
            val result = ExcelDownloader.gerarExcel(
                this@DetalhesViagemActivity,
                apiService,
                storeId,
                detalhes.viagem_id
            )
            result.onSuccess { caminho ->
                Toast.makeText(
                    this@DetalhesViagemActivity,
                    "✅ Excel gerado com sucesso!\nSalvo em: Downloads\nViagem: ${detalhes.viagem_id.takeLast(7)}",
                    Toast.LENGTH_LONG
                ).show()
                isExcelSelecionado = false
            }.onFailure { erro ->
                Toast.makeText(
                    this@DetalhesViagemActivity,
                    "❌ Erro ao gerar Excel: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    } ?: run {
        Toast.makeText(this, "⚠️ Dados da viagem não carregados", Toast.LENGTH_SHORT).show()
    }
}

private fun mostrarProtocoloDialog(protocolo: String) {
    val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
    dialog.setContentView(R.layout.dialog_protocolo_sucesso)

    dialog.findViewById<TextView>(R.id.tvProtocoloDialog).text = protocolo

    dialog.findViewById<View>(R.id.btnOkEntendi).setOnClickListener {
        dialog.dismiss()
    }

    dialog.setCancelable(true)

    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setGravity(android.view.Gravity.CENTER)
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    dialog.show()
}

private fun gerarProtocolo() {
    detalhesViagem?.let { detalhes ->
        val storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
        
        LogHelper.i("gerarProtocolo: iniciando viagem ${detalhes.viagem_id}")
        Toast.makeText(this, "🔄 Gerando protocolo...", Toast.LENGTH_LONG).show()
        
        lifecycleScope.launch {
            try {
                val request = ProtocoloRequest(id_recebimento = detalhes.viagem_id)
                val response = apiService.gerarProtocolo(storeId, request)
                LogHelper.i("gerarProtocolo: HTTP ${response.code()} body=${response.body()}")
                
                isProtocoloSelecionado = false
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        tvProtocolo.text = "PROTOCOLO: ${body.protocolo ?: "Gerado"}"
                        mostrarProtocoloDialog(body.protocolo ?: "Gerado")
                    } else {
                        val msg = body?.message ?: "Erro ao gerar protocolo"
                        Toast.makeText(this@DetalhesViagemActivity, "❌ $msg", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    handleError(response.code())
                }
            } catch (e: IOException) {
                isProtocoloSelecionado = false
                Toast.makeText(this@DetalhesViagemActivity, "Erro de rede: ${e.message}", Toast.LENGTH_SHORT).show()
                LogHelper.e("gerarProtocolo: Erro de rede", e)
            } catch (e: HttpException) {
                isProtocoloSelecionado = false
                handleError(e.code())
                LogHelper.e("gerarProtocolo: HTTP Error", e)
            } catch (e: Exception) {
                isProtocoloSelecionado = false
                Toast.makeText(this@DetalhesViagemActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                LogHelper.e("gerarProtocolo: Erro", e)
            }
        }
    } ?: run {
        Toast.makeText(this, "⚠️ Dados da viagem não carregados", Toast.LENGTH_SHORT).show()
    }
}

    private fun limparUI() {
        tvTitle.text = ""
        tvViagemData.text = ""
        tvOrigem.text = ""
        tvPlaca.text = ""
        tvStatus.text = ""
        tvTotal.text = ""
        tvDataRecebimento.text = ""
        tvProtocolo.text = ""
        detalhesViagem = null
    }

    private fun carregarDetalhes() {
        val storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
        limparUI()

        lifecycleScope.launch {
            try {
                val response = apiService.getDetalhesViagem(storeId, viagemId)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        detalhesViagem = body
                        atualizarUI(body)
                        carregarRastreioBRLog()
                    } else {
                        Toast.makeText(this@DetalhesViagemActivity, "Dados vazios", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    handleError(response.code())
                }
            } catch (e: IOException) {
                Toast.makeText(this@DetalhesViagemActivity, "Erro de rede: ${e.message}", Toast.LENGTH_SHORT).show()
                LogHelper.e("carregarDetalhes: Erro de rede", e)
            } catch (e: HttpException) {
                handleError(e.code())
                LogHelper.e("carregarDetalhes: HTTP Error", e)
            } catch (e: Exception) {
                Toast.makeText(this@DetalhesViagemActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                LogHelper.e("carregarDetalhes: Erro", e)
            }
        }
    }

    private fun atualizarUI(detalhes: DetalhesViagemResponse) {
        // ===== ID =====
        val idShort = if (detalhes.viagem_id.length > 7) {
            detalhes.viagem_id.takeLast(7)
        } else {
            detalhes.viagem_id
        }
        tvTitle.text = idShort
        
        // ===== DATA =====
        val formatadorEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val formatadorSaida = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        try {
            val dataViagem = formatadorEntrada.parse(detalhes.viagem_data) ?: throw Exception("Data nula")
            tvViagemData.text = formatadorSaida.format(dataViagem)
        } catch (e: Exception) {
            tvViagemData.text = detalhes.viagem_data
        }
        
        // ===== ORIGEM =====
        tvOrigem.text = "${detalhes.origem} (${detalhes.codigo_origem})"
        
        // ===== PLACA =====
        tvPlaca.text = detalhes.placa_veiculo
        
        // ===== STATUS =====
        tvStatus.text = detalhes.status.uppercase()
        val statusColor = when (detalhes.status.lowercase()) {
            "pendente" -> R.color.orange
            "erro" -> R.color.red
            "anomalia" -> R.color.yellow
            "recebido" -> R.color.green
            else -> R.color.black
        }
        tvStatus.setTextColor(getColor(statusColor))
        
        // ===== PROTOCOLO - gerar só para pendentes; exibir protocolo quando existir =====
        val isPendente = detalhes.status.lowercase() == "pendente"
        btnGerarProtocolo.visibility = if (isPendente) View.VISIBLE else View.GONE
        if (!detalhes.protocolo.isNullOrEmpty()) {
            tvProtocolo.text = "PROTOCOLO: ${detalhes.protocolo}"
            tvProtocolo.visibility = View.VISIBLE
            ivProtocoloIcon.visibility = View.VISIBLE
        } else {
            tvProtocolo.visibility = if (isPendente) View.VISIBLE else View.GONE
            ivProtocoloIcon.visibility = if (isPendente) View.VISIBLE else View.GONE
        }
        
        // ===== TOTAL =====
        tvTotal.text = CurrencyFormatter.formatarMoedaComSimbolo(detalhes.valorTotalViagem)
        
        // ===== DATA RECEBIMENTO =====
        try {
            val dataReceb = formatadorEntrada.parse(detalhes.data_recebimento) ?: throw Exception("Data nula")
            val formatadorRecebimento = SimpleDateFormat("dd/MM/yyyy - HH'h'mm'min'", Locale.getDefault())
            tvDataRecebimento.text = "Recebimento: ${formatadorRecebimento.format(dataReceb)}"
        } catch (e: Exception) {
            tvDataRecebimento.text = "Recebimento: ${detalhes.data_recebimento}"
        }
        val temGuias = detalhes.guias.isNotEmpty()
        val temRolls = detalhes.rolls.isNotEmpty()
        
        val guiasRollsList = mutableListOf<Any>()
        
        if (temGuias) {
            guiasRollsList.addAll(detalhes.guias)
            
            layoutLabelGuias.visibility = View.VISIBLE
            tvLabelGuiasRolls.text = "GUIAS (${detalhes.guias.size})"
            
        } else if (temRolls) {
            val guiasFromRolls = detalhes.rolls
                .groupBy { it.numGuia }
                .map { (numGuia, rolls) ->
                    Guia(
                        num = numGuia,
                        valorTotal = rolls.sumOf { it.valorTotal },
                        qtd_imei_guia = rolls.sumOf { it.qtd_imei_roll },
                        recebimentoNota = rolls.flatMap { it.recebimentoNota }
                    )
                }
            
            guiasRollsList.addAll(guiasFromRolls)
            
            layoutLabelGuias.visibility = View.VISIBLE
            tvLabelGuiasRolls.text = "GUIAS (${guiasFromRolls.size})"
            
        } else {
            layoutLabelGuias.visibility = View.GONE
        }
        
        // ===== CONFIGURAR LISTA DE GUIAS/ROLLS =====
        if (guiasRollsList.isNotEmpty()) {
            val guiaRollList = GuiaRollList(llGuiasRolls, guiasRollsList)
            guiaRollList.render()
            llGuiasRolls.visibility = View.VISIBLE
        } else {
            llGuiasRolls.visibility = View.GONE
        }
        
        // ===== RESETAR ESTADO DOS BOTÕES =====
        isExcelSelecionado = false
        isProtocoloSelecionado = false
    }

    // ================== BRLOG: RASTREIO + LIBERAÇÃO ==================

    /**
     * Carrega o rastreio da viagem. Primeiro usa a nota cacheada do login BRLog
     * (preenche imediatamente), depois tenta buscar o detalhe atualizado via
     * ObterInformacoesNotaCliente e aplica os campos frescos.
     */
    private fun carregarRastreioBRLog() {
        val viagemNumero = pegarNumeroViagem()

        lifecycleScope.launch {
            // 1) Dados em cache (sync do login BRLog)
            var nota = sessionManager.getBrlogNotas()
                .firstOrNull { it.numeroViagem == viagemNumero }

            if (nota == null) {
                layoutBrlog.visibility = View.GONE
                return@launch
            }

            var codPedido = nota.codPedido
            var codPedidoDestino = nota.codPedidoDestino
            val codPedidoOcorrenciaTipo = nota.codPedidoOcorrenciaTipo
            brlogCodEmpresaUsuario = sessionManager.getBrlogCodEmpresaUsuario().takeIf { it > 0 }

            aplicarRastreioBRLog(nota)

            // 2) Detalhe atualizado via API (busca por CodPedido + CodEmpresaUsuario)
            if (codPedido != null && brlogCodEmpresaUsuario != null && brlogCodEmpresaUsuario != 0) {
                try {
                    val resposta = BrasilRiskClient.getService()
                        .obterInformacoesNotaCliente(brlogCodEmpresaUsuario, codPedido)
                        .execute()
                    if (resposta.isSuccessful) {
                        val detalhe = resposta.body()
                        if (detalhe != null && detalhe.numeroViagem != null) {
                            codPedido = detalhe.codPedido ?: codPedido
                            codPedidoDestino = detalhe.codPedidoDestino ?: codPedidoDestino
                            aplicarRastreioBRLog(detalhe)
                        }
                    }
                } catch (e: Exception) {
                    LogHelper.e("carregarRastreioBRLog: detalhe erro", e)
                }
            }

            brlogCodPedido = codPedido
            brlogCodPedidoDestino = codPedidoDestino
            brlogCodPedidoOcorrenciaTipo = codPedidoOcorrenciaTipo

            // 3) Botão de liberação aparece só quando progresso = 100%
            val progresso = sessionManager.getBrlogProgress()[viagemNumero] ?: nota?.progressoViagem ?: 0.0
            atualizarBotaoLiberacao(progresso)
        }
    }

    private fun aplicarRastreioBRLog(
        nota: BrasilRiskNotaDetalhe
    ) {
        if (isFinishing || isDestroyed) return
        layoutBrlog.visibility = View.VISIBLE

        val progresso = nota.progressoViagem ?: 0.0
        val percentual = if (progresso > 1.0) progresso else progresso * 100.0
        tvBrlogProgresso.text = "${percentual.toInt()}%"

        tvBrlogMotorista.text = nota.nomeMotorista ?: "--"
        tvBrlogSaiuEntrega.text = formatarDataBRLog(nota.dataDeSaida, nota.dataIniciado)
        tvBrlogChegadaPrevista.text = formatarDataBRLog(nota.previsaoChegada, nota.previsaoChegadaRecalculada)
        tvBrlogDistancia.text = formatarDistancia(nota.distanciaRestante)
        tvBrlogEntregaConcluida.text = formatarDataBRLog(nota.dataConclusao, nota.dataEntrega, nota.dataDeEntregaNF)

        val bauAutorizado = nota.aberturaBauAutorizada == true
        if (bauAutorizado) {
            layoutBrlogAvisoBaJaAutorizado.visibility = View.VISIBLE
            tvBrlogBauAutorizado.text = "Baú já autorizado para abertura."
        } else {
            layoutBrlogAvisoBaJaAutorizado.visibility = View.GONE
        }
    }

    private fun aplicarRastreioBRLog(
        nota: BrasilRiskNota
    ) {
        if (isFinishing || isDestroyed) return
        layoutBrlog.visibility = View.VISIBLE

        val progresso = nota.progressoViagem ?: 0.0
        val percentual = if (progresso > 1.0) progresso else progresso * 100.0
        tvBrlogProgresso.text = "${percentual.toInt()}%"

        tvBrlogMotorista.text = nota.nomeMotorista ?: "--"
        tvBrlogSaiuEntrega.text = formatarDataBRLog(nota.dataDeSaida, nota.dataIniciado)
        tvBrlogChegadaPrevista.text = formatarDataBRLog(nota.previsaoChegada, nota.previsaoChegadaRecalculada)
        tvBrlogDistancia.text = formatarDistancia(nota.distanciaRestante)
        tvBrlogEntregaConcluida.text = formatarDataBRLog(nota.dataConclusao, nota.dataEntrega, nota.dataDeEntregaNF)
    }

    private fun atualizarBotaoLiberacao(progresso: Double) {
        var p = progresso
        if (p > 1.0) p /= 100.0
        p = p.coerceIn(0.0, 1.0)
        val completo = p >= 1.0

        val bauJaAutorizado = tvBrlogBauAutorizado.visibility == View.VISIBLE

        btnSolicitarLiberacao.visibility =
            if (completo && !bauJaAutorizado) View.VISIBLE else View.GONE
    }

    private fun solicitarLiberacaoBau() {
        val codPedido = brlogCodPedido
        val codEmpresaUsuario = brlogCodEmpresaUsuario
        val codPedidoDestino = brlogCodPedidoDestino
        val codPedidoOcorrenciaTipo = brlogCodPedidoOcorrenciaTipo

        if (codPedido == null || codEmpresaUsuario == null || codPedidoDestino == null) {
            Toast.makeText(this, "Dados do pedido não disponíveis para liberação.", Toast.LENGTH_LONG).show()
            return
        }
        if (codPedidoOcorrenciaTipo == null) {
            Toast.makeText(this, "Tipo de ocorrência não informado na nota.", Toast.LENGTH_LONG).show()
            return
        }

        val dataCadastro = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        Toast.makeText(this, "Solicitando liberação...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val request = AutorizarAberturaBauRequest(
                    codPedido = codPedido,
                    codEmpresaUsuario = codEmpresaUsuario,
                    dataCadastro = dataCadastro,
                    codPedidoDestino = codPedidoDestino,
                    codPedidoOcorrenciaTipo = codPedidoOcorrenciaTipo
                )
                val resposta = BrasilRiskClient.getService()
                    .autorizarAberturaBau(request)
                    .execute()

                if (resposta.isSuccessful) {
                    val body = resposta.body()
                    val mensagem = body?.mensagem
                    val status = body?.status

                    if (body?.aberturaBauAutorizada == true ||
                        status.equals("OK", true) ||
                        (status == null && mensagem == null)) {
                        Toast.makeText(this@DetalhesViagemActivity, "✅ Liberação autorizada!", Toast.LENGTH_LONG).show()
                        layoutBrlogAvisoBaJaAutorizado.visibility = View.VISIBLE
                        tvBrlogBauAutorizado.text = "Baú autorizado para abertura."
                        btnSolicitarLiberacao.visibility = View.GONE
                    } else {
                        Toast.makeText(
                            this@DetalhesViagemActivity,
                            "❌ ${mensagem ?: "Não autorizado (${status ?: body?.statusCode})"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(this@DetalhesViagemActivity, "Erro HTTP ${resposta.code()} na liberação.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                LogHelper.e("solicitarLiberacaoBau: erro", e)
                Toast.makeText(this@DetalhesViagemActivity, "Erro ao liberar baú: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pegarNumeroViagem(): String {
        val id = detalhesViagem?.viagem_id ?: viagemId
        return if (id.length > 7) id.takeLast(7) else id
    }

    private fun formatarDataBRLog(vararg valores: String?): String {
        for (v in valores) {
            if (v.isNullOrBlank()) continue
            return try {
                val formatadorEntrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val formatadorSaida = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val data = formatadorEntrada.parse(v)
                if (data != null) formatadorSaida.format(data)
                else v
            } catch (e: Exception) {
                v
            }
        }
        return "--"
    }

    private fun formatarDistancia(km: Double?): String {
        if (km == null) return "--"
        return if (km >= 1.0) {
            String.format(java.util.Locale.getDefault(), "%.1f km", km)
        } else {
            "${(km * 1000).toInt()} m"
        }
    }

    private fun abrirItensRisco() {
        val detalhes = detalhesViagem ?: return
        val riscos = setOf("008", "025", "027", "030", "063", "067")

        val todosItens = mutableListOf<RecebimentoItem>()
        detalhes.guias.forEach { guia ->
            guia.recebimentoNota.forEach { nota ->
                nota.recebimentoItem.forEach { item ->
                    todosItens.add(item.copy(guiaOuRoll = "GUIA: ${guia.num}"))
                }
            }
        }
        detalhes.rolls.forEach { roll ->
            roll.recebimentoNota.forEach { nota ->
                nota.recebimentoItem.forEach { item ->
                    val guiaRef = roll.numGuia.ifBlank { roll.num }
                    todosItens.add(item.copy(guiaOuRoll = "GUIA: $guiaRef"))
                }
            }
        }

        val itensRisco = todosItens
            .filter { it.departamento in riscos }
            .sortedBy { it.departamento }

        if (itensRisco.isEmpty()) {
            Toast.makeText(this, "Nenhum item de risco encontrado nesta viagem.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ItensGuiaActivity::class.java)
        intent.putExtra("TITULO", "ITENS DE RISCO")
        intent.putParcelableArrayListExtra("ITENS", ArrayList(itensRisco))
        startActivity(intent)
    }

    private fun handleError(code: Int) {
        when (code) {
            401 -> {
                Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
                SessionExpiredHandler.handleSessionExpired(this)
            }
            403 -> Toast.makeText(this, "Acesso negado.", Toast.LENGTH_SHORT).show()
            404 -> Toast.makeText(this, "Viagem não encontrada.", Toast.LENGTH_SHORT).show()
            500, 502, 503 -> Toast.makeText(this, "Erro no servidor. Tente novamente.", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(this, "Erro: Código $code", Toast.LENGTH_SHORT).show()
        }
    }
}