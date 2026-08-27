import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../utils/constants.dart';
import '../../utils/log_helper.dart';

class TokenInfo {
  final String accessToken;
  final String? refreshToken;
  final int expiresIn;

  TokenInfo({
    required this.accessToken,
    this.refreshToken,
    this.expiresIn = 0,
  });
}

class MicrosoftOAuth {
  static String? ultimoErro;

  static String getAuthorizeUrl({String state = "brlog"}) {
    final params = {
      'client_id': Constants.clientId,
      'response_type': 'code',
      'redirect_uri': Constants.redirectUri,
      'scope': Constants.scopes,
      'response_mode': 'query',
      'state': state,
      'nonce': 'brlog$state',
    };
    final query = params.entries
        .map((e) =>
            '${Uri.encodeComponent(e.key)}=${Uri.encodeComponent(e.value)}')
        .join('&');
    return '${Constants.authorizeUrl}?$query';
  }

  static bool isRedirectUrl(String url) =>
      url.startsWith(Constants.redirectUri);

  static String? extrairCodigo(String url) {
    final match = RegExp(r'[?&]code=([^&]+)').firstMatch(url);
    if (match == null) return null;
    return Uri.decodeComponent(match.group(1)!);
  }

  static String? extrairErro(String url) {
    final match = RegExp(r'[?&]error=([^&]+)').firstMatch(url);
    if (match == null) return null;
    return Uri.decodeComponent(match.group(1)!);
  }

  static Future<TokenInfo?> trocarCodigoPorToken(String accessCode) async {
    final body = {
      'client_id': Constants.clientId,
      'client_secret': Constants.clientSecret,
      'grant_type': 'authorization_code',
      'code': accessCode,
      'redirect_uri': Constants.redirectUri,
      'scope': Constants.scopes,
    };
    return _postToken(body);
  }

  static Future<TokenInfo?> renovarToken(String refreshToken) async {
    final body = {
      'client_id': Constants.clientId,
      'client_secret': Constants.clientSecret,
      'grant_type': 'refresh_token',
      'refresh_token': refreshToken,
      'scope': Constants.scopes,
      'redirect_uri': Constants.redirectUri,
    };
    return _postToken(body);
  }

  static Future<TokenInfo?> _postToken(Map<String, String> body) async {
    try {
      final response = await http.post(
        Uri.parse(Constants.tokenUrl),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: body,
      );
      final respBody = response.body;
      if (response.statusCode < 200 || response.statusCode >= 300) {
        ultimoErro = _extrairDescricaoErro(respBody) ?? 'HTTP ${response.statusCode}';
        LogHelper.e("OAuth BRLog: token HTTP ${response.statusCode} body=$respBody");
        return null;
      }
      final json = jsonDecode(respBody) as Map<String, dynamic>;
      final access = json['access_token'] as String? ?? '';
      if (access.isEmpty) {
        ultimoErro = _extrairDescricaoErro(respBody) ?? 'sem access_token';
        LogHelper.e("OAuth BRLog: token sem access_token body=$respBody");
        return null;
      }
      ultimoErro = null;
      return TokenInfo(
        accessToken: access,
        refreshToken: json['refresh_token'] as String?,
        expiresIn: json['expires_in'] as int? ?? 0,
      );
    } catch (e) {
      ultimoErro = e.toString();
      LogHelper.e("OAuth BRLog: exceção na troca de token", e);
      return null;
    }
  }

  static String? _extrairDescricaoErro(String body) {
    try {
      final json = jsonDecode(body) as Map<String, dynamic>;
      final msg = json['error_description'] as String?;
      return msg?.isNotEmpty == true ? msg : null;
    } catch (_) {
      return null;
    }
  }
}
