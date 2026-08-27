package io.recebimento.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("RecebimentoPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SessionManager"
        private const val KEY_BEARER_TOKEN = "BEARER_TOKEN"
        private const val KEY_TOKEN_EXPIRY = "TOKEN_EXPIRY"
        private const val KEY_USER_EMAIL = "USER_EMAIL"
        private const val KEY_USER_NAME = "USER_NAME"
        private const val KEY_USER_STORE = "USER_STORE"
        private const val KEY_REMEMBER_LOGIN = "REMEMBER_LOGIN"
        private const val KEY_MS_PASSWORD = "MS_PASSWORD"
        private const val KEY_BRLOG_REFRESH_TOKEN = "BRLOG_REFRESH_TOKEN"
        private const val KEY_BRLOG_PROGRESS = "BRLOG_PROGRESS"
        private const val KEY_BRLOG_NOTAS = "BRLOG_NOTAS"
        private const val KEY_BRLOG_COD_EMPRESA_USUARIO = "BRLOG_COD_EMPRESA_USUARIO"
        private const val TOKEN_EXPIRY_DAYS = 14
    }

    fun saveBrlogRefreshToken(refreshToken: String) {
        prefs.edit().putString(KEY_BRLOG_REFRESH_TOKEN, refreshToken).apply()
    }

    fun getBrlogRefreshToken(): String? = prefs.getString(KEY_BRLOG_REFRESH_TOKEN, null)

    fun saveBrlogProgress(progresso: Map<String, Double>) {
        try {
            prefs.edit().putString(KEY_BRLOG_PROGRESS, Gson().toJson(progresso)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveBrlogProgress: Erro", e)
        }
    }

    fun getBrlogProgress(): Map<String, Double> {
        return try {
            val json = prefs.getString(KEY_BRLOG_PROGRESS, null) ?: return emptyMap()
            val type = object : TypeToken<Map<String, Double>>() {}.type
            Gson().fromJson<Map<String, Double>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "getBrlogProgress: Erro", e)
            emptyMap()
        }
    }

    fun saveBrlogNotas(notas: List<BrasilRiskNota>) {
        try {
            prefs.edit().putString(KEY_BRLOG_NOTAS, Gson().toJson(notas)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveBrlogNotas: Erro", e)
        }
    }

    fun getBrlogNotas(): List<BrasilRiskNota> {
        return try {
            val json = prefs.getString(KEY_BRLOG_NOTAS, null) ?: return emptyList()
            val type = object : TypeToken<List<BrasilRiskNota>>() {}.type
            Gson().fromJson<List<BrasilRiskNota>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getBrlogNotas: Erro", e)
            emptyList()
        }
    }

    fun saveBrlogCodEmpresaUsuario(codEmpresaUsuario: Int) {
        prefs.edit().putInt(KEY_BRLOG_COD_EMPRESA_USUARIO, codEmpresaUsuario).apply()
    }

    fun getBrlogCodEmpresaUsuario(): Int = prefs.getInt(KEY_BRLOG_COD_EMPRESA_USUARIO, 0)

    fun saveToken(token: String) {
        try {
            Log.d(TAG, "saveToken: Salvando token (${token.take(30)}...)")
            
            val editor = prefs.edit()
            editor.putString(KEY_BEARER_TOKEN, token)
            
            val expiry = System.currentTimeMillis() + (TOKEN_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
            editor.putLong(KEY_TOKEN_EXPIRY, expiry)
            editor.putBoolean(KEY_REMEMBER_LOGIN, true)
            editor.apply()
            
            Log.d(TAG, "saveToken: Token salvo com sucesso!")
        } catch (e: Exception) {
            Log.e(TAG, "saveToken: Erro ao salvar token", e)
        }
    }

    fun getToken(): String? {
        return try {
            val token = prefs.getString(KEY_BEARER_TOKEN, null)
            
            if (token.isNullOrEmpty()) {
                Log.d(TAG, "getToken: Nenhum token encontrado")
                return null
            }
            
            if (isTokenExpired()) {
                Log.e(TAG, "getToken: Token EXPIRADO! Removendo...")
                clearToken()
                return null
            }
            
            Log.d(TAG, "getToken: Token VÁLIDO")
            token
            
        } catch (e: Exception) {
            Log.e(TAG, "getToken: Erro ao obter token", e)
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            val remember = prefs.getBoolean(KEY_REMEMBER_LOGIN, false)
            val token = getToken()
            remember && !token.isNullOrEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "isLoggedIn: Erro", e)
            false
        }
    }

    private fun isTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        if (expiry == 0L) return false
        return System.currentTimeMillis() > expiry
    }

    fun clearToken() {
        try {
            prefs.edit().remove(KEY_BEARER_TOKEN).apply()
            prefs.edit().remove(KEY_TOKEN_EXPIRY).apply()
            prefs.edit().putBoolean(KEY_REMEMBER_LOGIN, false).apply()
        } catch (e: Exception) {
            Log.e(TAG, "clearToken: Erro ao remover token", e)
        }
    }

    fun saveUserInfo(email: String, name: String, store: String) {
        try {
            prefs.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_NAME, name)
                putString(KEY_USER_STORE, store)
            }.apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveUserInfo: Erro ao salvar info", e)
        }
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    
    fun getUserStore(): String? = prefs.getString(KEY_USER_STORE, "L291")

    fun saveCredentials(email: String, password: String) {
        try {
            prefs.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_MS_PASSWORD, password)
            }.apply()
            Log.d(TAG, "saveCredentials: Credenciais salvas")
        } catch (e: Exception) {
            Log.e(TAG, "saveCredentials: Erro", e)
        }
    }

    fun getSavedPassword(): String? {
        return try {
            prefs.getString(KEY_MS_PASSWORD, null)
        } catch (e: Exception) {
            Log.e(TAG, "getSavedPassword: Erro", e)
            null
        }
    }

    fun hasSavedCredentials(): Boolean {
        val email = getUserEmail()
        val senha = getSavedPassword()
        return !email.isNullOrBlank() && !senha.isNullOrBlank()
    }

    fun clearCredentials() {
        try {
            prefs.edit().remove(KEY_MS_PASSWORD).apply()
            Log.d(TAG, "clearCredentials: Credenciais removidas")
        } catch (e: Exception) {
            Log.e(TAG, "clearCredentials: Erro", e)
        }
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "clearAll: Erro ao limpar preferências", e)
        }
    }
}