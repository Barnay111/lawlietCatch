import React, { useMemo } from 'react';
import { NativeScrollEvent, NativeSyntheticEvent, View, ViewProps } from 'react-native';
import { BehaviorGuard } from './client';
import { assertFieldId } from './privacy';

/**
 * Обёртка экрана: фиксирует касания и свайпы внутри себя.
 * Касания не перехватываются: дочерние элементы работают как обычно.
 * Жесты из нескольких пальцев (масштабирование) не анализируются.
 */
export function BehaviorGuardView({ children, ...rest }: ViewProps) {
  return (
    <View
      {...rest}
      onTouchStart={(e) => {
        const n = e.nativeEvent;
        if (n.touches.length > 1) {
          BehaviorGuard.touchCancel();
          return;
        }
        BehaviorGuard.touchStart(n.pageX, n.pageY);
      }}
      onTouchMove={(e) => {
        const n = e.nativeEvent;
        if (n.touches.length > 1) return;
        BehaviorGuard.touchMove(n.pageX, n.pageY);
      }}
      onTouchEnd={() => BehaviorGuard.touchEnd()}
      onTouchCancel={() => BehaviorGuard.touchCancel()}
    >
      {children}
    </View>
  );
}

/** Свойства для ScrollView: `<ScrollView {...behaviorScrollProps()}>`. */
export function behaviorScrollProps() {
  return {
    scrollEventThrottle: 100,
    onScroll: (e: NativeSyntheticEvent<NativeScrollEvent>) => {
      const { x, y } = e.nativeEvent.contentOffset;
      BehaviorGuard.scrollOffset(x, y);
    },
  };
}

export interface BehaviorFieldProps {
  onChangeText: (prevText: string, nextText: string) => void;
  onBlur: () => void;
}

const NOOP_FIELD: BehaviorFieldProps = {
  onChangeText: () => undefined,
  onBlur: () => undefined,
};

/**
 * Подключение текстового поля. Для чувствительных полей (ПИН, карта, ИИН)
 * возвращаются пустые обработчики: сбор не начинается, даже тайминги не пишутся.
 *
 *   const amount = useBehaviorField('transfer.amount');
 *   <TextInput value={v} onChangeText={(t) => { amount.onChangeText(v, t); setV(t); }} onBlur={amount.onBlur} />
 *
 * SDK читает только длины строк; текст не сохраняется.
 */
export function useBehaviorField(fieldId: string, options?: { sensitive?: boolean }): BehaviorFieldProps {
  const sensitive = options?.sensitive ?? false;
  return useMemo(() => {
    if (sensitive) {
      return NOOP_FIELD;
    }
    assertFieldId(fieldId);
    return {
      onChangeText: (prevText: string, nextText: string) =>
        BehaviorGuard.textChange(fieldId, prevText.length, nextText.length),
      onBlur: () => BehaviorGuard.textBlur(fieldId),
    };
  }, [fieldId, sensitive]);
}
