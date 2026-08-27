import 'package:flutter/material.dart';
import '../models/recebimento.dart';
import '../utils/constants.dart';
import '../utils/currency_formatter.dart';

class TripCard extends StatelessWidget {
  final Recebimento recebimento;
  final double? progresso;
  final VoidCallback? onVisualizar;
  final VoidCallback? onImprimir;
  final VoidCallback? onReceber;
  final bool compact;

  const TripCard({
    super.key,
    required this.recebimento,
    this.progresso,
    this.onVisualizar,
    this.onImprimir,
    this.onReceber,
    this.compact = false,
  });

  bool get _isPendente =>
      recebimento.status.toLowerCase() == Constants.statusPendente;

  int get _ano {
    // Tenta extrair o ano de viagem_data (formato ISO).
    try {
      final s = recebimento.viagemData;
      if (s.length >= 4) return int.parse(s.substring(0, 4));
    } catch (_) {}
    return 2025;
  }

  @override
  Widget build(BuildContext context) {
    if (compact) return _buildCompact();
    final numeroExibicao = recebimento.id.length > 7
        ? recebimento.id.substring(recebimento.id.length - 7)
        : recebimento.id;
    final is2026 = _ano >= 2026;
    final temProtocolo = recebimento.protocolo != null &&
        recebimento.protocolo!.isNotEmpty;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [
          BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2)),
        ],
      ),
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          // Header
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Viagem',
                        style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF9E9E9E))),
                    const SizedBox(height: 2),
                    Text(numeroExibicao,
                        style: const TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: Color(Constants.textDark))),
                    if (temProtocolo)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Text(
                          "Protocolo: ${recebimento.protocolo}",
                          style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: Color(Constants.primaryRed)),
                        ),
                      ),
                  ],
                ),
              ),
              InkWell(
                onTap: onImprimir,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 12, vertical: 8),
                  child: Row(
                    children: [
                      Image.asset('assets/drawables/printer.png',
                          width: 20, height: 20, errorBuilder: (_, __, ___) =>
                          const Icon(Icons.print, color: Color(Constants.primaryRed))),
                      const SizedBox(width: 6),
                      const Text('IMPRIMIR',
                          style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: Color(Constants.primaryRed))),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 28, color: Color(0xFFF0F0F0)),
          // Progresso
          if (_isPendente && progresso != null)
            Container(
              margin: const EdgeInsets.only(bottom: 16),
              child: Row(
                children: [
                  const Expanded(
                    child: Text('Progresso da viagem',
                        style: TextStyle(fontSize: 12, color: Color(0xFF9E9E9E))),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(Constants.primaryRed),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      "${((progresso! > 1 ? progresso! / 100 : progresso!) * 100).toInt()}%",
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 14),
                    ),
                  ),
                ],
              ),
            ),
          // Detalhes 4 colunas
          Row(
            children: [
              _coluna("Data", _formatarData()),
              _coluna("Origem", recebimento.codigoOrigem),
              _coluna("Placa", recebimento.placaVeiculo ?? '-'),
              _coluna(
                is2026 ? "Roll" : "GUIA",
                is2026
                    ? "${CurrencyFormatter.formatarInteiro(recebimento.qtdRolls)} Rolls"
                    : "${CurrencyFormatter.formatarInteiro(recebimento.qtdGuias)} Guias",
                cor: const Color(Constants.primaryRed),
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Ações
          Row(
            children: [
              Expanded(
                child: InkWell(
                  onTap: onVisualizar,
                  child: Container(
                    height: 48,
                    decoration: BoxDecoration(
                      border: Border.all(
                          color: const Color(Constants.primaryRed)),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Center(
                      child: Text('VISUALIZAR',
                          style: TextStyle(
                              color: Color(Constants.primaryRed),
                              fontWeight: FontWeight.bold)),
                    ),
                  ),
                ),
              ),
              if (_isPendente) ...[
                const SizedBox(width: 8),
                Expanded(
                  child: InkWell(
                    onTap: onReceber,
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: const Color(Constants.primaryRed),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Center(
                        child: Text('RECEBER',
                            style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildCompact() {
    final numeroExibicao = recebimento.id.length > 7
        ? recebimento.id.substring(recebimento.id.length - 7)
        : recebimento.id;
    final is2026 = _ano >= 2026;
    final temProtocolo = recebimento.protocolo != null &&
        recebimento.protocolo!.isNotEmpty;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [
          BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2)),
        ],
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Viagem',
                        style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF9E9E9E))),
                    const SizedBox(height: 2),
                    Text(numeroExibicao,
                        style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Color(Constants.textDark))),
                    if (temProtocolo)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Text(
                          "Protocolo: ${recebimento.protocolo}",
                          style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: Color(Constants.primaryRed)),
                        ),
                      ),
                  ],
                ),
              ),
              InkWell(
                onTap: onImprimir,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 6, vertical: 4),
                  child: Row(
                    children: [
                      Image.asset('assets/drawables/printer.png',
                          width: 18, height: 18, errorBuilder: (_, __, ___) =>
                          const Icon(Icons.print, color: Color(Constants.primaryRed))),
                      const SizedBox(width: 4),
                      const Text('IMPRIMIR',
                          style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: Color(Constants.primaryRed))),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (_isPendente && progresso != null)
            Container(
              margin: const EdgeInsets.only(bottom: 12),
              child: Row(
                children: [
                  const Expanded(
                    child: Text('Progresso da viagem',
                        style: TextStyle(fontSize: 11, color: Color(0xFF9E9E9E))),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: const Color(Constants.primaryRed),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      "${((progresso! > 1 ? progresso! / 100 : progresso!) * 100).toInt()}%",
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          Row(
            children: [
              _coluna("Data", _formatarData()),
              _coluna("Origem", recebimento.codigoOrigem),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _coluna("Placa", recebimento.placaVeiculo ?? '-'),
              _coluna(
                is2026 ? "Roll" : "GUIA",
                is2026
                    ? "${CurrencyFormatter.formatarInteiro(recebimento.qtdRolls)} Rolls"
                    : "${CurrencyFormatter.formatarInteiro(recebimento.qtdGuias)} Guias",
                cor: const Color(Constants.primaryRed),
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 44,
            child: InkWell(
              onTap: onVisualizar,
              child: Container(
                decoration: BoxDecoration(
                  border: Border.all(color: const Color(Constants.primaryRed)),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Center(
                  child: Text('VISUALIZAR',
                      style: TextStyle(
                          color: Color(Constants.primaryRed),
                          fontWeight: FontWeight.bold)),
                ),
              ),
            ),
          ),
          if (_isPendente) ...[
            const SizedBox(height: 8),
            SizedBox(
              height: 44,
              child: InkWell(
                onTap: onReceber,
                child: Container(
                  decoration: BoxDecoration(
                    color: const Color(Constants.primaryRed),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Center(
                    child: Text('RECEBER',
                        style: TextStyle(
                            color: Colors.white, fontWeight: FontWeight.bold)),
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _coluna(String label, String valor, {Color cor = const Color(Constants.textDark)}) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF9E9E9E))),
          const SizedBox(height: 4),
          Text(valor,
              style: TextStyle(
                  fontSize: 14, fontWeight: FontWeight.bold, color: cor)),
        ],
      ),
    );
  }

  String _formatarData() {
    try {
      final data = DateTime.parse(
          recebimento.viagemData.replaceAll('Z', '').replaceAll('T', ' '));
      return "${data.day.toString().padLeft(2, '0')}/"
          "${data.month.toString().padLeft(2, '0')}/"
          "${data.year}";
    } catch (_) {
      return recebimento.viagemData.length >= 10
          ? recebimento.viagemData.substring(0, 10)
          : recebimento.viagemData;
    }
  }
}
