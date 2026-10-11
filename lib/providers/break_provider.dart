import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:look_away/models/break_state.dart';
import 'package:look_away/services/break_service.dart';

/// Live timer state from the native service.
final breakStateProvider = StreamProvider<BreakState>((ref) => BreakService.states);
