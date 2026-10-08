import 'package:flutter/material.dart';

/// Navigator global usado pelo fluxo OAuth do BRLog (aad_oauth), que
/// precisa de um NavigatorState para abrir/recolher a WebView da Microsoft.
final GlobalKey<NavigatorState> appNavigatorKey = GlobalKey<NavigatorState>();
