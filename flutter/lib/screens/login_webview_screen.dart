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
            if (_oauthEmAndamento && url != null && MicrosoftOAuth.isRedirectUrl(url)) {
              _tratarOAuth(url);
            }
          },
          onPageFinished: (url) async {
            setState(() => _carregando = false);
            if (!_loginConcluido && !_tokenEncontrado) {
              _verificarTokenViaJavaScript();
              if (_autoLogin) _preencherLoginAutomatico();
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

  void _salvarToken(String token) {
    if (_loginConcluido) return;
    _loginConcluido = true;
    _tokenEncontrado = true;
    _session.saveToken(token);
    _session.saveUserInfo("usuario@americanas.io", "Usuário", "L291");
    _iniciarOAuthBRLog();
  }

  void _iniciarOAuthBRLog() {
    if (_oauthEmAndamento || _oauthIniciado) return;
    _oauthEmAndamento = true;
    _oauthIniciado = true;
    LogHelper.d("OAuth BRLog: iniciando fluxo silencioso");
    _controller.loadRequest(Uri.parse(MicrosoftOAuth.getAuthorizeUrl()));
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
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(Constants.primaryRed),
      appBar: AppBar(
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
          if (_carregando)
            Container(
              color: const Color(Constants.primaryRed),
              child: const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(color: Colors.white),
                    SizedBox(height: 16),
                    Text('Autenticando...',
                        style: TextStyle(color: Colors.white)),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
