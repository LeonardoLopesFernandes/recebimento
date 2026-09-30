import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../network/microsoft_oauth.dart';
import '../network/session_manager.dart';

/// Backup das fotos no OneDrive da conta logada (Microsoft Graph),
/// 100% Dart (sem plugin nativo, seguro para patch OTA).
/// Pasta: Recebimento/<viagem>/<arquivo>.
/// Requer consentimento Files.ReadWrite: no primeiro uso após essa
/// mudança, refaça o login (menu BRLog) uma vez.
class OneDriveBackup {
  static const _graph = 'https://graph.microsoft.com/v1.0';
  // Fragmento múltiplo de 320 KiB (exigência do upload resumível).
  static const _chunk = 3276800;

  static Future<String?> _accessToken() async {
    final session = await SessionManager.create();
    final rt = session.getBrlogRefreshToken();
    if (rt == null || rt.isEmpty) return null;
    try {
      final t = await MicrosoftOAuth.renovarToken(rt);
      if (t == null || t.accessToken.isEmpty) return null;
      if (t.refreshToken != null && t.refreshToken!.isNotEmpty) {
        session.saveBrlogRefreshToken(t.refreshToken!);
      }
      return t.accessToken;
    } catch (_) {
      return null;
    }
  }

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

  /// Envia uma foto. Retorna null em sucesso ou a mensagem de erro
  /// ('auth' = sem permissão/sessão: refazer login BRLog).
  static Future<String?> enviarFoto(String viagem, File file) async {
    final token = await _accessToken();
    if (token == null) return 'auth';
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
