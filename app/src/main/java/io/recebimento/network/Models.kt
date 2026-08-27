package io.recebimento.network

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName

// ========== RECEBIMENTO MODELS ==========
data class RecebimentoResponse(
    val recebimentos: List<Recebimento>,
    val dateFromGet: String,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val qtdRecebimentos: QtdRecebimentos
)

data class QtdRecebimentos(
    val pendente: Int,
    val erro: Int
)

// ========== DETALHES DA VIAGEM - MODELO UNIFICADO ==========
data class DetalhesViagemResponse(
    val viagem_id: String,
    val viagem_data: String,
    val cnpj_origem: String,
    val codigo_origem: String,
    val origem: String,
    val status: String,
    val qtd_imei: Int,
    val valorTotalViagem: Double,
    val data_recebimento: String,
    val placa_veiculo: String,
    val cnpj_destino: String,
    val protocolo: String? = null,
    val guias: List<Guia> = emptyList(),      // ← Para viagens 2025-
    val rolls: List<Roll> = emptyList()       // ← Para viagens 2026+
)

// ========== GUIA (Modelo 2025) ==========
data class Guia(
    val num: String,
    val valorTotal: Double,
    val qtd_imei_guia: Int,
    @SerializedName("recebimento_nota")
    val recebimentoNota: List<RecebimentoNota>
)

// ========== ROLL (Modelo 2026+) ==========
data class Roll(
    val num: String,
    val numGuia: String,           // ← Número da guia associada ao roll
    val qtd_imei_roll: Int,
    val valorTotal: Double,
    @SerializedName("recebimento_nota")
    val recebimentoNota: List<RecebimentoNota>
)

// ========== RECEBIMENTO NOTA (Comum a ambos) ==========
data class RecebimentoNota(
    val nota_chave: String,
    val nota_numero: String,
    val nota_serie: String,
    val nota_data: String,
    val recebimento_status_descricao: String,
    val data_recebimento: String,
    val erro: String? = null,
    val nota_valor: String,
    @SerializedName("recebimento_item")
    val recebimentoItem: List<RecebimentoItem>
)

// ========== RECEBIMENTO ITEM (Comum a ambos) ==========

    @Parcelize
data class RecebimentoItem(
    val id_sap: String,
    val id_ean: String,
    val descricao: String,
    val quantidade: Int,
    val preco: Double,
    val imeis: List<String> = emptyList(),
    val departamento: String,
    val guiaOuRoll: String? = null
) : Parcelable

// ========== PROTOCOLO ==========
data class ProtocoloRequest(
    val id_recebimento: String
)

data class ProtocoloResponse(
    val protocolo: String? = null,
    val success: Boolean = false,
    val message: String? = null
)

// ========== IMPRESSÃO ==========
data class ImpressaoResponse(
    val success: Boolean = false,
    val message: String? = null,
    val url: String? = null,
    val viagemId: String? = null,
    val error: String? = null  // Adicionar campo de erro
)

// ========== PROGRESSO DA VIAGEM (API BRLog / BrasilRisk) ==========
data class BrasilRiskViagem(
    @SerializedName("NumeroViagem")
    val numeroViagem: String? = null,
    @SerializedName("ProgressoViagem")
    val progressoViagem: Double? = null,
    @SerializedName("StatusDaViagem")
    val statusDaViagem: String? = null,
    @SerializedName("Status")
    val status: String? = null,
    @SerializedName("Mensagem")
    val mensagem: String? = null
)

// ========== LOGIN BRLOG (API BrasilRisk) ==========
data class TokenBody(
    val token: String
)

data class BrasilRiskLoginResponse(
    @SerializedName("CodEmpresa")
    val codEmpresa: Int? = null,
    @SerializedName("CodEmpresaUsuario")
    val codEmpresaUsuario: Int? = null,
    @SerializedName("NomeUsuario")
    val nomeUsuario: String? = null,
    @SerializedName("Mensagem")
    val mensagem: String? = null,
    @SerializedName("Status")
    val status: String? = null,
    @SerializedName("StatusCode")
    val statusCode: Int? = null,
    @SerializedName("NotaFiscal")
    val notaFiscal: List<BrasilRiskNota>? = emptyList()
)

