import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PastaFotos {
  final String viagem;
  final String data;
  PastaFotos({required this.viagem, required this.data});

  Map<String, dynamic> toJson() => {'viagem': viagem, 'data': data};
  factory PastaFotos.fromJson(Map<String, dynamic> j) =>
      PastaFotos(viagem: j['viagem'] ?? '', data: j['data'] ?? '');
}

/// Armazenamento local simplificado de fotos e pastas (substitui o
/// FotosRecebimentoStore do Android, que usava FileProvider + diretório
/// externo). Aqui usamos o diretório de documentos do app.
class FotosStore {
  static const String _keyPastas = "FOTOS_PASTAS";

  static Future<Directory> pastaDir(String viagem) async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/fotos/$viagem');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  static Future<List<String>> getFotos(String viagem) async {
    try {
      final dir = await pastaDir(viagem);
      final files = dir
          .listSync()
          .whereType<File>()
          .where((f) =>
              f.path.toLowerCase().endsWith('.jpg') ||
              f.path.toLowerCase().endsWith('.png'))
          .map((f) => f.path)
          .toList();
      files.sort((a, b) => b.compareTo(a));
      return files;
    } catch (_) {
      return [];
    }
  }

  static Future<void> adicionarImagem(String viagem, File origem) async {
    final dir = await pastaDir(viagem);
    final nome =
        "foto_${DateTime.now().millisecondsSinceEpoch}_${origem.path.split('/').last}";
    await origem.copy('${dir.path}/$nome');
  }

  static Future<bool> excluirFoto(String viagem, String caminho) async {
    try {
      final f = File(caminho);
      if (await f.exists()) await f.delete();
      return true;
    } catch (_) {
      return false;
    }
  }

  static Future<List<PastaFotos>> getPastas() async {
    final prefs = await SharedPreferences.getInstance();
    final json = prefs.getString(_keyPastas);
    if (json == null) return [];
    try {
      final list = jsonDecode(json) as List;
      return list.map((e) => PastaFotos.fromJson(e)).toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> criarPasta(String viagem, String data) async {
    final prefs = await SharedPreferences.getInstance();
    final pastas = await getPastas();
    if (pastas.any((p) => p.viagem == viagem)) return;
    pastas.add(PastaFotos(viagem: viagem, data: data));
    await prefs.setString(_keyPastas,
        jsonEncode(pastas.map((p) => p.toJson()).toList()));
  }

  static Future<void> renomearPasta(String viagem, String nova) async {
    final prefs = await SharedPreferences.getInstance();
    final pastas = await getPastas();
    final idx = pastas.indexWhere((p) => p.viagem == viagem);
    if (idx >= 0) {
      pastas[idx] = PastaFotos(viagem: nova, data: pastas[idx].data);
      await prefs.setString(_keyPastas,
          jsonEncode(pastas.map((p) => p.toJson()).toList()));
    }
  }

  static Future<void> editarDataPasta(String viagem, String novaData) async {
    final prefs = await SharedPreferences.getInstance();
    final pastas = await getPastas();
    final idx = pastas.indexWhere((p) => p.viagem == viagem);
    if (idx >= 0) {
      pastas[idx] = PastaFotos(viagem: viagem, data: novaData);
      await prefs.setString(_keyPastas,
          jsonEncode(pastas.map((p) => p.toJson()).toList()));
    }
  }

  static Future<void> excluirPasta(String viagem) async {
    final prefs = await SharedPreferences.getInstance();
    final pastas = await getPastas();
    pastas.removeWhere((p) => p.viagem == viagem);
    await prefs.setString(_keyPastas,
        jsonEncode(pastas.map((p) => p.toJson()).toList()));
  }
}
