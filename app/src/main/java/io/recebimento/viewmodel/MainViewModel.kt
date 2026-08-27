package io.recebimento.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.recebimento.network.ApiClient
import io.recebimento.network.BrasilRiskClient
import io.recebimento.network.MicrosoftOAuth
import io.recebimento.network.Recebimento
import io.recebimento.network.RecebimentoResponse
import io.recebimento.network.SessionManager
import io.recebimento.network.TokenBody
import io.recebimento.utils.Constants
import io.recebimento.utils.LogHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class MainUiState(
    val currentStatus: String = Constants.STATUS_PENDENTE,
    val currentPage: Int = 1,
    val isLoading: Boolean = false,
    val isLastPage: Boolean = false,
    val currentSort: String = "desc",
    val searchQuery: String = "",
    val items: List<Recebimento> = emptyList(),
    val totalItems: Int = 0,
    val qtdReceber: String = "0",
    val qtdAnomalia: String = "0",
    val qtdErro: String = "0",
    val qtdRecebidas: String = "0",
    val progressoViagem: Map<String, Double> = emptyMap(),
    val subtitle: String = "VIAGENS A RECEBER",
    val showSearch: Boolean = false,
    val isRefreshing: Boolean = false
)

sealed class MainEvent {
    data class ShowToast(val message: String) : MainEvent()
    data class Error(val code: Int) : MainEvent()
    data class AllLoaded(val total: Int) : MainEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val apiService = ApiClient.getInstance(application).getApiService()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>()
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private val allItems = mutableListOf<Recebimento>()

    private val brasilRiskService = BrasilRiskClient.getService()

    // Última sincronização do BRLog (evita repetir a cada troca de aba)
    private var ultimaSyncBRLog = 0L

    private val storeId: String
        get() = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE

    init {
        _uiState.update { it.copy(progressoViagem = sessionManager.getBrlogProgress()) }
        carregarDashboard()
        sincronizarProgressoBRLog()
    }

    fun selectTab(status: String) {
        val subtitle = when (status) {
            Constants.STATUS_PENDENTE -> "VIAGENS A RECEBER"
            Constants.STATUS_ANOMALIA -> "VIAGENS COM ANOMALIA"
            Constants.STATUS_ERRO -> "VIAGENS COM ERRO"
            Constants.STATUS_RECEBIDO -> "VIAGENS RECEBIDAS"
            else -> "VIAGENS A RECEBER"
        }
        val showSearch = status == Constants.STATUS_RECEBIDO
        _uiState.update { it.copy(currentStatus = status, subtitle = subtitle, showSearch = showSearch) }
        loadRecebimentos(reset = true)
    }

    fun setSort(sort: String) {
        _uiState.update { it.copy(currentSort = sort) }
        loadRecebimentos(reset = true)
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadRecebimentos(reset = true)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadRecebimentos(reset = true)
        sincronizarProgressoBRLog()
    }

    fun recarregarProgressoBRLog() {
        _uiState.update { it.copy(progressoViagem = sessionManager.getBrlogProgress()) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.isLoading && !state.isLastPage) {
            loadRecebimentos(reset = false)
        }
    }

    fun carregarDashboard() {
        viewModelScope.launch {
            try {
                val defPendente = async { apiService.getRecebimentos(storeId, Constants.STATUS_PENDENTE, null, "asc", 1) }
                val defAnomalia = async { apiService.getRecebimentos(storeId, Constants.STATUS_ANOMALIA, null, "asc", 1) }
                val defErro = async { apiService.getRecebimentos(storeId, Constants.STATUS_ERRO, null, "asc", 1) }
                val defRecebido = async { apiService.getRecebimentos(storeId, Constants.STATUS_RECEBIDO, null, "asc", 1) }

                val responsePendente = defPendente.await()
                val responseAnomalia = defAnomalia.await()
                val responseErro = defErro.await()
                val responseRecebido = defRecebido.await()

                val qtdReceber = if (responsePendente.isSuccessful) responsePendente.body()?.qtdRecebimentos?.pendente?.toString() ?: "0" else "0"
                val qtdAnomalia = if (responseAnomalia.isSuccessful) responseAnomalia.body()?.qtdRecebimentos?.erro?.toString() ?: "0" else "0"
                val qtdErro = if (responseErro.isSuccessful) responseErro.body()?.qtdRecebimentos?.erro?.toString() ?: "0" else "0"
                val qtdRecebidas = if (responseRecebido.isSuccessful) responseRecebido.body()?.totalItems?.toString() ?: "0" else "0"

                _uiState.update { it.copy(
                    qtdReceber = qtdReceber,
                    qtdAnomalia = qtdAnomalia,
                    qtdErro = qtdErro,
                    qtdRecebidas = qtdRecebidas
                )}
            } catch (e: Exception) {
                LogHelper.e("carregarDashboard: Erro", e)
            }
        }
    }

