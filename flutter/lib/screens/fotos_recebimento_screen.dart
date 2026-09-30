import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../utils/constants.dart';
import '../utils/excel_downloader.dart';
import '../utils/fotos_store.dart';

class FotosRecebimentoScreen extends StatefulWidget {
  const FotosRecebimentoScreen({super.key});

  @override
  State<FotosRecebimentoScreen> createState() =>
      _FotosRecebimentoScreenState();
}

class _FotosRecebimentoScreenState extends State<FotosRecebimentoScreen> {
  String _viagem = '';
  String _data = '';
  List<String> _fotos = [];
  final ImagePicker _picker = ImagePicker();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments
        as Map<String, dynamic>?;
    _viagem = args?['viagem'] ?? '';
    _data = args?['data'] ?? '';
    _carregar();
  }

  Future<void> _carregar() async {
    _fotos = await FotosStore.getFotos(_viagem);
    if (mounted) setState(() {});
  }

  Future<void> _daGaleria() async {
    final imgs = await _picker.pickMultiImage();
    for (final img in imgs) {
      await FotosStore.adicionarImagem(_viagem, File(img.path));
    }
    _carregar();
  }

  Future<void> _daCamera() async {
    final img = await _picker.pickImage(source: ImageSource.camera);
    if (img != null) {
      await FotosStore.adicionarImagem(_viagem, File(img.path));
      _carregar();
    }
  }

  void _verFoto(String path) {
    showDialog(
      context: context,
      builder: (_) => Dialog(
        backgroundColor: Colors.transparent,
        child: Stack(
          children: [
            InteractiveViewer(child: Image.file(File(path))),
            Positioned(
              top: 8,
              right: 8,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _excluir(String path) async {
    await FotosStore.excluirFoto(_viagem, path);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Foto excluída')));
    _carregar();
  }

  void _opcoesFoto(String path) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: Colors.white,
        contentPadding: const EdgeInsets.symmetric(vertical: 8),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit,
                  color: Color(Constants.primaryRed)),
              title: const Text('Renomear'),
              onTap: () async {
                Navigator.of(context).pop();
                final atual =
                    path.split('/').last.split('.').first;
                final ctl = TextEditingController(text: atual);
                final novo = await showDialog<String>(
                  context: context,
                  builder: (_) => AlertDialog(
                    backgroundColor: Colors.white,
                    title: const Text('Renomear foto'),
                    content: TextField(
                      controller: ctl,
                      decoration: const InputDecoration(
                          labelText: 'Nome (sem extensão)'),
                    ),
                    actions: [
                      TextButton(
                          onPressed: () =>
                              Navigator.of(context).pop(),
                          child: const Text('CANCELAR')),
                      TextButton(
                          onPressed: () => Navigator.of(context)
                              .pop(ctl.text.trim()),
                          child: const Text('SALVAR')),
                    ],
                  ),
                );
                if (novo != null && novo.isNotEmpty) {
                  final ok = await FotosStore.renomearFoto(
                      _viagem, path, novo);
                  if (!mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text(ok
                          ? 'Foto renomeada'
                          : 'Não foi possível renomear')));
                  _carregar();
                }
              },
            ),
            ListTile(
              leading:
                  const Icon(Icons.delete, color: Colors.red),
              title: const Text('Excluir'),
              onTap: () {
                Navigator.of(context).pop();
                _excluir(path);
              },
            ),
            ListTile(
              leading: const Icon(Icons.share,
                  color: Color(Constants.primaryRed)),
              title: const Text('Compartilhar'),
              onTap: () async {
                Navigator.of(context).pop();
                ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                        content: Text('Salvando em Downloads...')));
                try {
                  final bytes = await File(path).readAsBytes();
                  final nome = path.split('/').last;
                  final ext = nome.split('.').last.toLowerCase();
                  final mime = ext == 'png'
                      ? 'image/png'
                      : 'image/jpeg';
                  final destino =
                      await ExcelDownloader.salvarArquivoEmDownloads(
                    nome: nome,
                    mimeType: mime,
                    bytes: bytes,
                  );
                  if (!mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text('Imagem salva: $destino')));
                } catch (e) {
                  if (!mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('❌ $e')));
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(Constants.bgGray),
      appBar: AppBar(
        backgroundColor: const Color(Constants.primaryRed),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Viagem $_viagem',
                style: const TextStyle(color: Colors.white)),
            Text(_data.isEmpty ? 'Data não informada' : _data,
                style: const TextStyle(color: Colors.white70, fontSize: 12)),
          ],
        ),
      ),
      body: _fotos.isEmpty
          ? const Center(child: Text('Nenhuma foto nesta viagem'))
          : GridView.builder(
              padding: const EdgeInsets.all(8),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 4,
                mainAxisSpacing: 4,
              ),
              itemCount: _fotos.length,
              itemBuilder: (ctx, i) {
                final path = _fotos[i];
                return GestureDetector(
                  onTap: () => _verFoto(path),
                  onLongPress: () => _opcoesFoto(path),
                  child: Image.file(File(path), fit: BoxFit.cover),
                );
              },
            ),
      floatingActionButton: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton(
            heroTag: 'cam',
            backgroundColor: const Color(Constants.primaryRed),
            onPressed: _daCamera,
            child: const Icon(Icons.camera_alt, color: Colors.white),
          ),
          const SizedBox(height: 12),
          FloatingActionButton(
            heroTag: 'gal',
            backgroundColor: const Color(Constants.primaryRed),
            onPressed: _daGaleria,
            child: const Icon(Icons.photo_library, color: Colors.white),
          ),
        ],
      ),
    );
  }
}
