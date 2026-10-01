import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:share_plus/share_plus.dart';
import '../utils/constants.dart';
import '../utils/excel_downloader.dart';
import '../utils/fotos_store.dart';
import '../utils/onedrive_backup.dart';

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
  bool _modoSelecao = false;
  final Set<String> _selecionadas = {};

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

  bool _emLista = false;

  String _tamanho(String path) {
    try {
      final b = File(path).lengthSync();
      if (b < 1024) return '$b B';
      if (b < 1024 * 1024) {
        return '${(b / 1024).toStringAsFixed(1).replaceAll('.', ',')} KB';
      }
      return '${(b / (1024 * 1024)).toStringAsFixed(1).replaceAll('.', ',')} MB';
    } catch (_) {
      return '';
    }
  }

  Future<void> _daGaleria() async {
    final imgs = await _picker.pickMultiImage();
    final antes = _fotos.toSet();
    for (final img in imgs) {
      await FotosStore.adicionarImagem(_viagem, File(img.path));
    }
    await _carregar();
    _backupNovas(antes);
  }

  Future<void> _daCamera() async {
    final img = await _picker.pickImage(source: ImageSource.camera);
    if (img != null) {
      final antes = _fotos.toSet();
      await FotosStore.adicionarImagem(_viagem, File(img.path));
      await _carregar();
      _backupNovas(antes);
    }
  }

  /// Sobe ao OneDrive só as fotos recém-adicionadas (sem bloquear a UI).
  void _backupNovas(Set<String> antes) async {
    final novas = _fotos.where((p) => !antes.contains(p)).toList();
    for (final n in novas) {
      final err = await OneDriveBackup.enviarFoto(_viagem, File(n));
      if (!mounted) return;
      if (err != null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(err == 'auth'
                ? 'Foto salva local. Para o OneDrive, refaça o login (menu BRLog).'
                : 'Foto salva local. Falha no backup: $err')));
        return;
      }
    }
  }

  Future<void> _backupPasta() async {
    final pend =
        await OneDriveBackup.pendentes(_viagem, _fotos);
    if (!mounted) return;
    if (pend.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Tudo já está no OneDrive')));
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Enviando ${pend.length} foto(s)...')));
    var ok = 0;
    String? lastErr;
    for (final p in pend) {
      final err = await OneDriveBackup.enviarFoto(_viagem, File(p));
      if (!mounted) return;
      if (err == null) {
        ok++;
      } else {
        lastErr = err;
      }
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(lastErr == 'auth'
            ? 'Sem permissão no OneDrive: entre novamente pelo menu BRLog.'
            : (ok == pend.length
                ? 'Backup concluído: $ok foto(s)'
                : 'Enviadas $ok/${pend.length} ($lastErr)'))));
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

  void _alternarSelecao(String path) {
    setState(() {
      if (_selecionadas.contains(path)) {
        _selecionadas.remove(path);
      } else {
        _selecionadas.add(path);
      }
    });
  }

  Future<void> _compartilharSelecionadas() async {
    final files = _selecionadas.map((p) => XFile(p)).toList();
    if (files.isEmpty) return;
    try {
      await Share.shareXFiles(files, text: 'Fotos da viagem $_viagem');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Não foi possível compartilhar: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(Constants.bgGray),
      appBar: AppBar(
        backgroundColor: const Color(Constants.primaryRed),
        leading: IconButton(
          icon: Icon(
            _modoSelecao ? Icons.close : Icons.arrow_back,
            color: Colors.white,
          ),
          onPressed: () {
            if (_modoSelecao) {
              setState(() {
                _modoSelecao = false;
                _selecionadas.clear();
              });
            } else {
              Navigator.of(context).pop();
            }
          },
        ),
        actions: _modoSelecao
            ? [
                IconButton(
                  icon: const Icon(Icons.share, color: Colors.white),
                  tooltip: 'Compartilhar selecionadas',
                  onPressed: _selecionadas.isEmpty
                      ? null
                      : _compartilharSelecionadas,
                ),
              ]
            : [
                IconButton(
                  icon: const Icon(Icons.checklist, color: Colors.white),
                  tooltip: 'Selecionar',
                  onPressed: () => setState(() => _modoSelecao = true),
                ),
                IconButton(
                  icon: Icon(
                      _emLista ? Icons.grid_view : Icons.view_list,
                      color: Colors.white),
                  tooltip: _emLista ? 'Ver em grade' : 'Ver em lista',
                  onPressed: () => setState(() => _emLista = !_emLista),
                ),
                IconButton(
                  icon: const Icon(Icons.cloud_upload_outlined,
                      color: Colors.white),
                  tooltip: 'Backup OneDrive',
                  onPressed: _backupPasta,
                ),
              ],
        title: _modoSelecao
            ? Text('${_selecionadas.length} selecionada(s)',
                style: const TextStyle(color: Colors.white))
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Viagem $_viagem',
                      style: const TextStyle(color: Colors.white)),
                  Text(_data.isEmpty ? 'Data não informada' : _data,
                      style: const TextStyle(
                          color: Colors.white70, fontSize: 12)),
                ],
              ),
      ),
      body: _fotos.isEmpty
          ? const Center(child: Text('Nenhuma foto nesta viagem'))
          : _emLista
              ? ListView.builder(
                  padding:
                      const EdgeInsets.symmetric(vertical: 8),
                  itemCount: _fotos.length,
                  itemBuilder: (ctx, i) {
                    final path = _fotos[i];
                    final nome = path.split('/').last;
                    return Card(
                      color: _selecionadas.contains(path)
                          ? const Color(0xFFFFEBEE)
                          : null,
                      margin: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 4),
                      child: ListTile(
                        leading: ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: Image.file(File(path),
                              width: 56,
                              height: 56,
                              fit: BoxFit.cover),
                        ),
                        title: Text(
                          nome,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600),
                        ),
                        subtitle: Text(
                          _tamanho(path),
                          style: const TextStyle(
                              fontSize: 12, color: Colors.grey),
                        ),
                        trailing: _modoSelecao
                            ? Icon(
                                _selecionadas.contains(path)
                                    ? Icons.check_circle
                                    : Icons.radio_button_unchecked,
                                color: _selecionadas.contains(path)
                                    ? const Color(Constants.primaryRed)
                                    : Colors.grey,
                              )
                            : const Icon(Icons.chevron_right,
                                color: Colors.grey),
                        onTap: () => _modoSelecao
                            ? _alternarSelecao(path)
                            : _verFoto(path),
                        onLongPress: _modoSelecao
                            ? null
                            : () => _opcoesFoto(path),
                      ),
                    );
                  },
                )
              : GridView.builder(
              padding: const EdgeInsets.all(8),
              gridDelegate:
                  const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 4,
                mainAxisSpacing: 4,
                childAspectRatio: 0.82,
              ),
              itemCount: _fotos.length,
              itemBuilder: (ctx, i) {
                final path = _fotos[i];
                final nome = path.split('/').last;
                return GestureDetector(
                  onTap: () =>
                      _modoSelecao ? _alternarSelecao(path) : _verFoto(path),
                  onLongPress:
                      _modoSelecao ? null : () => _opcoesFoto(path),
                  child: Stack(
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Expanded(
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(6),
                              child: Image.file(File(path),
                                  fit: BoxFit.cover),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            nome,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                                fontSize: 10, color: Color(0xFF4A5568)),
                          ),
                        ],
                      ),
                      if (_selecionadas.contains(path))
                        Positioned.fill(
                          child: Container(
                            decoration: BoxDecoration(
                              color: Colors.black38,
                              borderRadius: BorderRadius.circular(6),
                            ),
                            alignment: Alignment.center,
                            child: const Icon(Icons.check_circle,
                                color: Colors.white, size: 32),
                          ),
                        ),
                    ],
                  ),
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
