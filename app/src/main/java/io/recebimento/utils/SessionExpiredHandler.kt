package io.recebimento.utils

import android.app.Activity
import android.content.Intent
import io.recebimento.network.SessionManager
import io.recebimento.ui.LoginActivity

object SessionExpiredHandler {
    
    fun handleSessionExpired(activity: Activity) {
        try {
            LogHelper.d("SessionExpiredHandler: Redirecionando para login...")
            
            val sessionManager = SessionManager(activity)
            sessionManager.clearToken()
            
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activity.startActivity(intent)
            activity.finish()
            
        } catch (e: Exception) {
            LogHelper.e("SessionExpiredHandler: Erro ao redirecionar", e)
            try {
                activity.finish()
            } catch (ex: Exception) {
                LogHelper.e("SessionExpiredHandler: Erro no fallback", ex)
            }
        }
    }
}