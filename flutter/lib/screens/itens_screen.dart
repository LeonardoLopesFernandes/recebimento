import 'package:flutter/material.dart';
import '../models/recebimento.dart';
import '../utils/constants.dart';
import '../utils/currency_formatter.dart';
import '../utils/excel_downloader.dart';
import '../widgets/item_row.dart';

class ItensScreen extends StatefulWidget {
  const ItensScreen({super.key});

  @override
  State<ItensScreen> createState() => _ItensScreenState();
}

class _ItensScreenState extends State<ItensScreen> {
  String _titulo = '';
  List<RecebimentoItem> _todos = [];
  List<RecebimentoItem> _filtrados = [];
  final _search = TextEditingController();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments
        as Map<String, dynamic>?;
    _titulo = args?['titulo'] ?? 'Itens';
    _todos = (args?['itens'] as List? ?? [])
        .whereType<RecebimentoItem>()
        .toList();
    _filtrados = _todos;
  }

  void _filtrar(String q) {
    final query = q.toLowerCase();
    setState(() {
      _filtrados = _todos.where((i) {
        return i.descricao.toLowerCase().contains(query) ||
            i.idSap.toLowerCase().contains(query) ||
            i.departamento.toLowerCase().contains(query) ||
            i.idEan.toLowerCase().contains(query);
      }).toList();
    });
  }

  double get _soma => _todos.fold(0.0, (s, i) => s + i.preco);

  void _gerarExcel() async {
    if (_todos.isEmpty) {
      _snack("Nenhum item para gerar Excel.");
      return;
    }
    _snack("📥 Gerando Excel...");
    try {
      final path = await ExcelDownloader.gerarXlsxItens(
        viagemId: _titulo,
        prefixo: "itens",
        itens: _todos,
      );
      _snack("✅ Excel salvo: $path");
    } catch (e) {
      _snack("❌ $e");
    }
  }

  void _gerarPdf() async {
    if (_todos.isEmpty) {
      _snack("Nenhum item para gerar PDF.");
      return;
    }
    _snack("📥 Gerando PDF...");
    try {
      final path = await ExcelDownloader.gerarPdfItens(
        titulo: _titulo,
        prefixo: "itens",
        itens: _todos,
        total: _soma,
      );
      _snack("✅ PDF salvo: $path");
    } catch (e) {
      _snack("❌ $e");
    }
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: Column(
        children: [
          // Header
          Container(
            color: const Color(Constants.primaryRed),
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Container(
                        height: 40,
                        alignment: Alignment.center,
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(_titulo,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                                color: Color(Constants.primaryRed),
                                fontSize: 13,
                                fontWeight: FontWeight.bold)),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: InkWell(
                        onTap: _gerarExcel,
                        child: Container(
                          height: 40,
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text('GERAR EXCEL',
                              style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF4CAF50))),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: Container(
                        height: 40,
                        alignment: Alignment.center,
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        decoration: BoxDecoration(
                          color: const Color(Constants.primaryRed),
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: Colors.white),
                        ),
                        child: Text(
                            "Total: ${CurrencyFormatter.formatarMoedaComSimbolo(_soma)}",
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                                color: Colors.white,
                                fontSize: 14,
                                fontWeight: FontWeight.bold)),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: InkWell(
                        onTap: _gerarPdf,
                        child: Container(
                          height: 40,
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text('GERAR PDF',
                              style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: Color(Constants.primaryRed))),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          // Busca
          Container(
            margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: Row(
              children: [
                const Icon(Icons.search, color: Color(0xFFA0AEC0)),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: _search,
                    onChanged: _filtrar,
                    decoration: const InputDecoration(
                      hintText: 'Pesquisar item (nome, SAP ou departamento)',
                      border: InputBorder.none,
                      hintStyle: TextStyle(color: Color(0xFFA0AEC0), fontSize: 14),
                    ),
                  ),
                ),
                if (_search.text.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: () {
                      _search.clear();
                      _filtrar('');
                    },
                  ),
              ],
            ),
          ),
          // Lista
          Expanded(
            child: _filtrados.isEmpty
                ? const Center(child: Text('Nenhum item encontrado'))
                : ListView.builder(
                    padding: const EdgeInsets.all(12),
                    itemCount: _filtrados.length,
                    itemBuilder: (ctx, i) => ItemRow(item: _filtrados[i]),
                  ),
          ),
        ],
      ),
    );
  }
}
