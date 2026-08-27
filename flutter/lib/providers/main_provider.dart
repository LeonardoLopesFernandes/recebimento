import 'package:flutter/material.dart';
import '../../models/recebimento.dart';
import '../network/api_client.dart';
import '../network/api_service.dart';
import '../network/session_manager.dart';
import '../network/brasil_risk_client.dart';
import '../network/microsoft_oauth.dart';
import '../models/brasil_risk.dart';
import '../utils/constants.dart';
import '../utils/log_helper.dart';

class MainProvider extends ChangeNotifier {
  final SessionManager sessionManager;
  late final ApiClient apiClient;
  late final ApiService apiService;
  final BrasilRiskClient brasilRiskClient = BrasilRiskClient();

  String currentStatus = Constants.statusPendente;
  int currentPage = 1;
  bool isLoading = false;
  bool isLastPage = false;
  String currentSort = "desc";
  String searchQuery = "";
  List<Recebimento> items = [];
  int totalItems = 0;
  String qtdReceber = "0";
  String qtdAnomalia = "0";
  String qtdErro = "0";
  String qtdRecebidas = "0";
  Map<String, double> progressoViagem = {};
  String subtitle = "VIAGENS A RECEBER";
  bool showSearch = false;
  bool isModoGrid = false;
  bool isRefreshing = false;

  final List<Recebimento> _allItems = [];

  MainProvider(this.sessionManager) {
    apiClient = ApiClient(sessionManager);
    apiService = ApiService(apiClient);
    progressoViagem = sessionManager.getBrlogProgress();
    carregarDashboard();
    sincronizarProgressoBRLog();
  }

  String get storeId =>
      sessionManager.getUserStore() ?? Constants.defaultStore;

  void selectTab(String status) {
    subtitle = switch (status) {
      Constants.statusPendente => "VIAGENS A RECEBER",
      Constants.statusAnomalia => "VIAGENS COM ANOMALIA",
      Constants.statusErro => "VIAGENS COM ERRO",
      Constants.statusRecebido => "VIAGENS RECEBIDAS",
      _ => "VIAGENS A RECEBER",
    };
    showSearch = status == Constants.statusRecebido;
    currentStatus = status;
    notifyListeners();
    loadRecebimentos(reset: true);
  }

  void setSort(String sort) {
    currentSort = sort;
    notifyListeners();
    loadRecebimentos(reset: true);
  }

  void setSearch(String query) {
    searchQuery = query;
    notifyListeners();
    loadRecebimentos(reset: true);
  }

  void setModoGrid(bool grid) {
    isModoGrid = grid;
    notifyListeners();
  }

  void refresh() {
    isRefreshing = true;
    notifyListeners();
    loadRecebimentos(reset: true);
    sincronizarProgressoBRLog();
  }

  void recarregarProgressoBRLog() {
    progressoViagem = sessionManager.getBrlogProgress();
    notifyListeners();
  }

  void loadMore() {
    if (isLoading || isLastPage) return;
    loadRecebimentos(reset: false);
  }

  Future<void> carregarDashboard() async {
    try {
      final results = await Future.wait([
        apiService.getRecebimentos(
            storeId: storeId,
            status: Constants.statusPendente,
            sort: "asc"),
        apiService.getRecebimentos(
            storeId: storeId,
            status: Constants.statusAnomalia,
            sort: "asc"),
        apiService.getRecebimentos(
            storeId: storeId, status: Constants.statusErro, sort: "asc"),
        apiService.getRecebimentos(
            storeId: storeId,
            status: Constants.statusRecebido,
            sort: "asc"),
      ]);
      qtdReceber = results[0].qtdRecebimentos.pendente.toString();
      qtdAnomalia = results[1].qtdRecebimentos.erro.toString();
      qtdErro = results[2].qtdRecebimentos.erro.toString();
      qtdRecebidas = results[3].totalItems.toString();
      notifyListeners();
    } catch (e) {
      LogHelper.e("carregarDashboard: Erro", e);
    }
  }

  Future<void> loadRecebimentos({required bool reset}) async {
    if (isLoading) return;
    if (!reset && isLastPage) return;
    isLoading = true;
    if (reset) {
      _allItems.clear();
      currentPage = 1;
      items = [];
      isLastPage = false;
    }
    notifyListeners();

    final search = searchQuery.isNotEmpty ? searchQuery : null;
    final page = reset ? 1 : currentPage + 1;

    try {
      final response = await apiService.getRecebimentos(
        storeId: storeId,
        status: currentStatus,
        search: search,
        sort: currentSort,
        page: page,
      );
      final filtered = response.recebimentos
          .where((r) => r.status.toLowerCase() == currentStatus.toLowerCase())
          .toList();
      _allItems.addAll(filtered);
      currentPage = page;
      isLastPage = page >= response.totalPages;
      totalItems = response.totalItems;
      items = List.from(_allItems);
      progressoViagem = sessionManager.getBrlogProgress();
      isLoading = false;
      isRefreshing = false;
      notifyListeners();
    } catch (e) {
      isLoading = false;
      isRefreshing = false;
      LogHelper.e("loadRecebimentos: Erro", e);
      notifyListeners();
      rethrow;
    }
  }

  String getProgressText() {
    if (totalItems > 0) {
      return "${_allItems.length} de $totalItems viagens";
    }
    return "${_allItems.length} viagens";
  }

  /// Gera protocolo (receber viagem). Retorna o protocolo gerado ou lança.
  Future<String> gerarProtocolo(Recebimento recebimento) async {
    final response = await apiService.gerarProtocolo(
        storeId, ProtocoloRequest(idRecebimento: recebimento.id));
    if (response.success) {
      refresh();
      return response.protocolo ?? "Gerado";
    } else {
      throw Exception(response.message ?? "Falha ao gerar protocolo");
    }
  }

  /// Renova token Microsoft e sincroniza progresso das viagens (BRLog).
  Future<void> sincronizarProgressoBRLog() async {
    final refreshToken = sessionManager.getBrlogRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) return;
    try {
      final token = await MicrosoftOAuth.renovarToken(refreshToken);
      if (token == null) {
        LogHelper.e("sincronizarProgressoBRLog: refresh token inválido");
        return;
      }
      if (token.refreshToken != null) {
        sessionManager.saveBrlogRefreshToken(token.refreshToken!);
      }
      final resposta = await brasilRiskClient
          .loginMicrosoft(TokenBody(token: token.accessToken));
      final lista = resposta.notaFiscal ?? [];
      final progresso = <String, double>{};
      for (final nota in lista) {
        if (nota.numeroViagem == null || nota.progressoViagem == null) continue;
        progresso[nota.numeroViagem!] = _normalizar(nota.progressoViagem!);
      }
      if (progresso.isNotEmpty) {
        sessionManager.saveBrlogProgress(progresso);
        progressoViagem = progresso;
        notifyListeners();
      }
      if (resposta.codEmpresaUsuario != null) {
        sessionManager.saveBrlogCodEmpresaUsuario(resposta.codEmpresaUsuario!);
      }
      if (lista.isNotEmpty) {
        sessionManager.saveBrlogNotas(lista);
      }
    } catch (e) {
      LogHelper.e("sincronizarProgressoBRLog: erro", e);
    }
  }

  double _normalizar(double p) {
    var v = p;
    if (v > 1.0) v /= 100.0;
    return v.clamp(0.0, 1.0);
  }

  void clearSession() => sessionManager.clearAll();

  bool isLoggedIn() => sessionManager.isLoggedIn();
}
