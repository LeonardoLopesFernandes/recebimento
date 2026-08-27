import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/recebimento.dart';
import '../network/session_manager.dart';
import '../providers/main_provider.dart';
import '../utils/constants.dart';
import '../utils/excel_downloader.dart';
import '../widgets/trip_card.dart';
import '../widgets/dialog_protocolo_sucesso.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<SessionManager>(
      future: SessionManager.create(),
      builder: (context, snap) {
        if (!snap.hasData) {
          return const Scaffold(
            backgroundColor: Color(Constants.primaryRed),
            body: Center(
                child: CircularProgressIndicator(color: Colors.white)),
          );
        }
        return ChangeNotifierProvider(
          create: (_) => MainProvider(snap.data!),
          child: const _HomeBody(),
        );
      },
    );
  }
}

class _HomeBody extends StatelessWidget {
  const _HomeBody();

  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<MainProvider>(context);
    return Scaffold(
      backgroundColor: const Color(Constants.bgGray),
      body: Column(
        children: [
          _Header(),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async => provider.refresh(),
              child: provider.items.isEmpty && !provider.isLoading
                  ? _EmptyState()
                  : ListView.builder(
                      itemCount: provider.items.length + 1,
                      itemBuilder: (ctx, i) {
                        if (i == provider.items.length) {
                          if (provider.isLoading) {
                            return const Padding(
                              padding: EdgeInsets.all(16),
                              child: Center(
                                  child: CircularProgressIndicator()),
                            );
                          }
                          return const SizedBox.shrink();
                        }
                        final r = provider.items[i];
                        final numero = r.id.length > 7
                            ? r.id.substring(r.id.length - 7)
                            : r.id;
                        return TripCard(
                          recebimento: r,
                          progresso: provider.progressoViagem[numero],
                          onVisualizar: () => Navigator.of(context)
                              .pushNamed('/detalhes', arguments: r.id),
                          onImprimir: () => _gerarExcel(context, r),
                          onReceber: () => _gerarProtocolo(context, r),
                        );
                      },
                    ),
            ),
          ),
        ],
      ),
    );
  }

  void _gerarExcel(BuildContext context, Recebimento r) async {
    final provider = Provider.of<MainProvider>(context, listen: false);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(
            "📥 Gerando Excel da viagem ${r.id.length > 7 ? r.id.substring(r.id.length - 7) : r.id}...")));
    try {
      final path = await ExcelDownloader.gerarExcel(
        apiService: provider.apiService,
        storeId: provider.storeId,
        viagemId: r.id,
      );
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text("✅ Excel salvo: $path")));
    } catch (e) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text("❌ $e")));
    }
  }

  void _gerarProtocolo(BuildContext context, Recebimento r) async {
    final provider = Provider.of<MainProvider>(context, listen: false);
    try {
      final protocolo = await provider.gerarProtocolo(r);
      showDialog(
        context: context,
        builder: (_) => DialogProtocoloSucesso(protocolo: protocolo),
      );
    } catch (e) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text("❌ $e")));
    }
  }
}

class _Header extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<MainProvider>(context);
    return Container(
      color: const Color(Constants.primaryRed),
      padding: const EdgeInsets.only(top: 8, left: 20, right: 20),
      child: Column(
        children: [
          SizedBox(
            height: 120 - 30,
            child: Row(
              children: [
                InkWell(
                  onTap: () => _abrirMenu(context),
                  child: Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.2),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.local_shipping,
                        color: Colors.white, size: 26),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Text('Recebimento Centralizado',
                          style: TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.bold)),
                      Text(provider.subtitle,
                          style: const TextStyle(
                              color: Color(0xB3FFFFFF),
                              fontSize: 12,
                              fontWeight: FontWeight.bold)),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.settings,
                      color: Colors.white, size: 28),
                  onPressed: () {
                    provider.clearSession();
                    Navigator.of(context).pushReplacementNamed('/login');
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          _Badges(),
          const SizedBox(height: 10),
          if (provider.showSearch) _BuscaFiltros(),
        ],
      ),
    );
  }

  void _abrirMenu(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (ctx) => _MenuBottomSheet(),
    );
  }
}

