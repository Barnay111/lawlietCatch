import React from 'react';
import { Text } from 'react-native';
import { Card, Muted, Screen, Title, colors } from '@/components/ui';
import { QrView } from '@/components/QrView';
import { receiveCode } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

/** Свой QR-код для получения денег. В коде только телефон владельца, без номера карты. */
export default function ReceiveScreen() {
  const session = useRequireLogin();
  useTrack('transfer.receive');
  const phone = '+77011234567';
  return (
    <Screen>
      <Card>
        <Title>Получить деньги</Title>
        <Muted>Покажите код отправителю: он отсканирует его в приложении банка.</Muted>
        <QrView value={receiveCode(phone)} size={220} />
        <Text style={{ textAlign: 'center', color: colors.text, marginTop: 12, fontWeight: '600' }}>
          {session.userId ? `Псевдоним: ${session.userId}` : ''}
        </Text>
      </Card>
    </Screen>
  );
}
