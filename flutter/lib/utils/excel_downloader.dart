import 'dart:io';
import 'package:excel/excel.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart' as pdf;
import 'package:pdf/widgets.dart' as pw;
import '../models/recebimento.dart';
import '../utils/currency_formatter.dart';
import '../network/api_service.dart';

class ExcelDownloader {
  /// Baixa o Excel da viagem (streaming) e salva no diretório do app.
  static Future<String> gerarExcel({
    required ApiService apiService,
    required String storeId,
    required String viagemId,
  }) async {
    final bytes = await apiService.gerarExcelViagem(storeId, viagemId);
    final dir = await getApplicationDocumentsDirectory();
    final nome = "viagem_${_short(viagemId)}_${_timestamp()}.xlsx";
    final file = File('${dir.path}/$nome');
    await file.writeAsBytes(bytes);
    return file.path;
  }

  /// Gera um xlsx local a partir da lista de itens.
  /// Colunas: DEP, SAP, DESCRIÇÃO, QDTE REAL, CONTAGEM (vazia p/ conferência).
  static Future<String> gerarXlsxItens({
    required String viagemId,
    required String prefixo,
    required List<RecebimentoItem> itens,
  }) async {
    final excel = Excel.createExcel();
    final sheet = excel['Itens'];
    sheet.appendRow([
      TextCellValue('DEP'),
      TextCellValue('SAP'),
      TextCellValue('DESCRIÇÃO'),
      TextCellValue('QDTE REAL'),
      TextCellValue('CONTAGEM'),
    ]);
    for (final item in itens) {
      sheet.appendRow([
        TextCellValue(item.departamento),
        TextCellValue(item.idSap),
        TextCellValue(item.descricao),
        TextCellValue(item.quantidade.toString()),
        TextCellValue(''),
      ]);
    }
    final bytes = excel.save();
    final dir = await getApplicationDocumentsDirectory();
    final nome = "${prefixo}_${_short(viagemId)}_${_timestamp()}.xlsx";
    final file = File('${dir.path}/$nome');
    await file.writeAsBytes(bytes ?? []);
    return file.path;
  }

  /// Gera um PDF local a partir da lista de itens.
  /// Colunas: DEP, SAP, DESCRIÇÃO, QTD, CONFERÊNCIA.
  /// Título = título recebido; subtítulo "Total: R$ 1.234,56" (pt-BR).
  static Future<String> gerarPdfItens({
    required String titulo,
    required String prefixo,
    required List<RecebimentoItem> itens,
    required double total,
  }) async {
    final doc = pw.Document();
    final headers = ['DEP', 'SAP', 'DESCRIÇÃO', 'QTD', 'CONFERÊNCIA'];
    doc.addPage(
      pw.MultiPage(
        pageFormat: pdf.PdfPageFormat.a4,
        build: (context) => [
          pw.Header(
            level: 0,
            child: pw.Text(titulo,
                style: pw.TextStyle(
                    fontSize: 16, fontWeight: pw.FontWeight.bold)),
          ),
          pw.Paragraph(
            text: "Total: ${CurrencyFormatter.formatarMoedaComSimbolo(total)}",
            style: pw.TextStyle(
                fontSize: 12, fontWeight: pw.FontWeight.bold),
          ),
          pw.SizedBox(height: 8),
          pw.Table.fromTextArray(
            context: context,
            headers: headers,
            headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold, color: pdf.PdfColors.white),
            headerDecoration:
                const pw.BoxDecoration(color: pdf.PdfColors.red),
            cellAlignment: pw.Alignment.centerLeft,
            data: itens.map((item) {
              final sap = int.tryParse(item.idSap)?.toString() ?? item.idSap;
              return [
                item.departamento,
                sap,
                item.descricao,
                item.quantidade.toString(),
                '',
              ];
            }).toList(),
          ),
        ],
      ),
    );
    final dir = await getApplicationDocumentsDirectory();
    final nome = "${prefixo}_${_short(titulo)}_${_timestamp()}.pdf";
    final file = File('${dir.path}/$nome');
    await file.writeAsBytes(await doc.save());
    return file.path;
  }

  static String _short(String v) => v.length > 7 ? v.substring(v.length - 7) : v;

  static String _timestamp() {
    final f = DateFormat('yyyyMMdd_HHmmss');
    return f.format(DateTime.now());
  }
}
