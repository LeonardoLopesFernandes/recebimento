package io.recebimento.ui

import android.content.Intent
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import io.recebimento.R
import io.recebimento.network.SessionManager
import io.recebimento.utils.LogHelper

class LoginActivity : AppCompatActivity() {

    private var autoLoginDisparado = false
    private var salvarCredenciaisMarcado = false
    private lateinit var imgCheckboxLogin: ImageView

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
