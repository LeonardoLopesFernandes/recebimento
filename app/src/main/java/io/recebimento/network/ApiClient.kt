package io.recebimento.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {
    
    companion object {
        private const val BASE_URL = "https://minhaloja-bff.americanas.io/"
        private var instance: ApiClient? = null
        private var apiService: ApiService? = null

        fun getInstance(context: Context): ApiClient {
            if (instance == null) {
                instance = ApiClient(context.applicationContext)
            }
            return instance!!
        }
    }

    private val sessionManager = SessionManager(context)

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = sessionManager.getToken()
        val storeId = sessionManager.getUserStore() ?: "L291"
        
        val requestBuilder = original.newBuilder()
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        requestBuilder.header("Content-Type", "application/json")
        requestBuilder.header("User-Store", "minhaloja/$storeId")
        requestBuilder.header("Platform-Version", "minhaloja/4.0.5")
        requestBuilder.header("Accept", "application/json, text/plain, */*")
        
        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun getApiService(): ApiService {
        if (apiService == null) {
            apiService = retrofit.create(ApiService::class.java)
        }
        return apiService!!
    }
}