package io.recebimento.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.recebimento.R
import io.recebimento.network.ApiService
import io.recebimento.network.SessionManager
import io.recebimento.utils.LogHelper
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private var autoLoginDisparado = false
    private var salvarCredenciaisMarcado = false
    private lateinit var imgCheckboxLogin: ImageView
    private var aguardandoRetornoNavegador = false
    private var validandoToken = false
    private var clipboardHash = ""
    private var btnEntrarNavegador: Button? = null

    override fun onResume() {
        super.onResume()
        if (aguardandoRetornoNavegador) {
            checarClipboardParaToken()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val sessionManager = SessionManager(this)

        // Se o usuário já tiver logado antes, pula direto para a tela principal
        if (sessionManager.isLoggedIn()) {
            val token = sessionManager.getToken()
            if (!token.isNullOrEmpty()) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            }
        }

        val logoGrande = findViewById<ImageView>(R.id.logoGrande)
        val tvTitulo = findViewById<TextView>(R.id.tvTitulo)
        val tvDescricao = findViewById<TextView>(R.id.tvDescricao)
        val containerLogin = findViewById<LinearLayout>(R.id.containerLogin)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etSenha = findViewById<EditText>(R.id.etSenha)
        val btnSalvarCredenciais = findViewById<View>(R.id.btnSalvarCredenciais)
        imgCheckboxLogin = findViewById(R.id.imgCheckboxLogin)

        etEmail.setText(sessionManager.getUserEmail() ?: "")
        etSenha.setText(sessionManager.getSavedPassword() ?: "")
        configurarOlhoSenha(etSenha)

        salvarCredenciaisMarcado = sessionManager.hasSavedCredentials()
        imgCheckboxLogin.setImageResource(
            if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
        )

        // Splash: logo gigante no centro da tela deslizando para o lugar
        logoGrande.post {
            val loc = IntArray(2)
            logoGrande.getLocationOnScreen(loc)
            val finalCenterY = loc[1] + logoGrande.height / 2f
            val screenCenterY = resources.displayMetrics.heightPixels / 2f
            logoGrande.translationY = (screenCenterY - finalCenterY).coerceAtLeast(0f)
            logoGrande.scaleX = 2.0f
            logoGrande.scaleY = 2.0f

            Handler(Looper.getMainLooper()).postDelayed({
                logoGrande.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationY(0f)
                    .setDuration(450)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {

                        // Título com Fade-In
                        tvTitulo.visibility = View.VISIBLE
                        tvTitulo.alpha = 0f
                        tvTitulo.animate()
                            .alpha(1f)
                            .setDuration(250)
                            .start()

                        tvDescricao.visibility = View.VISIBLE
                        tvDescricao.alpha = 0f
                        tvDescricao.animate()
                            .alpha(0.90f)
                            .setDuration(250)
                            .start()

                        // Card de login surge de baixo para cima
                        containerLogin.translationY = 50f
                        containerLogin.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(300)
                            .setInterpolator(OvershootInterpolator(0.8f))
                            .start()

                        // Checkbox surge junto com o card
                        btnSalvarCredenciais.translationY = 50f
                        btnSalvarCredenciais.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(300)
                            .setInterpolator(OvershootInterpolator(0.8f))
                            .start()
                    }
                    .start()
            }, 400)
        }

        // Botão ENTRAR: login com as credenciais digitadas
        btnEntrar.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val senha = etSenha.text.toString()
            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha email e senha para entrar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (salvarCredenciaisMarcado && !sessionManager.hasSavedCredentials()) {
                sessionManager.saveCredentials(email, senha)
            }
            autoLoginDisparado = true
            val intent = Intent(this, LoginWebViewActivity::class.java).apply {
                putExtra(LoginWebViewActivity.EXTRA_AUTO_LOGIN, true)
                putExtra(LoginWebViewActivity.EXTRA_EMAIL, email)
                putExtra(LoginWebViewActivity.EXTRA_SENHA, senha)
            }
            startActivity(intent)
        }

        btnSalvarCredenciais.setOnClickListener {
            salvarCredenciaisMarcado = !salvarCredenciaisMarcado
            animarCheckbox()
            if (!salvarCredenciaisMarcado) {
                Toast.makeText(this, "Login automático desativado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val email = etEmail.text.toString().trim()
            val senha = etSenha.text.toString()
            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha email e senha para salvar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sessionManager.saveCredentials(email, senha)
            Toast.makeText(this, "Credenciais salvas! Iniciando login automático...", Toast.LENGTH_SHORT).show()
            autoLoginDisparado = true
            startActivity(Intent(this, LoginWebViewActivity::class.java).apply {
                putExtra(LoginWebViewActivity.EXTRA_AUTO_LOGIN, true)
            })
        }

        // Botão ENTRAR VIA NAVEGADOR: abre o authorize no navegador externo e
        // captura o token da área de transferência ao retornar (fluxo minha-loja)
        btnEntrarNavegador = findViewById(R.id.btnEntrarNavegador)
        btnEntrarNavegador?.setOnClickListener {
            if (aguardandoRetornoNavegador) {
                checarClipboardParaToken()
                return@setOnClickListener
            }
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardHash = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                aguardandoRetornoNavegador = true
                atualizarBotaoNavegador()
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://sl-authorization.americanas.io/minha-loja")
                )
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Faça login no navegador, copie o token e volte ao app.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                aguardandoRetornoNavegador = false
                atualizarBotaoNavegador()
                Toast.makeText(this, "Não foi possível abrir o navegador", Toast.LENGTH_SHORT).show()
            }
        }

        if (sessionManager.hasSavedCredentials()) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !autoLoginDisparado && !sessionManager.isLoggedIn()) {
                    autoLoginDisparado = true
                    startActivity(Intent(this, LoginWebViewActivity::class.java).apply {
                        putExtra(LoginWebViewActivity.EXTRA_AUTO_LOGIN, true)
                    })
                }
            }, 2500)
        }
    }

    private fun atualizarBotaoNavegador() {
        btnEntrarNavegador?.text = if (aguardandoRetornoNavegador) {
            "JÁ COPIEI O TOKEN, TENTAR NOVAMENTE"
        } else {
            "ENTRAR VIA NAVEGADOR"
        }
    }

    private fun checarClipboardParaToken() {
        if (validandoToken) return
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val texto = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (texto.isEmpty() || texto == clipboardHash) return

            var token = texto.trim()
            if (token.startsWith("bearer ", ignoreCase = true)) {
                token = token.substring(7).trim()
            }
            val cookieIdx = token.indexOf("newToken=")
            if (cookieIdx >= 0) {
                var value = token.substring(cookieIdx + "newToken=".length)
                value = value.split(';', '&').firstOrNull() ?: value
                token = value.trim()
            }
            if (token.contains("token=")) {
                val qi = token.indexOf("token=")
                var value = token.substring(qi + "token=".length)
                value = value.split('&', ' ', '\n', '\r', '\t').firstOrNull() ?: value
                token = Uri.decode(value.trim()).trim()
            }

            if (token.length < 50) {
                Toast.makeText(
                    this,
                    "Nenhum token encontrado. Copie o token no navegador e volte ao app.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            if (token == clipboardHash) return
            clipboardHash = token

            validarToken(token)
        } catch (e: Exception) {
            LogHelper.e("BrowserLogin: erro ao ler clipboard", e)
        }
    }

    private fun validarToken(token: String) {
        if (validandoToken) return
        validandoToken = true
        Toast.makeText(this, "Token detectado! Validando...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val sessionManager = SessionManager(applicationContext)
                val store = sessionManager.getUserStore() ?: "L291"
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://minhaloja-bff.americanas.io/")
                    .client(client.newBuilder().addInterceptor(Interceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Authorization", "Bearer $token")
                                .header("User-Store", "minhaloja/$store")
                                .build()
                        )
                    }).build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val resp = retrofit.create(ApiService::class.java)
                    .getRecebimentos(store, "pendente")
                if (resp.isSuccessful) {
                    val (email, nome, loja) = extrairInfoToken(token, store)
                    sessionManager.saveToken(token)
                    sessionManager.saveUserInfo(email, nome, loja)
                    aguardandoRetornoNavegador = false
                    validandoToken = false
                    Toast.makeText(applicationContext, "Login realizado com sucesso", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    validandoToken = false
                    runOnUiThread {
                        atualizarBotaoNavegador()
                        Toast.makeText(
                            this@LoginActivity,
                            "Token detectado mas rejeitado (HTTP ${resp.code()}). Tente copiar o token novamente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("BrowserLogin: erro ao validar token", e)
                validandoToken = false
                runOnUiThread {
                    atualizarBotaoNavegador()
                    Toast.makeText(
                        this@LoginActivity,
                        "Token detectado mas não foi possível validar.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun extrairInfoToken(token: String, storeAtual: String): Triple<String, String, String> {
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                var p = parts[1]
                while (p.length % 4 != 0) p += "="
                val decoded = String(
                    Base64.decode(p.replace("-", "+").replace("_", "/"), Base64.DEFAULT),
                    Charsets.UTF_8
                )
                val json = JSONObject(decoded)
                val user = json.optJSONObject("user")
                var email = user?.optString("email")
                    ?: json.optString("email", json.optString("preferred_username", ""))
                var nome = user?.optString("nome")
                    ?: user?.optString("name")
                    ?: json.optString("name", json.optString("given_name", ""))
                var loja = user?.optString("loja")
                    ?: json.optString("loja", "")
                if (loja.isNullOrEmpty()) {
                    val stores = user?.optJSONArray("stores") ?: json.optJSONArray("stores")
                    loja = if (stores != null && stores.length() > 0) stores.optString(0) else storeAtual
                }
                if (email.isNullOrEmpty()) email = "usuario@americanas.io"
                if (nome.isNullOrEmpty()) {
                    nome = email.split("@").first()
                        .replace(".", " ").replace("_", " ")
                        .split(" ").joinToString(" ") {
                            if (it.isNotEmpty()) it[0].uppercase() + it.substring(1) else it
                        }
                }
                if (loja.isNullOrEmpty()) loja = storeAtual
                return Triple(email, nome, loja)
            }
        } catch (e: Exception) {
            LogHelper.e("BrowserLogin: erro ao decodificar JWT", e)
        }
        return Triple("usuario@americanas.io", "Usuário", storeAtual)
    }

    private fun animarCheckbox() {
        try {
            val de = if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_unchecked else R.drawable.ic_checkbox_checked
            val para = if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
            val transition = TransitionDrawable(
                arrayOf(
                    ContextCompat.getDrawable(this, de),
                    ContextCompat.getDrawable(this, para)
                )
            )
            transition.isCrossFadeEnabled = true
            imgCheckboxLogin.setImageDrawable(transition)
            transition.startTransition(250)
        } catch (e: Exception) {
            imgCheckboxLogin.setImageResource(
                if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
            )
        }
    }

    private fun configurarOlhoSenha(et: EditText) {
        var visivel = false
        fun atualizarIcone() {
            et.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0,
                if (visivel) R.drawable.ic_eye_hide else R.drawable.ic_eye_show,
                0
            )
        }
        atualizarIcone()
        et.setOnTouchListener { _, event ->
            val drawables = et.getCompoundDrawablesRelative()
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP && drawables[2] != null) {
                val icone = drawables[2]
                val dentroIcone = event.x >= et.width - et.totalPaddingRight - icone.intrinsicWidth - 8
                if (dentroIcone) {
                    visivel = !visivel
                    val selection = et.selectionEnd.coerceAtLeast(0)
                    et.inputType = if (visivel) {
                        android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    et.setSelection(selection)
                    atualizarIcone()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}
