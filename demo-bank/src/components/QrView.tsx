import React, { useMemo } from 'react';
import { View } from 'react-native';
import Svg, { Rect } from 'react-native-svg';
import QRCode from 'qrcode';

/** QR-код на SVG. Генерация на JavaScript, без нативных библиотек. */
export function QrView({ value, size = 220 }: { value: string; size?: number }) {
  const { n, cells } = useMemo(() => {
    const qr = QRCode.create(value, { errorCorrectionLevel: 'M' });
    const count = qr.modules.size;
    const data = qr.modules.data;
    const out: { x: number; y: number }[] = [];
    for (let y = 0; y < count; y++) {
      for (let x = 0; x < count; x++) {
        if (data[y * count + x]) out.push({ x, y });
      }
    }
    return { n: count, cells: out };
  }, [value]);
  return (
    <View style={{ alignSelf: 'center', backgroundColor: '#FFFFFF', padding: 12, borderRadius: 12 }}>
      <Svg width={size} height={size} viewBox={`0 0 ${n} ${n}`}>
        {cells.map((c) => (
          <Rect key={`${c.x}-${c.y}`} x={c.x} y={c.y} width={1} height={1} fill="#15202B" />
        ))}
      </Svg>
    </View>
  );
}
