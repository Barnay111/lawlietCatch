import React from 'react';
import { StyleSheet, Text } from 'react-native';
import { Card, colors } from '@/components/ui';

export function Result({ ok, text }: { ok: boolean; text: string }) {
  return (
    <Card style={{ marginTop: 12 }}>
      <Text style={[styles.text, { color: ok ? colors.ok : colors.danger }]}>{text}</Text>
    </Card>
  );
}

const styles = StyleSheet.create({ text: { fontWeight: '600', fontSize: 15 } });
