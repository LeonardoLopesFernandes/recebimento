import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../network/brlog_auth.dart';

/// Backup das fotos no OneDrive da conta Microsoft logada (Graph),
/// 100% Dart (sem plugin nativo).
/// Pasta: Recebimento/<viagem>/<arquivo>.
///
/// O token do OneDrive usa um OAuth separado (escopo com Files.ReadWrite),
/// obtido sob demanda, para nao afetar o login de sincronizacao do BRLog.
class OneDriveBackup {
  static const _graph = 'https://graph.microsoft.com/v1.0';
  // Fragmento múltiplo de 320 KiB (exigência do upload resumível).
  static const _chunk = 3276800;

  static String _upKey(String viagem) => 'ONEDRIVE_UP_$viagem';

  /// Nomes já enviados desta viagem.
  static Future<Set<String>> enviadas(String viagem) async {
    final p = await SharedPreferences.getInstance();
    return (p.getStringList(_upKey(viagem)) ?? []).toSet();
  }

  static Future<void> _marcar(String viagem, String nome) async {
    final p = await SharedPreferences.getInstance();
    final l = (p.getStringList(_upKey(viagem)) ?? []).toSet()..add(nome);
    await p.setStringList(_upKey(viagem), l.toList());
  }

  /// Caminhos ainda não enviados.
  static Future<List<String>> pendentes(
      String viagem, List<String> paths) async {
    final env = await enviadas(viagem);
    return paths
        .where((p) => !env.contains(p.split('/').last))
        .toList();
  }

  /// Token do OneDrive. [interactive] abre a tela de consentimento se
  /// ainda não houver permissão; sem isso, tenta silenciosamente.
  static Future<String?> token({bool interactive = false}) => interactive
      ? BrLogAuth.oneDriveTokenInteractive()
      : BrLogAuth.oneDriveTokenSilent();

  /// Envia uma foto usando o [token] informado. Retorna null em sucesso ou
  /// a mensagem de erro ('auth' = token/permissão inválidos).
  static Future<String?> enviarFotoComToken(
      String viagem, File file, String token) async {
    final nome = file.path.split('/').last;
    try {
      final bytes = await file.readAsBytes();
      final seg = ['Recebimento', viagem, nome]
          .map(Uri.encodeComponent)
          .join('/');
      if (bytes.length < 4 * 1024 * 1024) {
        final r = await http.put(
          Uri.parse('$_graph/me/drive/root:/$seg:/content'),
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/octet-stream',
          },
          body: bytes,
        );
        if (r.statusCode == 401 || r.statusCode == 403) return 'auth';
        if (r.statusCode < 200 || r.statusCode >= 300) {
          return 'HTTP ${r.statusCode}';
        }
      } else {
        final err = await _enviarResumivel(token, seg, bytes);
        if (err != null) return err;
      }
      await _marcar(viagem, nome);
      return null;
    } catch (e) {
      return e.toString();
    }
  }

  static Future<String?> _enviarResumivel(
      String token, String seg, List<int> bytes) async {
    final s = await http.post(
      Uri.parse('$_graph/me/drive/root:/$seg:/createUploadSession'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body:
          '{"item": {"@microsoft.graph.conflictBehavior": "replace"}}',
    );
    if (s.statusCode == 401 || s.statusCode == 403) return 'auth';
    if (s.statusCode < 200 || s.statusCode >= 300) {
      return 'HTTP ${s.statusCode}';
    }
    final map = jsonDecode(s.body);
    final url =
        map is Map ? map['uploadUrl']?.toString() ?? '' : '';
    if (url.isEmpty) return 'sem sessão de upload';
    var offset = 0;
    while (offset < bytes.length) {
      final end = (offset + _chunk < bytes.length)
          ? offset + _chunk
          : bytes.length;
      final r = await http.put(
        Uri.parse(url),
        headers: {
          'Content-Length': '${end - offset}',
          'Content-Range': 'bytes $offset-${end - 1}/${bytes.length}',
        },
        body: bytes.sublist(offset, end),
      );
      if (r.statusCode == 201 || r.statusCode == 200) return null;
      if (r.statusCode != 202) return 'HTTP ${r.statusCode}';
      offset = end;
    }
    return null;
  }
}
