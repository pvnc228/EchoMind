# Device Validation Guide (EchoMind)

## 2026-08-13 implementation handoff

Before publication, the integrated M0/M1 branch completed the JVM suite and
connected instrumentation on API 26, API 30, and API 35; each connected run
reported `99/99`. The API 35 pass also covered the exact transmission preview,
200% text, compact layout, IME, TalkBack service/tree focus, real landscape,
and proposal persistence after restart.

By owner decision, repeated whole-product regression, debugging, and
stabilization now form a separate stage after cascade implementation. Moving
Issues #2 and #3 to `Done` records integration of their scoped logic; it does
not claim that every later stabilization pass or broader product milestone is
complete.

Ручной проход по экранам и состояниям на устройстве/эмуляторе. Гонять перед
переводом UX-чекпоинта в `done`. Автопокрытие уже закрывает: сборку, юнит-
тесты и 12/12 инструментованных тестов (вкл. Review-структуру).

**Цель:** TalkBack, масштабирование текста, поворот/широкий экран и fallback
стекла на API 26-30. Стекло ограничено функциональным слоем (action dock и
toggle); provenance остаётся читаемым материал.

## 0. Подготовка

- Устройство/эмулятор на API 26-30 **и** на API 33+ (для проверки fallback).
- Debug-сборка: `.\gradlew.bat :app:assembleDebug` (JDK из Android Studio).
- Установка: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

## 1. TalkBack

1. Включи TalkBack (Настройки → Специальные возможности).
2. Пройди флоу: `capture → review → confirm` и `capture → review → reject`.
3. Проверь по шагам:
   - Заголовок «My wording» и label поля слышны и различимы от заголовков карточек.
   - Фокус идёт по порядку `source → thesis → alternative → my wording → действия`.
   - «Show full analysis» объявляется и раскрывает контент без потери фокуса.
   - Сообщение permission-denied читается (polite), но не блокирует текст.
   - После «Confirm my conclusion»/«Reject…» доступно «Start another reflection».
ИТОГ РУЧНОГО ПРОХОДА: все шаги проверены, функция работает и все объявляется правильно и отчетливо. ПОДПИСЬ: юзер от 07.08.26 0:52


## 2. Масштаб текста (200%)

1. Настройки → Размер шрифта → максимальный (200%).
2. Review-экран: карточки, поле, кнопки не обрезают текст и не перекрывают
   друг друга; кнопки остаются нажатыми в пределах экрана.
3. Значение поля «My wording» и заголовки не съезжают за экран; текст переносится.
ИТОГ РУЧНОГО ПРОХОДА: все шаги проверены, увеличение шрифта не повлияло на читаемость и отображение текста, текст не обрезается и ничего не перекрывает, текст корректно переносится. ПОДПИСЬ: юзер от 07.08.26 1:00

## 3. Поворот и широкий экран

1. Повтори Review в landscape и на большом окне/планшете.
2. `source → proposal → my wording` сохраняет порядок; длинный текст
   вертикально скроллится; поле уважает IME-inset.
ИТОГ РУЧНОГО ПРОХОДА: все шаги проверены, отображение корректно. ПОДПИСЬ: юзер от 07.08.26 1:10

## 4. Fallback API 26-30

1. Запусти ту же сборку на API 26-30 (где нет blur).
2. Action dock читаем: непрозрачная тональная подложка, кнопки различимы.
3. Icon-контент никогда не несёт состояние цветом в одиночку.
ОТВЕТ ОТ ЮЗЕРА: нет доступа к API 26-30 в андроид студио, даже в unsupported. Предлагаю расширение поддержки для outdated APIs отвести на дальнейшие шаги сопровождения продукта
## 5. Recovery-состояния

1. Отклони микрофон: текст по-прежнему доступен, сообщение видно.
2. Прерви несохранённую запись: возврат в capture без потери введённого.
3. Restart-восстановление: подтверждённая ревизия и источник сохраняются.
ИТОГ РУЧНОГО ПРОХОДА: все шаги проверены. ПОДПИСЬ: юзер от 07.08.26 1:15

## Результат

Зафиксируй итог в `JOURNAL.md` (проход/провал, замечания, кто гонял) с
подписью `агент opencode` перед тем, как отметить чекпоинт complete в
`ROADMAP.md`.

## 6. Автоматизация вёрстки и доступности через android-cli (2026-09-03)

- **Инструмент**: Google Android CLI (`android.exe` v1.0.15985488), эмулятор `Pixel_8_2` (API 35).
- **Выгрузка иерархии Compose (android layout)**:
  - Home 100% font scale: `docs/layout_home_100.json`, скриншот `docs/screenshots/home_100.png`
  - Home 200% font scale: `docs/layout_home_200.json`, скриншот `docs/screenshots/home_200.png`
  - Review 100% / 200%: `docs/layout_review_100.json`, `docs/layout_review_200.json`, скриншоты `docs/screenshots/review_100.png`
  - Settings (Speech Recognition & BYOK Presets): `docs/layout_settings_200.json`, `docs/layout_settings_gemini.json`, скриншоты `docs/screenshots/settings_200.png`, `docs/screenshots/settings_gemini.png`
- **Автоматическая валидация (`tools/validate_layout.py`)**:
  - Масштаб шрифта 200%: все текстовые и интерактивные элементы остаются в пределах границ экрана [1080 x 2400], текст корректно переносится.
  - ActionDock и навигационные элементы: отсутствуют перекрытия и взаимные пересечения активных зон.
  - Порядок обхода TalkBack (accessibility traversal order): логическая группировка элементов сохраняет семантическую последовательность обхода.
- **Инструментованный запуск**: `:app:connectedDebugAndroidTest` — 107/107 passed на `Pixel_8_2` API 35.
