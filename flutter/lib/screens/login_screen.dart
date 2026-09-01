import 'package:flutter/material.dart';
import 'dart:convert';
import '../network/session_manager.dart';
import '../utils/constants.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _senhaController = TextEditingController();
  bool _senhaVisivel = false;
  bool _salvarCredenciais = false;

  @override
  void initState() {
    super.initState();
    _preencherCredenciais();
  }

  Future<void> _preencherCredenciais() async {
    final session = await SessionManager.create();
    _emailController.text = session.getUserEmail() ?? '';
    _senhaController.text = session.getSavedPassword() ?? '';
    _salvarCredenciais = session.hasSavedCredentials();
    if (mounted) setState(() {});
  }

  void _entrar() {
    final email = _emailController.text.trim();
    final senha = _senhaController.text;
    if (email.isEmpty || senha.isEmpty) {
      _toast("Preencha email e senha para entrar");
      return;
    }
    if (_salvarCredenciais && !_emailController.text.isEmpty) {
      // salvo ao abrir o webview
    }
    Navigator.of(context).pushNamed(
      '/login_webview',
      arguments: {
        'autoLogin': true,
        'email': email,
        'senha': senha,
      },
    );
  }

  void _entrarMicrosoft() {
    Navigator.of(context).pushNamed(
      '/login_webview',
      arguments: {
        'autoLogin': false,
        'oauthOnly': false,
      },
    );
  }

  void _entrarComTokenManual() {
    final tokenController = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Entrar com Token'),
        content: TextField(
          controller: tokenController,
          maxLines: 3,
          decoration: const InputDecoration(
            hintText: 'Cole o token JWT aqui',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            onPressed: () async {
              final token = tokenController.text.trim();
              if (token.isEmpty || token.length < 50) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Token inválido')),
                );
                return;
              }
              Navigator.pop(ctx);
              await _salvarTokenManual(token);
            },
            child: const Text('Entrar'),
          ),
        ],
      ),
    );
  }

  Future<void> _salvarTokenManual(String token) async {
    try {
      final session = await SessionManager.create();
      
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
      session.saveTokenWithExpiry(token,
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
          '';

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

      session.saveUserInfo(email, nome, loja ?? session.getUserStore());
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Login realizado com sucesso'),
            backgroundColor: Colors.green,
          ),
        );
        Navigator.of(context).pushReplacementNamed('/home');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erro ao salvar token: $e')),
        );
      }
    }
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(Constants.primaryRed),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            children: [
              const SizedBox(height: 48),
              // Logo
              Container(
                width: 84,
                height: 84,
                decoration: const BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                ),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Image.asset(
                    'assets/drawables/ic_caminhao.png',
                    fit: BoxFit.contain,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                'recebimento',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                'Para ter acesso ao portal, vamos manter os fluxos de '
                'autenticação segura.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white, fontSize: 13),
              ),
              const SizedBox(height: 28),
              // Card branco
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                  boxShadow: const [
                    BoxShadow(
                      color: Colors.black26,
                      blurRadius: 8,
                      offset: Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text('E-mail',
                        style: TextStyle(
                            color: Color(0xFF49454F),
                            fontSize: 12,
                            fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    TextField(
                      controller: _emailController,
                      keyboardType: TextInputType.emailAddress,
                      decoration: const InputDecoration(
                        hintText: 'Email Microsoft',
                        hintStyle: TextStyle(color: Color(0xFF9CA3AF)),
                        border: OutlineInputBorder(),
                        contentPadding:
                            EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      ),
                    ),
                    const SizedBox(height: 12),
                    const Text('Senha',
                        style: TextStyle(
                            color: Color(0xFF49454F),
                            fontSize: 12,
                            fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    TextField(
                      controller: _senhaController,
                      obscureText: !_senhaVisivel,
                      decoration: InputDecoration(
                        hintText: 'Senha',
                        hintStyle: const TextStyle(color: Color(0xFF9CA3AF)),
                        border: const OutlineInputBorder(),
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 12),
                        suffixIcon: IconButton(
                          icon: Icon(_senhaVisivel
                              ? Icons.visibility
                              : Icons.visibility_off),
                          onPressed: () =>
                              setState(() => _senhaVisivel = !_senhaVisivel),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton(
                      onPressed: _entrar,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(Constants.primaryRed),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                        textStyle: const TextStyle(
                            fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                      child: const Text('ENTRAR'),
                    ),
                    const SizedBox(height: 10),
                    ElevatedButton.icon(
                      onPressed: _entrarMicrosoft,
                      icon: const Icon(Icons.business, size: 20),
                      label: const Text('ENTRAR COM MICROSOFT'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFF3F4F9),
                        foregroundColor: const Color(Constants.textDark),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                        textStyle: const TextStyle(
                            fontSize: 14, fontWeight: FontWeight.bold),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextButton.icon(
                      onPressed: _entrarComTokenManual,
                      icon: const Icon(Icons.vpn_key, size: 18),
                      label: const Text('ENTRAR COM TOKEN'),
                      style: TextButton.styleFrom(
                        foregroundColor: const Color(Constants.textDark),
                        textStyle: const TextStyle(
                            fontSize: 13, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              // Salvar credenciais
              InkWell(
                onTap: () =>
                    setState(() => _salvarCredenciais = !_salvarCredenciais),
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _salvarCredenciais
                            ? Icons.check_box
                            : Icons.check_box_outline_blank,
                        color: Colors.white,
                      ),
                      const SizedBox(width: 8),
                      const Text(
                        'Salvar credenciais e ativar login automático',
                        style: TextStyle(color: Colors.white, fontSize: 13),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
