package io.recebimento.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface BrasilRiskService {

    // ========== LOGIN BRLOG (token Microsoft) ==========
    @POST("validar/LoginMobileDeliveryClientMicrosoft")
    fun loginMicrosoft(
        @Body body: TokenBody
    ): Call<BrasilRiskLoginResponse>

    // ========== DETALHE DA NOTA (rastreio BRLog) ==========
    @GET("listar/ObterInformacoesNotaCliente")
    fun obterInformacoesNotaCliente(
        @Query("CodEmpresaUsuario") codEmpresaUsuario: Int?,
        @Query("CodPedido") codPedido: Int?
    ): Call<BrasilRiskNotaDetalhe>

    // ========== AUTORIZAR ABERTURA DO BAÚ (liberação) ==========
    @POST("salvar/AutorizarAberturaBau")
    fun autorizarAberturaBau(
        @Body body: AutorizarAberturaBauRequest
    ): Call<BrasilRiskBauResponse>
}

object BrasilRiskClient {

    private const val BASE_URL = "https://apimobile.brasilrisk.com.br/"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun getService(): BrasilRiskService = retrofit.create(BrasilRiskService::class.java)
}