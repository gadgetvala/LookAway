import 'package:flutter/material.dart';

/// Single-choice chip row. With [selected] null, chips act as plain buttons.
class OptionChips<T> extends StatelessWidget {
  final List<T> options;
  final T? selected;
  final String Function(T) label;
  final ValueChanged<T> onSelected;

  const OptionChips({super.key, required this.options, required this.label, required this.onSelected, this.selected});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final option in options)
          ChoiceChip(label: Text(label(option)), selected: option == selected, onSelected: (_) => onSelected(option)),
      ],
    );
  }
}
