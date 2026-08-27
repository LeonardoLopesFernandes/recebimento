package io.recebimento.network

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.recebimento.BuildConfig
import io.recebimento.utils.LogHelper
import java.util.concurrent.TimeUnit

data class TokenInfo(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long = 0
)

object MicrosoftOAuth {

    @Volatile
    var ultimoErro: String? = null
        private set

    const val TENANT = "e316d1ac-42c8-4d30-817c-12c7a71f8ab2"
    const val CLIENT_ID = "16021f31-43f8-4f7a-8af4-5e47efe7db8a"
    const val CLIENT_SECRET = BuildConfig.MICROSOFT_CLIENT_SECRET
    const val SCOPES = "openid profile offline_access"
    const val REDIRECT_URI = "https://apimobile.brasilrisk.com.br/Validar/SamlResponseConsumer"

    private const val AUTHORIZE_URL =
        "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/authorize"
    private const val TOKEN_URL =
        "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getAuthorizeUrl(state: String = "brlog"): String {
        return "$AUTHORIZE_URL?" +
            "client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&response_mode=query" +
            "&state=$state" +
            "&nonce=brlog$state"
    }

    fun isRedirectUrl(url: String): Boolean {
        return url.startsWith(REDIRECT_URI)
    }

    fun extrairCodigo(url: String): String? {
        val match = Regex("[?&]code=([^&]+)").find(url)
        return match?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    fun extrairErro(url: String): String? {
        val match = Regex("[?&]error=([^&]+)").find(url)
        return match?.groupValues?.get(1)
    }

    /**
     * Troca o código de autorização por token (autorization code flow).
     */
    fun trocarCodigoPorToken(accessCode: String): TokenInfo? {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .add("code", accessCode)
            .add("redirect_uri", REDIRECT_URI)
            .add("scope", SCOPES)
            .build()
        return postToken(form)
    }

    /**
     * Renova o token usando o refresh_token (offline_access), sem precisar do WebView.
     */
    fun renovarToken(refreshToken: String): TokenInfo? {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", SCOPES)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        return postToken(form)
    }

    private fun postToken(form: FormBody): TokenInfo? {
        return try {
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(form)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    ultimoErro = extrairDescricaoErro(body) ?: "HTTP ${response.code}"
                    LogHelper.e("OAuth BRLog: token HTTP ${response.code} body=$body")
                    return null
                }
                val json = JSONObject(body)
                val access = json.optString("access_token")
                if (access.isEmpty()) {
                    ultimoErro = extrairDescricaoErro(body) ?: "sem access_token"
                    LogHelper.e("OAuth BRLog: token sem access_token body=$body")
                    return null
                }
                ultimoErro = null
                TokenInfo(
                    accessToken = access,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
                    expiresIn = json.optLong("expires_in")
                )
            }
        } catch (e: Exception) {
            ultimoErro = e.message
            LogHelper.e("OAuth BRLog: exceção na troca de token", e)
            null
        }
    }

    private fun extrairDescricaoErro(body: String): String? {
        return try {
            JSONObject(body).optString("error_description").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}