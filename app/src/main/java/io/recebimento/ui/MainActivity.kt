package io.recebimento.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.recebimento.R
import io.recebimento.adapters.RecebimentoAdapter
import io.recebimento.network.ApiClient
import io.recebimento.network.Recebimento
import io.recebimento.network.ProtocoloRequest
import io.recebimento.network.SessionManager
import io.recebimento.utils.Constants
import io.recebimento.utils.ExcelDownloader
import io.recebimento.utils.LogHelper
import io.recebimento.utils.SessionExpiredHandler
import io.recebimento.viewmodel.MainEvent
import io.recebimento.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: io.recebimento.network.ApiService
    private lateinit var adapter: RecebimentoAdapter

    // Views
    private lateinit var cvHeader: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnMenu: View
    private lateinit var btnLogout: ImageButton
    private lateinit var btnViagensReceber: LinearLayout
    private lateinit var btnViagensAnomalia: LinearLayout
    private lateinit var btnViagensErro: LinearLayout
    private lateinit var btnViagensRecebidas: LinearLayout
    private lateinit var tvCountReceber: TextView
    private lateinit var tvCountAnomalia: TextView
    private lateinit var tvCountErro: TextView
    private lateinit var tvCountRecebidas: TextView
    private lateinit var tvLabelReceber: TextView
    private lateinit var tvLabelAnomalia: TextView
    private lateinit var tvLabelErro: TextView
    private lateinit var tvLabelRecebidas: TextView
    private lateinit var layoutBusca: LinearLayout
    private lateinit var etBuscarViagem: AppCompatEditText
    private lateinit var btnLimparBuscaViagem: ImageButton
    private lateinit var layoutFiltros: LinearLayout
    private lateinit var btnOrdenarRecente: Button
    private lateinit var btnOrdenarAntigo: Button
    private lateinit var layoutViewMode: LinearLayout
    private lateinit var btnModoLista: ImageView
    private lateinit var btnModoGrid: ImageView
    private var isModoGrid = false
    private lateinit var rvRecebimentos: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmptySubtitle: TextView

    // Badge icons & labels
    private lateinit var ivIconReceber: ImageView
    private lateinit var ivIconAnomalia: ImageView
    private lateinit var ivIconErro: ImageView
    private lateinit var ivIconRecebidas: ImageView

    // Badge lines
    private lateinit var viewLineReceber: View
    private lateinit var viewLineAnomalia: View
    private lateinit var viewLineErro: View
    private lateinit var viewLineRecebidas: View

    // Counter values for selected state
    private var qtdReceber = "0"
    private var qtdAnomalia = "0"
    private var qtdErro = "0"
    private var qtdRecebidas = "0"

    private val botoes = mutableListOf<LinearLayout>()

    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }

        apiService = ApiClient.getInstance(this).getApiService()

        initViews()
        setupListeners()
        setupRecyclerView()
        observeState()
        observeEvents()

        selecionarBotao(btnViagensReceber)

        btnOrdenarRecente.backgroundTintList = null
        btnOrdenarAntigo.backgroundTintList = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.recarregarProgressoBRLog()
    }

    private fun initViews() {
        cvHeader = findViewById(R.id.cvHeader)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        btnMenu = findViewById(R.id.btnMenu)
        btnLogout = findViewById(R.id.btnLogout)

        btnViagensReceber = findViewById(R.id.btnViagensReceber)
        btnViagensAnomalia = findViewById(R.id.btnViagensAnomalia)
        btnViagensErro = findViewById(R.id.btnViagensErro)
        btnViagensRecebidas = findViewById(R.id.btnViagensRecebidas)

        tvCountReceber = findViewById(R.id.tvCountReceber)
        tvCountAnomalia = findViewById(R.id.tvCountAnomalia)
        tvCountErro = findViewById(R.id.tvCountErro)
        tvCountRecebidas = findViewById(R.id.tvCountRecebidas)

        tvLabelReceber = findViewById(R.id.tvLabelReceber)
        tvLabelAnomalia = findViewById(R.id.tvLabelAnomalia)
        tvLabelErro = findViewById(R.id.tvLabelErro)
        tvLabelRecebidas = findViewById(R.id.tvLabelRecebidas)

        layoutBusca = findViewById(R.id.layoutBusca)
        etBuscarViagem = findViewById(R.id.etBuscarViagem)
        btnLimparBuscaViagem = findViewById(R.id.btnLimparBuscaViagem)

        layoutFiltros = findViewById(R.id.layoutFiltros)
        btnOrdenarRecente = findViewById(R.id.btnOrdenarRecente)
        btnOrdenarAntigo = findViewById(R.id.btnOrdenarAntigo)

        layoutViewMode = findViewById(R.id.layoutViewMode)
        btnModoLista = findViewById(R.id.btnModoLista)
        btnModoGrid = findViewById(R.id.btnModoGrid)

        rvRecebimentos = findViewById(R.id.rvRecebimentos)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle)

        ivIconReceber = findViewById(R.id.ivIconReceber)
        ivIconAnomalia = findViewById(R.id.ivIconAnomalia)
        ivIconErro = findViewById(R.id.ivIconErro)
        ivIconRecebidas = findViewById(R.id.ivIconRecebidas)

        viewLineReceber = findViewById(R.id.viewLineReceber)
        viewLineAnomalia = findViewById(R.id.viewLineAnomalia)
        viewLineErro = findViewById(R.id.viewLineErro)
        viewLineRecebidas = findViewById(R.id.viewLineRecebidas)

        botoes.addAll(listOf(btnViagensReceber, btnViagensAnomalia, btnViagensErro, btnViagensRecebidas))

        tvTitle.text = "Recebimento Centralizado"
    }

    private fun setupListeners() {
        btnViagensReceber.setOnClickListener {
            selecionarTab(Constants.STATUS_PENDENTE, btnViagensReceber)
        }
        btnViagensAnomalia.setOnClickListener {
            selecionarTab(Constants.STATUS_ANOMALIA, btnViagensAnomalia)
        }
        btnViagensErro.setOnClickListener {
            selecionarTab(Constants.STATUS_ERRO, btnViagensErro)
        }
        btnViagensRecebidas.setOnClickListener {
            selecionarTab(Constants.STATUS_RECEBIDO, btnViagensRecebidas)
        }

        btnMenu.setOnClickListener {
            abrirMenu()
        }

        etBuscarViagem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearch(s.toString())
            }
        })

        btnLimparBuscaViagem.setOnClickListener {
            etBuscarViagem.text?.clear()
            viewModel.setSearch("")
        }

        btnOrdenarRecente.setOnClickListener {
            viewModel.setSort("desc")
            btnOrdenarRecente.setBackgroundResource(R.drawable.bg_pill_red_v2)
            btnOrdenarAntigo.setBackgroundResource(R.drawable.bg_pill_inactive_v2)
        }

        btnOrdenarAntigo.setOnClickListener {
            viewModel.setSort("asc")
            btnOrdenarAntigo.setBackgroundResource(R.drawable.bg_pill_red_v2)
            btnOrdenarRecente.setBackgroundResource(R.drawable.bg_pill_inactive_v2)
        }

        btnLogout.setOnClickListener {
            viewModel.clearSession()
            redirectToLogin()
        }

        btnModoLista.setOnClickListener {
            setModoVisualizacao(false)
        }

        btnModoGrid.setOnClickListener {
            setModoVisualizacao(true)
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun abrirMenu() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.menu_bottom_sheet)

        dialog.findViewById<LinearLayout>(R.id.menuSincronizarBRLog)?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, LoginWebViewActivity::class.java)
            intent.putExtra(LoginWebViewActivity.EXTRA_OAUTH_ONLY, true)
            startActivity(intent)
        }
        dialog.findViewById<LinearLayout>(R.id.menuImagens)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ImagensRecebimentoActivity::class.java))
        }
        dialog.findViewById<LinearLayout>(R.id.menuReceber)?.setOnClickListener {
            dialog.dismiss()
            selecionarTab(Constants.STATUS_PENDENTE, btnViagensReceber)
        }
        dialog.findViewById<LinearLayout>(R.id.menuRecebidas)?.setOnClickListener {
            dialog.dismiss()
            selecionarTab(Constants.STATUS_RECEBIDO, btnViagensRecebidas)
        }
        dialog.findViewById<LinearLayout>(R.id.menuAnomalia)?.setOnClickListener {
            dialog.dismiss()
            selecionarTab(Constants.STATUS_ANOMALIA, btnViagensAnomalia)
        }
        dialog.findViewById<LinearLayout>(R.id.menuErro)?.setOnClickListener {
            dialog.dismiss()
            selecionarTab(Constants.STATUS_ERRO, btnViagensErro)
        }

        dialog.show()
    }

    private fun selecionarTab(status: String, botao: LinearLayout) {
        viewModel.selectTab(status)
        selecionarBotao(botao)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.uiState.collectLatest { state ->
                    qtdReceber = state.qtdReceber
                    qtdAnomalia = state.qtdAnomalia
                    qtdErro = state.qtdErro
                    qtdRecebidas = state.qtdRecebidas
                    tvSubtitle.text = state.subtitle
                    tvCountReceber.text = state.qtdReceber
                    tvCountAnomalia.text = state.qtdAnomalia
                    tvCountErro.text = state.qtdErro
                    tvCountRecebidas.text = state.qtdRecebidas
                    adapter.updateItems(state.items)
                    adapter.setProgressoPorViagem(state.progressoViagem)
                    mostrarFiltrosBusca(state.showSearch)

                    swipeRefresh.isRefreshing = state.isRefreshing

                    if (!state.isLoading && state.items.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                        rvRecebimentos.visibility = View.GONE
                    } else {
                        layoutEmptyState.visibility = View.GONE
                        rvRecebimentos.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MainEvent.ShowToast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        is MainEvent.Error -> handleError(event.code)
                        is MainEvent.AllLoaded -> Toast.makeText(this@MainActivity, "Todas as ${event.total} viagens carregadas!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun mostrarFiltrosBusca(mostrar: Boolean) {
        layoutBusca.visibility = if (mostrar) View.VISIBLE else View.GONE
        layoutFiltros.visibility = if (mostrar) View.VISIBLE else View.GONE
        layoutBusca.requestLayout()
        layoutFiltros.requestLayout()
    }

    private fun selecionarBotao(botaoSelecionado: LinearLayout) {
        val density = resources.displayMetrics.density
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_red)
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            val flags = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            window.decorView.systemUiVisibility = flags
        }

        val corSelecionado = 0xFFC62828.toInt()
        val corVerde = 0xFF2E7D32.toInt()

        botoes.forEach { botao ->
            val selecionado = botao == botaoSelecionado
            botao.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (selecionado) 0xFFFFFAFB.toInt() else 0xFFFFFFFF.toInt())
                setStroke(
                    (2 * density).toInt(),
                    if (selecionado) corSelecionado else 0x00000000
                )
                cornerRadius = 16f * density
            }
            botao.elevation = if (selecionado) 4f else 0f
        }

        pintarBadge(botaoSelecionado == btnViagensReceber, 0xFFFFC107.toInt(), tvCountReceber, viewLineReceber)
        pintarBadge(botaoSelecionado == btnViagensAnomalia, 0xFFFFC107.toInt(), tvCountAnomalia, viewLineAnomalia)
        pintarBadge(botaoSelecionado == btnViagensErro, 0xFFF44336.toInt(), tvCountErro, viewLineErro)
        pintarBadge(botaoSelecionado == btnViagensRecebidas, 0xFF2E7D32.toInt(), tvCountRecebidas, viewLineRecebidas)

        layoutViewMode.visibility =
            if (botaoSelecionado == btnViagensRecebidas) View.VISIBLE else View.GONE
    }

    private fun setModoVisualizacao(grid: Boolean) {
        isModoGrid = grid
        adapter.setModoGrid(grid)

        val corAtiva = ContextCompat.getColor(this, R.color.primary_red)
        val corInativa = 0xFFB0BEC5.toInt()
        btnModoLista.setColorFilter(if (!grid) corAtiva else corInativa)
        btnModoGrid.setColorFilter(if (grid) corAtiva else corInativa)

        rvRecebimentos.layoutManager = if (grid) {
            GridLayoutManager(this, 2).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (adapter.getItemViewType(position) == RecebimentoAdapter.TYPE_HEADER) 2 else 1
                    }
                }
            }
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun pintarBadge(selecionado: Boolean, corPadrao: Int, tvCount: TextView, viewLine: View) {
        val cor = corPadrao
        val corLinha = if (selecionado) corPadrao else (corPadrao and 0x00FFFFFF) or (0x4D shl 24)
        val density = resources.displayMetrics.density
        tvCount.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(cor)
            cornerRadius = 10f * density
            setStroke((2 * density).toInt(), 0xFFFFFFFF.toInt())
        }
        viewLine.setBackgroundColor(corLinha)
    }

    private fun setupRecyclerView() {
        adapter = RecebimentoAdapter()

        adapter.setOnItemClickListener { recebimento ->
            val intent = Intent(this, DetalhesViagemActivity::class.java)
            intent.putExtra("VIAGEM_ID", recebimento.id)
            startActivity(intent)
        }

        adapter.setOnGerarExcelClickListener { recebimento ->
            gerarExcel(recebimento)
        }

        adapter.setOnReceberClickListener { recebimento ->
            gerarProtocolo(recebimento)
        }

        val layoutManager = LinearLayoutManager(this)
        rvRecebimentos.layoutManager = layoutManager
        rvRecebimentos.adapter = adapter

        rvRecebimentos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0) {
                        viewModel.loadMore()
                    }
                }
            }
        })
    }

    private fun gerarExcel(recebimento: Recebimento) {
        val storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE

        Toast.makeText(this, "📥 Gerando Excel da viagem ${recebimento.id.takeLast(7)}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = ExcelDownloader.gerarExcel(
                context = this@MainActivity,
                apiService = apiService,
                storeId = storeId,
                viagemId = recebimento.id
            )
            result.onSuccess { caminho ->
                Toast.makeText(this@MainActivity, "✅ Excel salvo em: $caminho", Toast.LENGTH_LONG).show()
            }.onFailure { erro ->
                Toast.makeText(this@MainActivity, "❌ ${erro.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun gerarProtocolo(recebimento: Recebimento) {
        val storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE

        LogHelper.i("gerarProtocolo: iniciando viagem ${recebimento.id}")
        Toast.makeText(this, "📄 Gerando protocolo para viagem ${recebimento.id.takeLast(7)}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val response = apiService.gerarProtocolo(storeId, ProtocoloRequest(recebimento.id))
                LogHelper.i("gerarProtocolo: HTTP ${response.code()} body=${response.body()}")
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        LogHelper.i("gerarProtocolo: sucesso protocolo=${body.protocolo}")
                        viewModel.refresh()
                        mostrarProtocoloDialog(body.protocolo ?: "Gerado")
                    } else {
                        LogHelper.w("gerarProtocolo: success=false msg=${body?.message}")
                        Toast.makeText(this@MainActivity, "❌ ${body?.message ?: "Falha ao gerar protocolo"}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    LogHelper.w("gerarProtocolo: erro HTTP ${response.code()}")
                    Toast.makeText(this@MainActivity, "❌ Erro ${response.code()}: ${response.message()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                LogHelper.e("gerarProtocolo: exceção", e)
                Toast.makeText(this@MainActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarProtocoloDialog(protocolo: String) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_protocolo_sucesso)

        dialog.findViewById<TextView>(R.id.tvProtocoloDialog).text = protocolo

        dialog.findViewById<View>(R.id.btnOkEntendi).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setCancelable(true)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.CENTER)
            val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialog.show()
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    private fun showExitDialog() {
        val dialog = Dialog(this)
        dialog.setCancelable(true)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = 16f * dp
                setStroke((2 * dp).toInt(), 0xFF333333.toInt())
            }
        }

        root.addView(TextView(this).apply {
            text = "⚠️"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(0xFFE5093A.toInt())
        })

        root.addView(TextView(this).apply {
            text = "Deseja realmente sair do aplicativo?"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFE5093A.toInt())
            setPadding(0, 12, 0, 20)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        val btnLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        btnLayout.addView(Button(this).apply {
            text = "NÃO"
            layoutParams = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f).apply {
                marginEnd = (6 * dp).toInt()
            }
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundDrawable(criarDrawableSolido(0xFFD32F2F.toInt()))
            setOnClickListener { dialog.dismiss() }
        })

        btnLayout.addView(Button(this).apply {
            text = "SIM, SAIR"
            layoutParams = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundDrawable(criarDrawableSolido(0xFF4CAF50.toInt()))
            setOnClickListener {
                dialog.dismiss()
                finishAffinity()
            }
        })

        root.addView(btnLayout)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun criarDrawableBorda(cor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFFFFFFFF.toInt())
            setStroke(2, cor.toInt())
            cornerRadius = 50f
        }
    }

    private fun criarDrawableSolido(cor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(cor)
            cornerRadius = 50f
        }
    }

    private fun handleError(code: Int) {
        when (code) {
            401 -> {
                Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
                SessionExpiredHandler.handleSessionExpired(this)
            }
            403 -> Toast.makeText(this, "Acesso negado.", Toast.LENGTH_SHORT).show()
            404 -> Toast.makeText(this, "Recurso não encontrado.", Toast.LENGTH_SHORT).show()
            500, 502, 503 -> Toast.makeText(this, "Erro no servidor. Tente novamente.", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(this, "Erro: Código $code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
