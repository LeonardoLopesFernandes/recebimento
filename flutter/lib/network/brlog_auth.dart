import 'package:aad_oauth/aad_oauth.dart';
import 'package:aad_oauth/model/config.dart';
import '../app_navigator.dart';
import '../utils/constants.dart';

/// Resultado do login Microsoft do BRLog.
class BrLogToken {
  final String accessToken;
  final String? refreshToken;

  BrLogToken(this.accessToken, this.refreshToken);
}

/// Login Microsoft do BRLog usando o pacote aad_oauth (mesmo fluxo do app
/// BRLog/delivery_client). Só é chamado no menu "Sincronizar viagens do BRLog".
class BrLogAuth {
  static AadOAuth _oauth() => AadOAuth(
        Config(
          tenant: Constants.tenant,
          clientId: Constants.clientId,
          clientSecret:
              Constants.clientSecret.isEmpty ? null : Constants.clientSecret,
          redirectUri: Constants.redirectUri,
          scope: Constants.brlogScopes,
          responseType: 'code',
          navigatorKey: appNavigatorKey,
        ),
      );

  /// Abre a WebView da Microsoft e retorna o token.
  /// Retorna null se o usuário cancelar ou o login falhar.
  static Future<BrLogToken?> login() async {
    final result = await _oauth().login();
    return result.fold(
      (_) => null,
      (token) {
        final access = token.accessToken;
        if (access == null || access.isEmpty) return null;
        return BrLogToken(access, token.refreshToken);
      },
    );
  }

  /// Access token válido, renovando silenciosamente quando necessário.
  static Future<String?> accessToken() async {
    try {
      return await _oauth().getAccessToken();
    } catch (_) {
      return null;
    }
  }

  static Future<void> logout() async {
    try {
      await _oauth().logout(showWebPopup: false);
    } catch (_) {}
  }
}