class _Badges extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<MainProvider>(context);
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [
          BoxShadow(color: Colors.black12, blurRadius: 3, offset: Offset(0, 2)),
        ],
      ),
      child: Row(
        children: [
          _Badge(
            label: 'A Receber',
            count: provider.qtdReceber,
            icon: 'assets/drawables/pendente.png',
            selected: provider.currentStatus == Constants.statusPendente,
            lineColor: const Color(Constants.badgeAmber),
            onTap: () => provider.selectTab(Constants.statusPendente),
          ),
          _Badge(
            label: 'Anomalias',
            count: provider.qtdAnomalia,
            icon: 'assets/drawables/anomalia.png',
            selected: provider.currentStatus == Constants.statusAnomalia,
            lineColor: const Color(Constants.badgeAmber),
            onTap: () => provider.selectTab(Constants.statusAnomalia),
          ),
          _Badge(
            label: 'Erro',
            count: provider.qtdErro,
            icon: 'assets/drawables/erro.png',
            selected: provider.currentStatus == Constants.statusErro,
            lineColor: const Color(Constants.badgeRed),
            onTap: () => provider.selectTab(Constants.statusErro),
          ),
          _Badge(
            label: 'Recebidas',
            count: provider.qtdRecebidas,
            icon: 'assets/drawables/recebidas.png',
            selected: provider.currentStatus == Constants.statusRecebido,
            lineColor: const Color(Constants.badgeGreen),
            onTap: () => provider.selectTab(Constants.statusRecebido),
          ),
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  final String label;
  final String count;
  final String icon;
  final bool selected;
  final Color lineColor;
  final VoidCallback onTap;

  const _Badge({
    required this.label,
    required this.count,
    required this.icon,
    required this.selected,
    required this.lineColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Column(
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Image.asset(icon,
                    width: 32,
                    height: 32,
                    errorBuilder: (_, __, ___) =>
                        const Icon(Icons.receipt, size: 32)),
                Positioned(
                  top: -6,
                  right: -6,
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: lineColor,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(count,
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.bold)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(label,
                style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: Color(Constants.textDark))),
            Container(
              margin: const EdgeInsets.only(top: 4, left: 20, right: 20),
              height: 3,
              color: selected ? lineColor : lineColor.withOpacity(0.3),
            ),
          ],
        ),
      ),
    );
  }
}

class _BuscaFiltros extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<MainProvider>(context);
    return Column(
      children: [
        Container(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              const Icon(Icons.search, color: Color(0xFFA0AEC0)),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  onChanged: provider.setSearch,
                  decoration: const InputDecoration(
                    hintText: 'Pesquisar Viagem, Placa...',
                    border: InputBorder.none,
                    hintStyle: TextStyle(color: Color(0xFFA0AEC0)),
                  ),
                ),
              ),
            ],
          ),
        ),
        Container(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              const Text('Ordenar:',
                  style: TextStyle(
                      fontSize: 11, fontWeight: FontWeight.bold)),
              const SizedBox(width: 8),
              _Pill(
                text: 'MAIS RECENTES',
                ativo: provider.currentSort == 'desc',
                onTap: () => provider.setSort('desc'),
              ),
              const SizedBox(width: 6),
              _Pill(
                text: 'MAIS ANTIGAS',
                ativo: provider.currentSort == 'asc',
                onTap: () => provider.setSort('asc'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Pill extends StatelessWidget {
  final String text;
  final bool ativo;
  final VoidCallback onTap;

  const _Pill({required this.text, required this.ativo, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: ativo ? const Color(Constants.primaryRed) : Colors.grey[300],
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(text,
            style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.bold,
                color: ativo ? Colors.white : const Color(Constants.textDark))),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.inbox, size: 80, color: Color(0xFFCCCCCC)),
          const SizedBox(height: 24),
          const Text('Nenhuma viagem encontrada',
              style: TextStyle(
                  fontSize: 16, fontWeight: FontWeight.bold, color: Color(Constants.textDark))),
          const SizedBox(height: 4),
          Text('Puxe para baixo para recarregar',
              style: TextStyle(fontSize: 14, color: Colors.grey[600])),
        ],
      ),
    );
  }
}

class _MenuBottomSheet extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<MainProvider>(context, listen: false);
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _MenuItem(Icons.sync, 'Sincronizar BRLog', () {
            Navigator.of(context).pop();
            Navigator.of(context)
                .pushNamed('/login_webview', arguments: {'oauthOnly': true});
          }),
          _MenuItem(Icons.image, 'Imagens', () {
            Navigator.of(context).pop();
            Navigator.of(context).pushNamed('/imagens');
          }),
          _MenuItem(Icons.inbox, 'Receber', () {
            Navigator.of(context).pop();
            provider.selectTab(Constants.statusPendente);
          }),
          _MenuItem(Icons.check_circle, 'Recebidas', () {
            Navigator.of(context).pop();
            provider.selectTab(Constants.statusRecebido);
          }),
          _MenuItem(Icons.warning, 'Anomalia', () {
            Navigator.of(context).pop();
            provider.selectTab(Constants.statusAnomalia);
          }),
          _MenuItem(Icons.error, 'Erro', () {
            Navigator.of(context).pop();
            provider.selectTab(Constants.statusErro);
          }),
        ],
      ),
    );
  }
}

class _MenuItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _MenuItem(this.icon, this.label, this.onTap);

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: const Color(Constants.primaryRed)),
      title: Text(label),
      onTap: onTap,
    );
  }
}
