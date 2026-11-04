import React from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import { colors } from '@/components/ui';
import { BehaviorFieldProps } from '@behaviorguard/mobile-sdk/react';

/**
 * Поле ввода с подключением к SDK. Чувствительные поля не анализируются вообще.
 * SDK видит только длины строк, сам текст не сохраняется.
 */
export function Field({
  label,
  value,
  onChange,
  bg,
  placeholder,
  keyboard = 'default',
  hint,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  bg: BehaviorFieldProps;
  placeholder?: string;
  keyboard?: 'default' | 'number-pad' | 'phone-pad';
  hint?: string;
}) {
  return (
    <View style={{ marginBottom: 10 }}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        placeholder={placeholder}
        placeholderTextColor="#9AA5B1"
        keyboardType={keyboard}
        autoCorrect={false}
        onChangeText={(next) => {
          bg.onChangeText(value, next);
          onChange(next);
        }}
        onBlur={bg.onBlur}
        accessibilityLabel={label}
      />
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  label: { fontSize: 13, color: colors.muted, marginBottom: 4 },
  input: { borderWidth: 1, borderColor: '#D5DCE6', borderRadius: 12, padding: 12, fontSize: 16, backgroundColor: '#FAFBFD', color: colors.text },
  hint: { fontSize: 12, color: colors.muted, marginTop: 4 },
});
