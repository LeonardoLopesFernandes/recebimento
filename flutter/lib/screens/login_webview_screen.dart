import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../models/brasil_risk.dart';
import '../network/session_manager.dart';
import '../network/microsoft_oauth.dart';
import '../network/brasil_risk_client.dart';
import '../network/api_service.dart';
import '../network/api_client.dart';
import '../utils/constants.dart';
import '../utils/log_helper.dart';

/// URL de login SAML do minhaloja (fiel ao LoginWebViewActivity.carregarLogin).
const String _loginUrl =
    "https://login.microsoftonline.com/e316d1ac-42c8-4d30-817c-12c7a71f8ab2/saml2?SAMLRequest=nVPLjhoxEPyVke%2Beh4fdYS1gRUBRkDYJApJDLlGPp2dx4gdxezYkXx8xQMIhy4Gru1RVXdUePe6tSV4wkPZuzIo0Z4%2BTEYE1Oznt4tat8EeHFJO9NY5kPxizLjjpgTRJBxZJRiXX0%2FdPUqS53AUfvfKGJYv5mH29K%2B%2BwflDQVrlohVICy5Yln8%2BCIs1ZsiDqcOEogotjJnJxz%2FN7LgYbUcqikqJMi7z4wpLlifqNdo12z9d91EcQyXebzZIvP643LJkjRe0g9tLbGHcks8z4Z%2B1Sq1Xw5NvondEOU%2BVthmVx3xSg%2BECoIR80Zc6HRaV4IVQFVdEOoRbZIRLBkikRhgPxzDvqLIY1hhet8NPq6Z8UGQ5d3Pqgf%2FcmUrAYtAIHlGqfWe22wI3%2FBpkCY2pQ39mxDNlHFC5auL48nN2wCcKwaaui4YhNyQfDhwEHqEtegWhzUau6yttRdiFyrv8DWFzMl95o9euW%2Bt%2F6YCG%2Bji7Son%2FRDW97qEQL2kybJiARS6bG%2BJ%2BzgBBxzGLokGVna6ejxKY%2F0Zl3Efc3nejM2x0ETYd7wD2oeM77knhmgGiF7S3pX4UpqQ7USLJztEOlW43NqYv%2FGZgcZ6%2Fs%2F3d6%2BW8nfwA%3D&sso_reload=true";

class LoginWebViewScreen extends StatefulWidget {
  const LoginWebViewScreen({super.key});

  @override
  State<LoginWebViewScreen> createState() => _LoginWebViewScreenState();
}

class _LoginWebViewScreenState extends State<LoginWebViewScreen> {
  late final WebViewController _controller;
  late final SessionManager _session;
  late final ApiService _apiService;

