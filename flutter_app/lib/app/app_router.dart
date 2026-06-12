import 'package:flutter/material.dart';

import '../features/assistant/screens/assistant_home_screen.dart';
import '../features/onboarding/screens/assistant_selection_screen.dart';

class AppRoutes {
  static const String selection = '/';
  static const String assistantHome = '/assistant-home';
}

class AppRouter {
  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case AppRoutes.selection:
        return MaterialPageRoute<void>(
          settings: settings,
          builder: (_) => const AssistantSelectionScreen(),
        );
      case AppRoutes.assistantHome:
        return MaterialPageRoute<void>(
          settings: settings,
          builder: (_) => const AssistantHomeScreen(),
        );
      default:
        return MaterialPageRoute<void>(
          settings: settings,
          builder: (_) => const AssistantSelectionScreen(),
        );
    }
  }
}
