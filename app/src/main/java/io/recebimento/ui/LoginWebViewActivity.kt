package io.recebimento.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.recebimento.R
import io.recebimento.network.SessionManager
import io.recebimento.utils.LogHelper

class LoginWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutCarregamentoCustom: LinearLayout
    private lateinit var imgLogoPreenchimento: ImageView
    
    private var tokenEncontrado = false
    private var loginConcluido = false
    private var pulsoAnimator: ValueAnimator? = null
    private var autoLogin = false
    private var emailCredencial = ""
    private var senhaCredencial = ""
    private var ultimaInjecao = 0L
    private var tentativasMesmaPagina = 0
    private var ultimaUrlPreenchida = ""
    private var oauthBRLogEmAndamento = false
    private var oauthBRLogIniciado = false
    private var oauthPaginaVisivel = false
    private var oauthConsentimentoClicado = false
    private var tentativasPreenchimentoBRLog = 0
    private var oauthSomente = false
    private var brlogSyncOk = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_webview)

        autoLogin = intent.getBooleanExtra(EXTRA_AUTO_LOGIN, false)
        oauthSomente = intent.getBooleanExtra(EXTRA_OAUTH_ONLY, false)
        if (autoLogin) {
            val sessionManager = SessionManager(applicationContext)
            emailCredencial = intent.getStringExtra(EXTRA_EMAIL) ?: sessionManager.getUserEmail() ?: ""
            senhaCredencial = intent.getStringExtra(EXTRA_SENHA) ?: sessionManager.getSavedPassword() ?: ""
            if (emailCredencial.isBlank() || senhaCredencial.isBlank()) autoLogin = false
        }

        webView = findViewById(R.id.webView)
        layoutCarregamentoCustom = findViewById(R.id.layoutCarregamentoCustom)
        imgLogoPreenchimento = findViewById(R.id.imgLogoPreenchimento)

        configurarWebView()
        if (oauthSomente) {
            iniciarOAuthBRLog()
        } else {
            carregarLogin()
        }
    }

    private fun iniciarAnimacaoPulse() {
        if (pulsoAnimator != null && pulsoAnimator!!.isRunning) return

        runOnUiThread {
            pulsoAnimator = ValueAnimator.ofFloat(1.0f, 0.2f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val valorAlpha = animator.animatedValue as Float
                    imgLogoPreenchimento.alpha = valorAlpha
                }
                start()
            }
        }
    }

    private fun pararAnimacaoPulse() {
        runOnUiThread {
            pulsoAnimator?.cancel()
            pulsoAnimator = null
            imgLogoPreenchimento.alpha = 1.0f
        }
    }

    private fun exibirCarregamento(exibir: Boolean) {
        if (oauthPaginaVisivel) return
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (loginConcluido || tokenEncontrado) {
                layoutCarregamentoCustom.visibility = View.VISIBLE
                iniciarAnimacaoPulse()
                return@runOnUiThread
            }
            
            if (exibir) {
                layoutCarregamentoCustom.visibility = View.VISIBLE
                iniciarAnimacaoPulse()
            } else {
                layoutCarregamentoCustom.visibility = View.GONE
                pararAnimacaoPulse()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    exibirCarregamento(true)

                    if (url != null && !loginConcluido) {
                        verificarCookies()
                        verificarTokenNaUrl(url)
                    }

                    if (oauthBRLogEmAndamento && url != null &&
                        io.recebimento.network.MicrosoftOAuth.isRedirectUrl(url)) {
                        tratarOAuthBRLog(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    if (!loginConcluido && !tokenEncontrado) {
                        exibirCarregamento(false)
                        verificarCookies()
                        
                        if (url != null && url.startsWith("https://minhaloja.americanas.io")) {
                            verificarTokenViaJavaScript()
                        }
                        preencherLoginAutomatico()
                    }

                    // BRLog OAuth: se cair numa tela de login ou consentimento da Microsoft,
                    // revela o WebView e tenta resolver (auto-preenchimento / aceitar)
                    if (oauthBRLogEmAndamento) {
                        val urlAtual = url ?: ""
                        if (urlAtual.startsWith("https://login.microsoftonline.com") &&
                            !io.recebimento.network.MicrosoftOAuth.isRedirectUrl(urlAtual)) {
                            webView.evaluateJavascript(
                                "(function(){if(document.getElementById('i0116'))return 'login';" +
                                "var b=document.querySelector('input[type=submit]');" +
                                "if(b){var v=(b.value||b.getAttribute('value')||'').toLowerCase();" +
                                "if(v.indexOf('accept')>=0||v.indexOf('aceitar')>=0||v.indexOf('concordar')>=0||v.indexOf('permitir')>=0)return 'consent';}" +
                                "return 'none';})()"
                            ) { resultado ->
                                val tipo = resultado?.trim()?.trim('"') ?: "none"
                                when (tipo) {
                                    "login" -> {
                                        if (!oauthPaginaVisivel) {
                                            oauthPaginaVisivel = true
                                            layoutCarregamentoCustom.visibility = View.GONE
                                            pararAnimacaoPulse()
                                            Toast.makeText(
                                                applicationContext,
                                                "BRLog: confirme o login para sincronizar as viagens",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        preencherLoginBRLog()
                                    }
                                    "consent" -> {
                                        if (!oauthConsentimentoClicado) {
                                            oauthConsentimentoClicado = true
                                            webView.evaluateJavascript(
                                                "(function(){var b=document.querySelector('input[type=submit]');" +
                                                "if(b){b.click();return 'ok';}return 'no';})()"
                                            ) { }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (!loginConcluido) {
                        verificarCookies()
                        verificarTokenNaUrl(url)
                    }

                    if (oauthBRLogEmAndamento && io.recebimento.network.MicrosoftOAuth.isRedirectUrl(url)) {
                        tratarOAuthBRLog(url)
                        return true
                    }
                    return loginConcluido
                }
            }
        } catch (e: Exception) {
            LogHelper.e("WebView: Erro config", e)
        }
    }

    private fun verificarTokenNaUrl(url: String) {
        if (loginConcluido || tokenEncontrado) return
        val patterns = listOf(Regex("[?&]newToken=([^&]+)"), Regex("[?&]token=([^&]+)"))
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                val token = match.groupValues[1]
                if (token.isNotEmpty() && token.length > 50) {
                    salvarToken(token)
                    return
                }
            }
        }
    }

    private fun verificarCookies() {
        if (loginConcluido || tokenEncontrado) return
        try {
            val cookies = CookieManager.getInstance().getCookie("https://minhaloja.americanas.io")
            if (cookies != null) {
                val cookieList = cookies.split(";")
                for (cookie in cookieList) {
                    val cookieTrim = cookie.trim()
                    if (cookieTrim.startsWith("newToken=") || cookieTrim.startsWith("token=")) {
                        val token = cookieTrim.substring(cookieTrim.indexOf("=") + 1)
                        if (token.isNotEmpty() && token.length > 50) {
                            salvarToken(token)
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.e("WebView: Erro cookies", e)
        }
    }

    private fun verificarTokenViaJavaScript() {
        if (tokenEncontrado || loginConcluido) return
        val script = "(function() { return localStorage.getItem('newToken') || localStorage.getItem('token') || sessionStorage.getItem('newToken') || sessionStorage.getItem('token') || window.newToken || window.token; })();"
        
        webView.evaluateJavascript(script) { result ->
            if (result != "null" && result.length > 10 && !tokenEncontrado && !loginConcluido) {
                var token = result.trim().replace("\"", "")
                if (token.length > 50) {
                    salvarToken(token)
                }
            }
        }
    }

    private fun salvarToken(token: String) {
        if (loginConcluido) return
        loginConcluido = true
        tokenEncontrado = true

        exibirCarregamento(true)

        try {
            val sessionManager = SessionManager(applicationContext)
            sessionManager.saveToken(token)
            sessionManager.saveUserInfo("usuario@americanas.io", "Usuário", "L291")
            iniciarOAuthBRLog()
        } catch (e: Exception) {
            loginConcluido = false
            tokenEncontrado = false
            exibirCarregamento(false)
        }
    }

    /**
     * Roda o OAuth2 do BRLog silenciosamente no mesmo WebView (a sessão da
     * Microsoft já existe após o login do minhaloja). Ao capturar o code,
     * troca por token e sincroniza o progresso das viagens.
     */
    private fun iniciarOAuthBRLog() {
        if (oauthBRLogEmAndamento || oauthBRLogIniciado) return
        oauthBRLogEmAndamento = true
        oauthBRLogIniciado = true
        LogHelper.d("OAuth BRLog: iniciando fluxo silencioso")
        Toast.makeText(applicationContext, "Sincronizando viagens (BRLog)...", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            if (oauthBRLogEmAndamento && !isFinishing && !isDestroyed) {
                LogHelper.e("OAuth BRLog: timeout, seguindo sem sincronizar")
                oauthBRLogEmAndamento = false
                finalizarLogin()
            }
        }, 90000)
        webView.loadUrl(io.recebimento.network.MicrosoftOAuth.getAuthorizeUrl())
    }

    private fun tratarOAuthBRLog(url: String) {
        if (oauthBRLogEmAndamento) {
            val erro = io.recebimento.network.MicrosoftOAuth.extrairErro(url)
            val codigo = io.recebimento.network.MicrosoftOAuth.extrairCodigo(url)
            if (erro != null || codigo != null) {
                oauthBRLogEmAndamento = false
                if (codigo != null) {
                    LogHelper.d("OAuth BRLog: code capturado, trocando por token")
                    trocarCodigoBRLog(codigo)
                } else {
                    LogHelper.e("OAuth BRLog: erro AAD ($erro)")
                    finalizarLogin()
                }
            }
        }
    }

    private fun trocarCodigoBRLog(codigo: String) {
        val sessionManager = SessionManager(applicationContext)
        Thread {
            val token = io.recebimento.network.MicrosoftOAuth.trocarCodigoPorToken(codigo)
            if (token == null) {
                val motivo = io.recebimento.network.MicrosoftOAuth.ultimoErro ?: "sem resposta"
                LogHelper.e("OAuth BRLog: falha na troca de token ($motivo)")
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "BRLog: falha ao obter token ($motivo)",
                        Toast.LENGTH_LONG
                    ).show()
                    finalizarLogin()
                }
                return@Thread
            }
            token.refreshToken?.let { sessionManager.saveBrlogRefreshToken(it) }
            brlogSyncOk = true
            val progresso = sincronizarProgressoBRLog(token.accessToken, sessionManager)
            runOnUiThread {
                if (progresso.isNotEmpty()) {
                    LogHelper.d("OAuth BRLog: sincronizado ${progresso.size} viagens")
                    Toast.makeText(
                        applicationContext,
                        "BRLog sincronizado: ${progresso.size} viagens",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    LogHelper.e("OAuth BRLog: nenhuma viagem retornada")
                    Toast.makeText(
                        applicationContext,
                        "BRLog: nenhuma viagem em andamento",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finalizarLogin()
            }
        }.start()
    }

    private fun sincronizarProgressoBRLog(
        accessToken: String,
        sessionManager: SessionManager
    ): Map<String, Double> {
        return try {
            val api = io.recebimento.network.BrasilRiskClient.getService()
            val resposta = api.loginMicrosoft(io.recebimento.network.TokenBody(accessToken)).execute()
            if (resposta.isSuccessful) {
                val body = resposta.body()
                val lista = body?.notaFiscal ?: return emptyMap()
                val progresso = lista.mapNotNull { nota ->
                    val numero = nota.numeroViagem ?: return@mapNotNull null
                    val p = nota.progressoViagem ?: return@mapNotNull null
                    numero to normalizarProgresso(p)
                }.toMap()
                if (progresso.isNotEmpty()) {
                    sessionManager.saveBrlogProgress(progresso)
                }
                body.codEmpresaUsuario?.let { sessionManager.saveBrlogCodEmpresaUsuario(it) }
                if (lista.isNotEmpty()) {
                    sessionManager.saveBrlogNotas(lista)
                }
                progresso
            } else {
                LogHelper.e("BRLog login HTTP ${resposta.code()}")
                emptyMap()
            }
        } catch (e: Exception) {
            LogHelper.e("BRLog login erro", e)
            emptyMap()
        }
    }

    private fun normalizarProgresso(p: Double): Double {
        var valor = p
        if (valor > 1.0) valor /= 100.0
        return valor.coerceIn(0.0, 1.0)
    }

    private fun preencherLoginBRLog() {
        if (!oauthBRLogEmAndamento || isFinishing || isDestroyed) return
        if (oauthPaginaVisivel && System.currentTimeMillis() - ultimaInjecao < 1500) return
        val urlAtual = webView.url ?: return
        if (!urlAtual.contains("login.microsoftonline.com")) return

        val sessionManager = SessionManager(applicationContext)
        val email = sessionManager.getUserEmail() ?: ""
        val senha = sessionManager.getSavedPassword() ?: ""
        if (email.isBlank() || senha.isBlank()) return

        ultimaInjecao = System.currentTimeMillis()
        webView.evaluateJavascript(montarScriptPreenchimento(email, senha)) { resultado ->
            val r = resultado?.trim()?.trim('"') ?: ""
            if (r == "no-field" || r == "email-no-btn" || r == "pwd-no-btn" || r == "kmsi-no-btn") {
                if (tentativasPreenchimentoBRLog++ < 12) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isFinishing && !isDestroyed) preencherLoginBRLog()
                    }, 1500)
                }
            }
        }
    }

    private fun finalizarLogin() {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (oauthSomente) {
                if (brlogSyncOk) {
                    Toast.makeText(applicationContext, "Sincronização BRLog concluída", Toast.LENGTH_SHORT).show()
                }
                finish()
                return@runOnUiThread
            }
            Toast.makeText(applicationContext, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun preencherLoginAutomatico() {
        if (!autoLogin || loginConcluido || tokenEncontrado || isFinishing || isDestroyed) return
        val urlAtual = webView.url ?: return
        if (!urlAtual.contains("login.microsoftonline.com")) return
        if (System.currentTimeMillis() - ultimaInjecao < 1200) return

        if (urlAtual == ultimaUrlPreenchida) {
            tentativasMesmaPagina++
        } else {
            tentativasMesmaPagina = 0
            ultimaUrlPreenchida = urlAtual
        }
        if (tentativasMesmaPagina >= 8) {
            autoLogin = false
            Toast.makeText(applicationContext, "Login automático falhou. Preencha manualmente.", Toast.LENGTH_LONG).show()
            return
        }

        ultimaInjecao = System.currentTimeMillis()
        val script = montarScriptPreenchimento(emailCredencial, senhaCredencial)
        webView.evaluateJavascript(script) { resultado ->
            val r = resultado?.trim()?.trim('"') ?: ""
            if (r == "no-field" || r == "email-no-btn" || r == "pwd-no-btn" || r == "kmsi-no-btn") {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) preencherLoginAutomatico()
                }, 1000)
            }
        }
    }

    private fun montarScriptPreenchimento(email: String, senha: String): String {
        val e = jsString(email)
        val s = jsString(senha)
        return "(function(){" +
            "var setV=function(el,v){var d=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');" +
            "if(d&&d.set){d.set.call(el,v);}else{el.value=v;}" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "el.dispatchEvent(new Event('change',{bubbles:true}));};" +
            "var btn=document.getElementById('idSIButton9');" +
            "var em=document.getElementById('i0116');" +
            "if(em){setV(em,$e);if(btn){btn.click();return 'email-clicked';}return 'email-no-btn';}" +
            "var pw=document.getElementById('i0118');" +
            "if(pw){setV(pw,$s);if(btn){btn.click();return 'pwd-clicked';}return 'pwd-no-btn';}" +
            "var km=document.getElementById('KmsiCheckboxField');" +
            "if(km){if(btn){btn.click();return 'kmsi-clicked';}return 'kmsi-no-btn';}" +
            "return 'no-field';" +
            "})()"
    }

    private fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""

    private fun carregarLogin() {
        val loginUrl = "https://login.microsoftonline.com/e316d1ac-42c8-4d30-817c-12c7a71f8ab2/saml2?SAMLRequest=nVPLjhoxEPyVke%2Beh4fdYS1gRUBRkDYJApJDLlGPp2dx4gdxezYkXx8xQMIhy4Gru1RVXdUePe6tSV4wkPZuzIo0Z4%2BTEYE1Oznt4tat8EeHFJO9NY5kPxizLjjpgTRJBxZJRiXX0%2FdPUqS53AUfvfKGJYv5mH29K%2B%2BwflDQVrlohVICy5Yln8%2BCIs1ZsiDqcOEogotjJnJxz%2FN7LgYbUcqikqJMi7z4wpLlifqNdo12z9d91EcQyXebzZIvP643LJkjRe0g9tLbGHcks8z4Z%2B1Sq1Xw5NvondEOU%2BVthmVx3xSg%2BECoIR80Zc6HRaV4IVQFVdEOoRbZIRLBkikRhgPxzDvqLIY1hhet8NPq6Z8UGQ5d3Pqgf%2FcmUrAYtAIHlGqfWe22wI3%2FBpkCY2pQ39mxDNlHFC5auL48nN2wCcKwaaui4YhNyQfDhwEHqEtegWhzUau6yttRdiFyrv8DWFzMl95o9euW%2Bt%2F6YCG%2Bji7Son%2FRDW97qEQL2kybJiARS6bG%2BJ%2BzgBBxzGLokGVna6ejxKY%2F0Zl3Efc3nejM2x0ETYd7wD2oeM77knhmgGiF7S3pX4UpqQ7USLJztEOlW43NqYv%2FGZgcZ6%2Fs%2F3d6%2BW8nfwA%3D&sso_reload=true"
        webView.loadUrl(loginUrl)
    }

    override fun onBackPressed() {
        if (webView.canGoBack() && !loginConcluido) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        pararAnimacaoPulse()
        try { webView.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_LOGIN = "extra_auto_login"
        const val EXTRA_OAUTH_ONLY = "extra_oauth_only"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_SENHA = "extra_senha"
    }
}