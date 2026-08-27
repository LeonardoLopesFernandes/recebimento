import 'package:flutter/material.dart';
import 'utils/constants.dart';
import 'network/session_manager.dart';
import 'screens/login_screen.dart';
import 'screens/login_webview_screen.dart';
import 'screens/home_screen.dart';
import 'screens/detalhes_viagem_screen.dart';
import 'screens/itens_screen.dart';
import 'screens/fotos_recebimento_screen.dart';
import 'screens/imagens_recebimento_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Recebimento',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primaryColor: const Color(Constants.primaryRed),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(Constants.primaryRed),
          primary: const Color(Constants.primaryRed),
        ),
        scaffoldBackgroundColor: const Color(Constants.bgGray),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(Constants.primaryRed),
          foregroundColor: Colors.white,
        ),
        useMaterial3: true,
      ),
      initialRoute: '/',
      routes: {
        '/': (ctx) => const SplashDecider(),
        '/login': (ctx) => const LoginScreen(),
        '/login_webview': (ctx) => const LoginWebViewScreen(),
        '/home': (ctx) => const HomeScreen(),
        '/detalhes': (ctx) => const DetalhesViagemScreen(),
        '/itens': (ctx) => const ItensScreen(),
        '/fotos': (ctx) => const FotosRecebimentoScreen(),
        '/imagens': (ctx) => const ImagensRecebimentoScreen(),
      },
    );
  }
}

/// Decide a tela inicial com base na sessão (fiel ao LoginActivity).
class SplashDecider extends StatefulWidget {
  const SplashDecider({super.key});

  @override
  State<SplashDecider> createState() => _SplashDeciderState();
}

class _SplashDeciderState extends State<SplashDecider> {
  @override
  void initState() {
    super.initState();
    _decide();
  }

  Future<void> _decide() async {
    final session = await SessionManager.create();
    if (session.isLoggedIn()) {
      Navigator.of(context).pushReplacementNamed('/home');
    } else {
      Navigator.of(context).pushReplacementNamed('/login');
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: Color(Constants.primaryRed),
      body: Center(
        child: CircularProgressIndicator(color: Colors.white),
      ),
    );
  }
}
