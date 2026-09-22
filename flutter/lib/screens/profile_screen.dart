import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import '../network/session_manager.dart';
import '../screens/login_screen.dart';
import '../utils/constants.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final ImagePicker _picker = ImagePicker();
  File? _fotoPerfil;
  SessionManager? _session;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    final session = await SessionManager.create();
    if (!mounted) return;
    setState(() => _session = session);
    final path = session.getProfilePhoto();
    if (path != null) {
      final f = File(path);
      if (f.existsSync()) setState(() => _fotoPerfil = f);
    }
  }

  Future<void> _escolherFoto() async {
    try {
      final session = _session ?? await SessionManager.create();
      final picked = await _picker.pickImage(
          source: ImageSource.gallery, imageQuality: 70, maxWidth: 1200);
      if (picked == null) return;
      final arquivo = File(picked.path);
      // Copia para diretório permanente do app para persistir após reinicialização
      final dir = await getApplicationDocumentsDirectory();
      final nomeArquivo = 'profile_photo_${DateTime.now().millisecondsSinceEpoch}.jpg';
      final destino = File('${dir.path}/$nomeArquivo');
      await arquivo.copy(destino.path);
      session.saveProfilePhoto(destino.path);
      setState(() => _fotoPerfil = destino);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Foto atualizada com sucesso')),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erro ao escolher foto: $e')),
      );
    }
  }

  void _confirmarSaida() {
    showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: Colors.white,
        title: const Text('Sair da conta'),
        content: const Text('Deseja realmente sair da sua conta?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Não')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Sim')),
        ],
      ),
    ).then((confirmado) async {
      if (confirmado == true) {
        final session = _session ?? await SessionManager.create();
        session.clearAll();
        if (mounted) {
          Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(builder: (_) => const LoginScreen()),
            (_) => false,
          );
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    const brandRed = Color(Constants.primaryRed);
    return Scaffold(
      backgroundColor: const Color(Constants.bgGray),
      appBar: AppBar(
        title: const Center(
          child: Text('Perfil do Usuário',
              style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
        ),
        backgroundColor: brandRed,
        iconTheme: const IconThemeData(color: Colors.white),
        automaticallyImplyLeading: false,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            const SizedBox(height: 16),
            GestureDetector(
              onTap: _escolherFoto,
              child: Stack(
                alignment: Alignment.bottomRight,
                children: [
                  CircleAvatar(
                    radius: 50,
                    backgroundColor: Colors.grey.shade300,
                    backgroundImage: _fotoPerfil != null
                        ? ResizeImage(
                            FileImage(_fotoPerfil!),
                            width: 200,
                            height: 200,
                          )
                        : null,
                    child: _fotoPerfil == null
                        ? const Icon(Icons.person, size: 50, color: Colors.grey)
                        : null,
                  ),
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration:
                        const BoxDecoration(color: brandRed, shape: BoxShape.circle),
                    child: const Icon(Icons.camera_alt,
                        size: 18, color: Colors.white),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            const Text('Toque para alterar a foto',
                style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 24),
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Informações da Conta',
                        style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                            color: brandRed)),
                    const SizedBox(height: 12),
                    FutureBuilder<SessionManager>(
                      future: SessionManager.create(),
                      builder: (context, snapshot) {
                        final session = snapshot.data;
                        final nome = session?.getUserName() ?? 'Usuário';
                        final email = session?.getUserEmail() ?? 'Não informado';
                        final loja = session?.getUserStore() ?? 'L291';
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Nome: $nome', style: const TextStyle(fontSize: 14)),
                            const SizedBox(height: 6),
                            Text('E-mail: $email', style: const TextStyle(fontSize: 14)),
                            const SizedBox(height: 6),
                            Text('Loja: $loja', style: const TextStyle(fontSize: 14)),
                          ],
                        );
                      },
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: _confirmarSaida,
                style: ElevatedButton.styleFrom(
                  backgroundColor: brandRed,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16)),
                ),
                icon: const Icon(Icons.logout),
                label: const Text('Sair da Conta',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}