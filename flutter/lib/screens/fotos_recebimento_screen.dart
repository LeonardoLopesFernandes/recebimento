import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../utils/constants.dart';
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
    _carregar();
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
                  onLongPress: () => _excluir(path),
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