data class BrasilRiskNota(
    @SerializedName("CodPedido")
    val codPedido: Int? = null,
    @SerializedName("CodPedidoDestino")
    val codPedidoDestino: Int? = null,
    @SerializedName("CodPedidoOcorrenciaTipo")
    val codPedidoOcorrenciaTipo: Int? = null,
    @SerializedName("CodEmpresaUsuario")
    val codEmpresaUsuario: Int? = null,
    @SerializedName("NumeroViagem")
    val numeroViagem: String? = null,
    @SerializedName("ProgressoViagem")
    val progressoViagem: Double? = null,
    @SerializedName("CodStatusDaEntrega")
    val codStatusDaEntrega: Int? = null,
    @SerializedName("StatusDaViagem")
    val statusDaViagem: String? = null,
    @SerializedName("DataDeSaida")
    val dataDeSaida: String? = null,
    @SerializedName("DataIniciado")
    val dataIniciado: String? = null,
    @SerializedName("DistanciaRestante")
    val distanciaRestante: Double? = null,
    @SerializedName("DistanciaTotalPrevista")
    val distanciaTotalPrevista: Double? = null,
    @SerializedName("DistanciaPercorrida")
    val distanciaPercorrida: Double? = null,
    @SerializedName("NomeMotorista")
    val nomeMotorista: String? = null,
    @SerializedName("Placa")
    val placa: String? = null,
    @SerializedName("Carreta")
    val carreta: String? = null,
    @SerializedName("PrevisaoChegada")
    val previsaoChegada: String? = null,
    @SerializedName("PrevisaoChegadaRecalculada")
    val previsaoChegadaRecalculada: String? = null,
    @SerializedName("DtPrevisaoEntrega")
    val dtPrevisaoEntrega: String? = null,
    @SerializedName("DataChegadaOrigemRecalculada")
    val dataChegadaOrigemRecalculada: String? = null,
    @SerializedName("DataConclusao")
    val dataConclusao: String? = null,
    @SerializedName("DataEntrega")
    val dataEntrega: String? = null,
    @SerializedName("DataDeEntregaNF")
    val dataDeEntregaNF: String? = null,
    @SerializedName("Status")
    val status: String? = null
)

