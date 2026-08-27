import 'package:flutter/material.dart';
import '../utils/constants.dart';
import '../utils/fotos_store.dart';

class ImagensRecebimentoScreen extends StatefulWidget {
  const ImagensRecebimentoScreen({super.key});

  @override
  State<ImagensRecebimentoScreen> createState() =>
      _ImagensRecebimentoScreenState();
}

class _ImagensRecebimentoScreenState
    extends State<ImagensRecebimentoScreen> {
  List<PastaFotos> _pastas = [];

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    _pastas = await FotosStore.getPastas();
    if (mounted) setState(() {});
  }

  void _abrirPasta(PastaFotos p) {
    Navigator.of(context).pushNamed('/fotos',
        arguments: {'viagem': p.viagem, 'data': p.data});
  }

  void _novaPasta() {
    final viagemCtl = TextEditingController();
    final dataCtl = TextEditingController();
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Nova Pasta'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: viagemCtl,
              decoration: const InputDecoration(labelText: 'Viagem'),
            ),
            TextField(
              controller: dataCtl,
              decoration: const InputDecoration(labelText: 'Data (dd/mm/aaaa)'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('CANCELAR'),
          ),
          TextButton(
            onPressed: () async {
              final v = viagemCtl.text.trim();
              final d = dataCtl.text.trim();
              if (v.isEmpty || d.isEmpty) return;
              await FotosStore.criarPasta(v, d);
              Navigator.of(context).pop();
              _carregar();
              _abrirPasta(PastaFotos(viagem: v, data: d));
            },
            child: const Text('CRIAR'),
          ),
        ],
      ),
    );
  }

  void _opcoes(PastaFotos p) {
    showModalBottomSheet(
      context: context,
      builder: (_) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.edit),
            title: const Text('Renomear pasta'),
            onTap: () async {
              Navigator.of(context).pop();
              final ctl = TextEditingController(text: p.viagem);
              final novo = await showDialog<String>(
                context: context,
                builder: (_) => AlertDialog(
                  title: const Text('Renomear'),
                  content: TextField(controller: ctl),
                  actions: [
                    TextButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: const Text('CANCELAR')),
                    TextButton(
                        onPressed: () =>
                            Navigator.of(context).pop(ctl.text.trim()),
                        child: const Text('SALVAR')),
                  ],
                ),
              );
              if (novo != null && novo.isNotEmpty) {
                await FotosStore.renomearPasta(p.viagem, novo);
                _carregar();
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.delete),
            title: const Text('Excluir'),
            onTap: () async {
              Navigator.of(context).pop();
              await FotosStore.excluirPasta(p.viagem);
              _carregar();
            },
          ),
        ],
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
        title: const Text('Imagens'),
      ),
      body: _pastas.isEmpty
          ? const Center(child: Text('Nenhuma pasta. Crie uma nova.'))
          : ListView.builder(
              itemCount: _pastas.length,
              itemBuilder: (ctx, i) {
                final p = _pastas[i];
                return Card(
                  margin:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  child: ListTile(
                    leading: const Icon(Icons.folder,
                        color: Color(Constants.primaryRed)),
                    title: Text('Viagem ${p.viagem}'),
                    subtitle: Text(p.data),
                    onTap: () => _abrirPasta(p),
                    onLongPress: () => _opcoes(p),
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: const Color(Constants.primaryRed),
        onPressed: _novaPasta,
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }
}