  bool _loginConcluido = false;
  bool _tokenEncontrado = false;
  bool _oauthEmAndamento = false;
  bool _oauthIniciado = false;
  bool _oauthSomente = false;
  bool _oauthPaginaVisivel = false;
  bool _oauthConsentimentoClicado = false;
  Timer? _oauthTimer;
  bool _autoLogin = false;
  bool _carregando = true;
  bool _pronto = false;
  String _email = '';
  String _senha = '';
  bool _argsLidos = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_argsLidos) {
      _argsLidos = true;
      final args = ModalRoute.of(context)?.settings.arguments
          as Map<String, dynamic>?;
      _oauthSomente = args?['oauthOnly'] == true;
      _autoLogin = args?['autoLogin'] == true;
      _email = args?['email'] ?? '';
      _senha = args?['senha'] ?? '';
      _initAsync();
    }
  }

  Future<void> _initAsync() async {
    _session = await SessionManager.create();
    _apiService = ApiService(ApiClient(_session));
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36")
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            if (url != null) _verificarTokenNaUrl(url);
            if (url != null) _verificarTokenViaCookies();
            if (_oauthEmAndamento && url != null && MicrosoftOAuth.isRedirectUrl(url)) {
              _tratarOAuth(url);
            }
          },
          onPageFinished: (url) async {
            // Antes de capturar o token, esconde o carregamento para o
            // usuário ver/preencher o login SAML. Depois do token, mantém a
            // tela de carregamento (logo centralizado) e NUNCA revela a
            // WebView do OAuth BRLog — espelha o LoginWebViewActivity Kotlin.
            if (!_loginConcluido && !_tokenEncontrado) {
              setState(() => _carregando = false);
              _verificarTokenViaJavaScript();
              _verificarTokenViaCookies();
              if (_autoLogin) _preencherLoginAutomatico();
            }
            if (_oauthEmAndamento && url != null) {
              _tratarPaginaOAuth(url);
            }
          },
          onNavigationRequest: (request) {
            final url = request.url;
            if (!_loginConcluido) _verificarTokenNaUrl(url);
            if (_oauthEmAndamento && MicrosoftOAuth.isRedirectUrl(url)) {
              _tratarOAuth(url);
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      );

    if (_oauthSomente) {
      _iniciarOAuthBRLog();
    } else {
      await _controller.loadRequest(Uri.parse(_loginUrl));
    }
    _pronto = true;
    if (mounted) setState(() {});
  }

  void _verificarTokenNaUrl(String url) {
    if (_loginConcluido || _tokenEncontrado) return;
    for (final pattern in [r'[?&]newToken=([^&]+)', r'[?&]token=([^&]+)']) {
      final m = RegExp(pattern).firstMatch(url);
      if (m != null) {
        final token = m.group(1)!;
        if (token.length > 50) {
          _salvarToken(token);
          return;
        }
      }
    }
  }

  Future<void> _verificarTokenViaJavaScript() async {
    if (_tokenEncontrado || _loginConcluido) return;
    const script =
        "(function(){return localStorage.getItem('newToken')||"
        "localStorage.getItem('token')||"
        "sessionStorage.getItem('newToken')||"
        "sessionStorage.getItem('token')||window.newToken||window.token;"
        "})();";
    try {
      final result = await _controller.runJavaScriptReturningResult(script);
      String? token;
      if (result is String) {
        token = result.replaceAll('"', '').trim();
      }
      if (token != null && token != 'null' && token.length > 50) {
        _salvarToken(token);
      }
    } catch (e) {
      LogHelper.e("verificarTokenViaJavaScript", e);
    }
  }

  /// Espelha o verificarCookies() do Kotlin LoginWebViewActivity: o portal
  /// minhaloja pode entregar o token em cookie (newToken/token) em vez de
  /// localStorage, e sem esta checagem o app ficava preso na WebView.
  Future<void> _verificarTokenViaCookies() async {
    if (_tokenEncontrado || _loginConcluido) return;
    try {
      final cookies = await WebViewCookieManager().getCookies(
        domain: Uri.parse("https://minhaloja.americanas.io"),
      );
      for (final c in cookies) {
        final name = c.name;
        if (name == "newToken" || name == "token") {
          final token = c.value;
          if (token.isNotEmpty && token.length > 50) {
            _salvarToken(token);
            return;
          }
        }
      }
    } catch (e) {
      LogHelper.e("verificarTokenViaCookies", e);
    }
  }

  void _salvarToken(String token) {
    if (_loginConcluido) return;
    _loginConcluido = true;
    _tokenEncontrado = true;
    if (mounted) setState(() => _carregando = true);

    // Decodifica claims do JWT
    Map<String, dynamic>? claims;
    try {
      final parts = token.split('.');
      if (parts.length == 3) {
        var p = parts[1];
        p = p.padRight(p.length + (4 - p.length % 4) % 4, '=');
        final j = jsonDecode(utf8.decode(base64Url.decode(p)));
        if (j is Map) claims = Map<String, dynamic>.from(j);
      }
    } catch (_) {}

    // Salva token com expiry do JWT (exp)
    _session.saveTokenWithExpiry(token,
        expiryEpochSeconds: claims?['exp'] is int ? claims!['exp'] as int : null);

    // Suporta JWT com claims aninhados em "user" (minhaloja/trocafacil)
    // e flat (Microsoft OAuth padrão).
    final user = claims?['user'] is Map
        ? Map<String, dynamic>.from(claims!['user'])
        : null;

    String email = (user?['email'] ??
            claims?['email'] ??
            claims?['preferred_username'] ??
            claims?['upn'])
        ?.toString() ??
        (_email.isNotEmpty ? _email : '');

    String nome = (user?['nome'] ??
            user?['name'] ??
            claims?['name'] ??
            claims?['given_name'])
        ?.toString() ??
        '';

    final stores = user?['stores'] ?? claims?['stores'];
    final loja = user?['loja']?.toString() ??
        claims?['loja']?.toString() ??
        (stores is List && stores.isNotEmpty ? stores.first.toString() : null);

    if (email.isEmpty) email = 'usuario@americanas.io';
    if (nome.isEmpty) {
      nome = email.split('@').first.replaceAll('.', ' ').replaceAll('_', ' ');
      nome = nome
          .split(' ')
          .map((w) => w.isNotEmpty ? w[0].toUpperCase() + w.substring(1) : w)
          .join(' ');
    }

    _session.saveUserInfo(email, nome, loja ?? _session.getUserStore() ?? 'L291');
    _iniciarOAuthBRLog();
  }

  void _iniciarOAuthBRLog() {
    if (_oauthEmAndamento || _oauthIniciado) return;
    _oauthEmAndamento = true;
    _oauthIniciado = true;
    LogHelper.d("OAuth BRLog: iniciando fluxo silencioso");
    _oauthTimer?.cancel();
    _oauthTimer = Timer(const Duration(seconds: 90), () {
      if (_oauthEmAndamento && mounted) {
        LogHelper.e("OAuth BRLog: timeout, seguindo sem sincronizar");
        _oauthEmAndamento = false;
        _finalizarLogin();
      }
    });
    _controller.loadRequest(Uri.parse(MicrosoftOAuth.getAuthorizeUrl()));
  }

  Future<void> _tratarPaginaOAuth(String url) async {
    if (!url.startsWith("https://login.microsoftonline.com")) return;
    if (MicrosoftOAuth.isRedirectUrl(url)) return;
    final tipo = await _avaliarPaginaOAuth();
    if (tipo == "login") {
      // Mantém a tela de carregamento visível (não revela a WebView). Se
      // houver credenciais salvas, tenta preencher silenciosamente; caso
      // contrário o timer de 90s encerra e segue para o home.
      _preencherLoginBRLog();
    } else if (tipo == "consent") {
      if (!_oauthConsentimentoClicado) {
        _oauthConsentimentoClicado = true;
        await _controller.runJavaScript(
          "(function(){var b=document.querySelector('input[type=submit]');"
          "if(b){b.click();return 'ok';}return 'no';})()");
      }
    }
  }

  Future<String> _avaliarPaginaOAuth() async {
    try {
      final result = await _controller.runJavaScriptReturningResult(
        "(function(){if(document.getElementById('i0116'))return 'login';"
        "var b=document.querySelector('input[type=submit]');"
        "if(b){var v=(b.value||b.getAttribute('value')||'').toLowerCase();"
        "if(v.indexOf('accept')>=0||v.indexOf('aceitar')>=0||v.indexOf('concordar')>=0||v.indexOf('permitir')>=0)return 'consent';}"
        "return 'none';})()");
      if (result is String) return result.replaceAll('"', '').trim();
      return 'none';
    } catch (_) {
      return 'none';
    }
  }

  Future<void> _preencherLoginBRLog() async {
    if (!_oauthEmAndamento || !mounted) return;
    final url = await _controller.currentUrl();
    if (url == null || !url.contains("login.microsoftonline.com")) return;
    final email = _session.getUserEmail() ?? '';
    final senha = _session.getSavedPassword() ?? '';
    if (email.isEmpty || senha.isEmpty) return;
    final script = _montarScriptPreenchimento(email, senha);
    await _controller.runJavaScript(script);
  }

  void _tratarOAuth(String url) {
    if (!_oauthEmAndamento) return;
    final erro = MicrosoftOAuth.extrairErro(url);
    final codigo = MicrosoftOAuth.extrairCodigo(url);
    if (erro != null || codigo != null) {
      _oauthEmAndamento = false;
      if (codigo != null) {
        _trocarCodigoBRLog(codigo);
      } else {
        LogHelper.e("OAuth BRLog: erro AAD ($erro)");
        _finalizarLogin();
      }
    }
  }

  Future<void> _trocarCodigoBRLog(String codigo) async {
    final token = await MicrosoftOAuth.trocarCodigoPorToken(codigo);
    if (token == null) {
      final motivo = MicrosoftOAuth.ultimoErro ?? "sem resposta";
      LogHelper.e("OAuth BRLog: falha na troca de token ($motivo)");
      _finalizarLogin();
      return;
    }
    if (token.refreshToken != null) {
      _session.saveBrlogRefreshToken(token.refreshToken!);
    }
    await _sincronizarProgressoBRLog(token.accessToken);
    _finalizarLogin();
  }

  Future<void> _sincronizarProgressoBRLog(String accessToken) async {
    try {
      final api = BrasilRiskClient();
      final resposta =
          await api.loginMicrosoft(TokenBody(token: accessToken));
      final lista = resposta.notaFiscal ?? [];
      final progresso = <String, double>{};
      for (final nota in lista) {
        if (nota.numeroViagem == null || nota.progressoViagem == null) continue;
        var p = nota.progressoViagem!;
        if (p > 1.0) p /= 100.0;
        progresso[nota.numeroViagem!] = p.clamp(0.0, 1.0);
      }
      if (progresso.isNotEmpty) {
        _session.saveBrlogProgress(progresso);
      }
      if (resposta.codEmpresaUsuario != null) {
        _session.saveBrlogCodEmpresaUsuario(resposta.codEmpresaUsuario!);
      }
      if (lista.isNotEmpty) _session.saveBrlogNotas(lista);
    } catch (e) {
      LogHelper.e("BRLog login erro", e);
    }
  }

  void _finalizarLogin() {
    _oauthTimer?.cancel();
    if (_oauthSomente) {
      Navigator.of(context).pop();
      return;
    }
    Navigator.of(context).pushReplacementNamed('/home');
  }

  Future<void> _preencherLoginAutomatico() async {
    final url = await _controller.currentUrl();
    if (url == null || !url.contains("login.microsoftonline.com")) return;
    if (_email.isEmpty || _senha.isEmpty) return;
    final script = _montarScriptPreenchimento(_email, _senha);
    await _controller.runJavaScript(script);
  }

  String _montarScriptPreenchimento(String email, String senha) {
    final e = _jsString(email);
    final s = _jsString(senha);
    return "(function(){"
        "var setV=function(el,v){var d=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');"
        "if(d&&d.set){d.set.call(el,v);}else{el.value=v;}"
        "el.dispatchEvent(new Event('input',{bubbles:true}));"
        "el.dispatchEvent(new Event('change',{bubbles:true}));};"
        "var btn=document.getElementById('idSIButton9');"
        "var em=document.getElementById('i0116');"
        "if(em){setV(em,$e);if(btn){btn.click();return 'email-clicked';}return 'email-no-btn';}"
        "var pw=document.getElementById('i0118');"
        "if(pw){setV(pw,$s);if(btn){btn.click();return 'pwd-clicked';}return 'pwd-no-btn';}"
        "return 'no-field';"
        "})()";
  }

  String _jsString(String s) =>
      '"${s.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"';

  @override
  void dispose() {
    _oauthTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(Constants.primaryRed),
      appBar: _carregando
          ? null
          : AppBar(
              backgroundColor: const Color(Constants.primaryRed),
              title: const Text('Login'),
              leading: IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ),
      body: Stack(
        children: [
          if (_pronto)
            WebViewWidget(controller: _controller)
          else
            const SizedBox.shrink(),
          if (_carregando) const _CarregamentoWidget(),
        ],
      ),
    );
  }
}

/// Tela de carregamento fiel ao LoginWebViewActivity do Kotlin: fundo vermelho,
/// logo do caminhão centralizado com animação de pulso e texto "Autenticando...".
/// Exibida após capturar o token, enquanto o fluxo OAuth BRLog roda oculto na
/// WebView; ao concluir, navega para o home sem nunca mostrar a WebView.
class _CarregamentoWidget extends StatefulWidget {
  const _CarregamentoWidget();

  @override
  State<_CarregamentoWidget> createState() => _CarregamentoWidgetState();
}

class _CarregamentoWidgetState extends State<_CarregamentoWidget>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(Constants.primaryRed),
      child: Center(
        child: FadeTransition(
          opacity: _pulse.drive(Tween<double>(begin: 1.0, end: 0.2)),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Image.asset(
                'assets/drawables/ic_caminhao_logo.png',
                width: 160,
                height: 160,
              ),
              const SizedBox(height: 24),
              const Text(
                'Autenticando...',
                style: TextStyle(color: Colors.white, fontSize: 16),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
