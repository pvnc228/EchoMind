# Owner decisions: API compatibility and M4 follow-up

Дата подтверждения: 2026-08-09

Статус: **CONFIRMED BY PRODUCT OWNER**. Это решение, а не предложение агента.

Источник ввода:
[`USER_INPUT_API_COMPAT_AND_M4_2026-08-09.md`](USER_INPUT_API_COMPAT_AND_M4_2026-08-09.md).

## Контекст личного устройства

- Устройство: Xiaomi POCO X6 5G.
- Android 16, API 36, HyperOS 3.0.301.0.
- Экран: 6.67 inch, 2712x1220 px.
- Владелец готов провести проверку на устройстве позже.
- Устройство не подключалось в repair-сессии 2026-08-09.

Личное устройство даёт будущий API36 hardware oracle, но не заменяет проверку
opaque fallback на API 26-30.

## Решение 1: API 26-30 compatibility gate

1. Создать отдельные AVD для API 26 и API 30.
2. API 26 проверяет нижнюю границу `minSdk`; API 30 проверяет верхнюю границу
   pre-Android-12 fallback. API 35 остаётся текущим контрольным runtime.
3. Владелец разрешил загрузить необходимые Android system images.
4. Перед закрытием критерия выполнить на обоих AVD реальные build/install/run,
   Compose/instrumented tests и визуально-семантический fallback oracle.
5. Не считать наличие SDK package или собранного APK runtime-доказательством.

Точные package IDs, ABI и размер загрузки определяются через `sdkmanager --list`
перед установкой и фиксируются в артефакте выполнения.

## Решение 2: M4 outcome -> reviewed revision

После записи outcome приложение предлагает необязательное локальное действие
`Review impact`:

1. Пользователь сам открывает сравнение.
2. Экран показывает исходные grounds/choice, outcome и предлагаемый diff.
3. Предложенная формулировка остаётся proposal и не изменяет подтверждённую
   conclusion.
4. Только явное подтверждение создаёт новую revision; отклонение не меняет
   conclusion или исторические links.
5. Decision, outcome, исходная revision и новая revision остаются inspectable и
   связаны provenance.

Никакого автоматического вывода о том, что прежняя conclusion была неверной,
и никакого автоматического rebase исторических evidence/theme links.

## Решение 3: optional follow-up

После фиксации choice можно один раз предложить срок follow-up в диапазоне
1-3 дней. Предложение не блокирует дальнейшую работу и может быть отклонено.

Если пользователь принимает follow-up:

- reminder планируется локально;
- системное push/local notification допустимо после явного opt-in и получения
  требуемого Android notification permission;
- notification предоставляет действия `Перенести` и `Отменить`;
- те же действия доступны внутри приложения;
- отказ в notification permission оставляет in-app follow-up без ошибки и без
  повторного давления;
- текст на lock screen минимизирован и не раскрывает raw reflection или choice.

Точный preset внутри диапазона 1-3 дней и набор вариантов переноса выбираются в
implementation slice; они не должны превращаться в streak, guilt или
обязательный reminder.

## Статус реализации

- API26/API30 AVD evidence: **не выполнено**.
- M4 Review impact: **не реализовано**.
- Optional follow-up notification: **не реализовано**.

Решение снимает product-неопределённость и разрешает следующий implementation
slice, но само по себе не закрывает roadmap criteria.
