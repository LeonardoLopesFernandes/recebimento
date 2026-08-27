package io.recebimento.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ========== RECEBIMENTOS ==========
    @GET("web/recebimento/{storeId}")
    suspend fun getRecebimentos(
        @Path("storeId") storeId: String,
        @Query("status") status: String,
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "asc",
        @Query("page") page: Int = 1
    ): Response<RecebimentoResponse>

    // ========== DETALHES DA VIAGEM ==========
    @GET("web/recebimento/{storeId}/{viagemId}")
    suspend fun getDetalhesViagem(
        @Path("storeId") storeId: String,
        @Path("viagemId") viagemId: String
    ): Response<DetalhesViagemResponse>

    // ========== GERAR PROTOCOLO ==========
    @POST("web/recebimento/{storeId}")
    suspend fun gerarProtocolo(
        @Path("storeId") storeId: String,
        @Body request: ProtocoloRequest
    ): Response<ProtocoloResponse>

    // ========== IMPRIMIR VIAGEM ==========
    @GET("web/recebimento/print/{storeId}/{viagemId}")
    suspend fun imprimirViagem(
        @Path("storeId") storeId: String,
        @Path("viagemId") viagemId: String
    ): Response<ImpressaoResponse>

    // ========== GERAR EXCEL DA VIAGEM ==========
    @GET("web/recebimento/planilha/{storeId}/{viagemId}")
    @Streaming
    suspend fun gerarExcelViagem(
        @Path("storeId") storeId: String,
        @Path("viagemId") viagemId: String
    ): Response<ResponseBody>
}