    private fun loadRecebimentos(reset: Boolean) {
        val state = _uiState.value
        if (state.isLoading) return
        if (!reset && state.isLastPage) return

        _uiState.update { it.copy(isLoading = true) }
        if (reset) {
            allItems.clear()
            _uiState.update { it.copy(currentPage = 1, isLastPage = false, items = emptyList()) }
        }

        val search = if (state.searchQuery.isNotEmpty()) state.searchQuery else null
        val page = if (reset) 1 else state.currentPage + 1

        viewModelScope.launch {
            try {
                val response = apiService.getRecebimentos(storeId, state.currentStatus, search, state.currentSort, page)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        updateUI(body, reset, page)
                    } else {
                        _events.emit(MainEvent.ShowToast("Dados vazios"))
                    }
                } else {
                    _events.emit(MainEvent.Error(response.code()))
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                _events.emit(MainEvent.ShowToast("Erro de rede: ${e.message}"))
                LogHelper.e("loadRecebimentos: Erro de rede", e)
            } catch (e: HttpException) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                _events.emit(MainEvent.Error(e.code()))
                LogHelper.e("loadRecebimentos: HTTP Error", e)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                _events.emit(MainEvent.ShowToast("Erro: ${e.message}"))
                LogHelper.e("loadRecebimentos: Erro", e)
            }
        }
    }

    private fun updateUI(response: RecebimentoResponse, reset: Boolean, page: Int) {
        val state = _uiState.value
        val filtered = response.recebimentos.filter { it.status.equals(state.currentStatus, ignoreCase = true) }
        allItems.addAll(filtered)

        _uiState.update { it.copy(
            isLoading = false,
            isRefreshing = false,
            currentPage = page,
            isLastPage = page >= response.totalPages,
            totalItems = response.totalItems,
            items = allItems.toList(),
            progressoViagem = sessionManager.getBrlogProgress()
        )}

        if (!reset && page >= response.totalPages && response.totalItems > 0) {
            viewModelScope.launch {
                _events.emit(MainEvent.AllLoaded(response.totalItems))
            }
        }
    }

    fun getProgressText(): String {
        val state = _uiState.value
        return if (state.totalItems > 0) {
            "${allItems.size} de ${state.totalItems} viagens"
        } else {
            "${allItems.size} viagens"
        }
    }

    /**
     * Renova o token Microsoft (refresh_token) e sincroniza o progresso das
     * viagens direto da resposta do login BRLog. Roda sem WebView.
     */
    fun sincronizarProgressoBRLog() {
        val agora = System.currentTimeMillis()
        if (agora - ultimaSyncBRLog < 5 * 60 * 1000L) return
        ultimaSyncBRLog = agora

        val refreshToken = sessionManager.getBrlogRefreshToken()
        if (refreshToken.isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                val token = MicrosoftOAuth.renovarToken(refreshToken) ?: run {
                    LogHelper.e("sincronizarProgressoBRLog: refresh token inválido")
                    return@launch
                }
                token.refreshToken?.let { sessionManager.saveBrlogRefreshToken(it) }

                val resposta = brasilRiskService.loginMicrosoft(TokenBody(token.accessToken)).execute()
                if (resposta.isSuccessful) {
                    val body = resposta.body()
                    val lista = body?.notaFiscal ?: return@launch
                    val progresso = lista.mapNotNull { nota ->
                        val numero = nota.numeroViagem ?: return@mapNotNull null
                        val p = nota.progressoViagem ?: return@mapNotNull null
                        numero to normalizarProgresso(p)
                    }.toMap()
                    if (progresso.isNotEmpty()) {
                        sessionManager.saveBrlogProgress(progresso)
                        _uiState.update { it.copy(progressoViagem = progresso) }
                        LogHelper.d("sincronizarProgressoBRLog: ${progresso.size} viagens")
                    }
                    body.codEmpresaUsuario?.let { sessionManager.saveBrlogCodEmpresaUsuario(it) }
                    if (lista.isNotEmpty()) {
                        sessionManager.saveBrlogNotas(lista)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("sincronizarProgressoBRLog: erro", e)
            }
        }
    }

    private fun normalizarProgresso(p: Double): Double {
        var valor = p
        if (valor > 1.0) valor /= 100.0
        return valor.coerceIn(0.0, 1.0)
    }

    fun clearSession() {
        sessionManager.clearAll()
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
}