// ========== DETALHE DA NOTA (API BRLog) ==========
data class BrasilRiskNotaDetalhe(
    @SerializedName("AberturaBauAutorizada")
    val aberturaBauAutorizada: Boolean? = null,
    @SerializedName("CidadeCliente")
    val cidadeCliente: String? = null,
    @SerializedName("CodDestino")
    val codDestino: Int? = null,
    @SerializedName("CodEmpresaNF")
    val codEmpresaNF: Int? = null,
    @SerializedName("CodEmpresaTransacaoNF")
    val codEmpresaTransacaoNF: Int? = null,
    @SerializedName("CodEmpresaUsuario")
    val codEmpresaUsuario: Int? = null,
    @SerializedName("CodPedido")
    val codPedido: Int? = null,
    @SerializedName("CodPedidoDestino")
    val codPedidoDestino: Int? = null,
    @SerializedName("CodPedidoStatus")
    val codPedidoStatus: Int? = null,
    @SerializedName("NomeStatus")
    val nomeStatus: String? = null,
    @SerializedName("CodStatusDaEntrega")
    val codStatusDaEntrega: Int? = null,
    @SerializedName("CodStatusNF")
    val codStatusNF: Int? = null,
    @SerializedName("DataDeChegada")
    val dataDeChegada: String? = null,
    @SerializedName("DataDeEntregaNF")
    val dataDeEntregaNF: String? = null,
    @SerializedName("DataDeSaida")
    val dataDeSaida: String? = null,
    @SerializedName("DataEmissaoNF")
    val dataEmissaoNF: String? = null,
    @SerializedName("DataEntrega")
    val dataEntrega: String? = null,
    @SerializedName("DataIniciado")
    val dataIniciado: String? = null,
    @SerializedName("DataConclusao")
    val dataConclusao: String? = null,
    @SerializedName("DistanciaPercorrida")
    val distanciaPercorrida: Double? = null,
    @SerializedName("DistanciaRestante")
    val distanciaRestante: Double? = null,
    @SerializedName("DistanciaTotalPrevista")
    val distanciaTotalPrevista: Double? = null,
    @SerializedName("DtPrevisaoEntrega")
    val dtPrevisaoEntrega: String? = null,
    @SerializedName("Mensagem")
    val mensagem: String? = null,
    @SerializedName("NF")
    val nf: String? = null,
    @SerializedName("NomeCliente")
    val nomeCliente: String? = null,
    @SerializedName("NomeMotorista")
    val nomeMotorista: String? = null,
    @SerializedName("NomeTransportadora")
    val nomeTransportadora: String? = null,
    @SerializedName("NrSerieNF")
    val nrSerieNF: String? = null,
    @SerializedName("NumeroViagem")
    val numeroViagem: String? = null,
    @SerializedName("Origem")
    val origem: String? = null,
    @SerializedName("Placa")
    val placa: String? = null,
    @SerializedName("Carreta")
    val carreta: String? = null,
    @SerializedName("DataColheita")
    val dataColheita: String? = null,
    @SerializedName("PrevisaoChegada")
    val previsaoChegada: String? = null,
    @SerializedName("PrevisaoChegadaRecalculada")
    val previsaoChegadaRecalculada: String? = null,
    @SerializedName("Produto")
    val produto: String? = null,
    @SerializedName("ProgressoViagem")
    val progressoViagem: Double? = null,
    @SerializedName("Status")
    val status: String? = null,
    @SerializedName("StatusCode")
    val statusCode: Int? = null,
    @SerializedName("StatusNF")
    val statusNF: String? = null,
    @SerializedName("TemperaturaAtual")
    val temperaturaAtual: Double? = null,
    @SerializedName("TemperaturaSaida")
    val temperaturaSaida: Double? = null,
    @SerializedName("TemperaturaMaxima")
    val temperaturaMaxima: Double? = null,
    @SerializedName("TemperaturaEntrega")
    val temperaturaEntrega: Double? = null,
    @SerializedName("Localizacao")
    val localizacao: String? = null,
    @SerializedName("LatitudeVeiculo")
    val latitudeVeiculo: Double? = null,
    @SerializedName("LongitudeVeiculo")
    val longitudeVeiculo: Double? = null,
    @SerializedName("Contrato")
    val contrato: String? = null,
    @SerializedName("Hibrido")
    val hibrido: Boolean? = null,
    @SerializedName("LatitudeDestino")
    val latitudeDestino: Double? = null,
    @SerializedName("LongitudeDestino")
    val longitudeDestino: Double? = null,
    @SerializedName("LatitudeOrigem")
    val latitudeOrigem: Double? = null,
    @SerializedName("LongitudeOrigem")
    val longitudeOrigem: Double? = null,
    @SerializedName("DataChegadaOrigemRecalculada")
    val dataChegadaOrigemRecalculada: String? = null,
    @SerializedName("Regional")
    val regional: String? = null,
    @SerializedName("StatusDaViagem")
    val statusDaViagem: String? = null,
    @SerializedName("DataChegadaCampo")
    val dataChegadaCampo: String? = null,
    @SerializedName("DataSaidaCampo")
    val dataSaidaCampo: String? = null
)

// ========== AUTORIZAR ABERTURA DO BAÚ (API BRLog) ==========
data class AutorizarAberturaBauRequest(
    @SerializedName("CodPedido")
    val codPedido: Int,
    @SerializedName("CodEmpresaUsuario")
    val codEmpresaUsuario: Int,
    @SerializedName("DataCadastro")
    val dataCadastro: String,
    @SerializedName("CodPedidoDestino")
    val codPedidoDestino: Int,
    @SerializedName("CodPedidoOcorrenciaTipo")
    val codPedidoOcorrenciaTipo: Int
)

data class BrasilRiskBauResponse(
    @SerializedName("Mensagem")
    val mensagem: String? = null,
    @SerializedName("Status")
    val status: String? = null,
    @SerializedName("StatusCode")
    val statusCode: Int? = null,
    @SerializedName("AberturaBauAutorizada")
    val aberturaBauAutorizada: Boolean? = null
)

data class Recebimento(
    @SerializedName("_id") 
    val id: String,
    val viagem_data: String,
    val codigo_origem: String,
    val origem: String,
    val status: String,
    val placa_veiculo: String,
    val data_recebimento: String? = null,  // Pode ser null para pendentes
    val qtd_rolls: Int,
    val qtd_guias: Int,
    val protocolo: String? = null,  // Pode ser null para pendentes
    @SerializedName(value = "valor_total", alternate = ["valorTotalViagem", "valorTotal"])
    val valorTotal: Double = 0.0
)


