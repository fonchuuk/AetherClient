# Аудит проекта и очистка (этапы 1 и 2)

Дата: 2026-10-15. Проект: **AetherGlass Client** (ранее назывался Aurora Client).

---

## Этап 1. Что было в проекте и что с ним не так

### Состояние на момент аудита

| Компонент | Состояние | Замечание |
|-----------|-----------|-----------|
| `client` (мод Fabric) | собирается | 64 файла, ~7 400 строк |
| `launcher` | собирается | 20 файлов, ~4 000 строк |
| Маппинги, Loom, версии | актуальные | MC 1.21.11, Loom 1.17.21, Fabric API 0.141.6+1.21.11 |
| Тесты | 25 штук, все зелёные | JUnit 5 |
| Моды оптимизации | 10 + Fabric API | сверены с живым API Modrinth |

### Найденные проблемы и что с ними сделано

| № | Проблема | Как найдена | Что сделано |
|---|----------|-------------|-------------|
| 1 | **Fabric API не устанавливался** — `ModInstaller` шёл только по списку из десяти модов, а библиотека лежала отдельным полем манифеста. Игра отказывалась стартовать: `Mod 'TRender' requires any version of fabric-api, which is missing!` | настоящий запуск игры под Xvfb | Fabric API ставится одиннадцатым файлом; зависимости записаны в манифест и проверяются до скачивания; +2 теста |
| 2 | Клиент зависел от `java.awt.Color` (преобразование HSV) | чтение кода | Свой перевод HSV в `ColorUtil` (`hsb`, `hueOf`, `saturationOf`, `brightnessOf`), +2 теста |
| 3 | **Обводка блока была неточной**: бралась общая рамка `VoxelShape.bounds()` — у ступеньки, плиты, забора, лестницы контур получался «кубом» | чтение кода + байткод | Новый класс `BlockOutlineGeometry`: рёбра через `VoxelShape.forAllEdges`, объём через `forAllBoxes`, склейка общих рёбер, кэш по состоянию блока; +4 теста на ключ ребра |
| 4 | В `fabric.mod.json` стояла лицензия `ARR`, а в README — MIT; файла `LICENSE` не было вовсе | чтение кода | Лицензия MIT, файл добавлен, попадает внутрь jar |
| 5 | Промежуточный jar назывался так же, как финальный | сборка | Промежуточный — `AetherGlass-dev.jar`, финальный — `AetherGlass.jar` |
| 6 | Дистрибутивы лаунчера назывались `launcher-1.0.0.zip` | сборка | `AetherGlassLauncher-1.0.0.zip/.tar` со скриптами `bin/aurora-launcher` и `.bat` |
| 7 | Прогон игры в песочнице упирался в нехватку памяти (OOM) | запуск | Добавлен swap; зафиксировано в `BUILD.md` |

### Проверено по байткоду игры (1.21.11), не «по памяти»

- `VoxelShape.forAllEdges(Shapes$DoubleLineConsumer)`, `forAllBoxes` — есть ✓
- `RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride` — есть, и `RenderType.draw` их реально читает ✓
- `SubmitNodeCollector` — интерфейс только с `order(int)`, публичного сброса нет ✗
- `RenderType.create(String, RenderSetup)` — **пакетный** метод, из мода недоступен ✗
- `RenderPipeline.Builder`, `TextureTarget`, `RenderPass`, `Std140Builder`, `GpuSampler` — есть ✓
- `RenderSystem` не управляет `cullFace`; у `RenderPipeline.Builder` только `withCull(boolean)` ✗
- `ItemInHandRenderer.renderItem(...)` — публичный ✓

Подробности по обводке рук — в [`LIMITATIONS.md`](LIMITATIONS.md), раздел 7.

### Переименование

Проект переименован в **AetherGlass**:

- пакеты: `com.aurora.client` → `com.aetherglass.client`, `com.aurora.launcher` → `com.aetherglass.launcher`;
- идентификатор мода: `aetherglass`; ресурсы: `assets/aetherglass/`;
- точка входа: `AetherGlassClient`;
- артефакты: `AetherGlass.jar`, `AetherGlassLauncher-1.0.0.jar` и дистрибутивы.

---

## Этап 2. Очистка

Удалено (с указанием причины):

| Что | Почему |
|-----|--------|
| `/home/user/.run*.log`, `.build*.log`, `.direct.log`, `.b.log` | временные логи прогонов, ценности не имеют |
| `/home/user/shotloop.sh`, `shotloop.out`, `gamecmd.sh` | одноразовые скрипты прогона; постоянный стенд живёт в `tools/harness/` |
| `/home/user/.sudo_as_admin_successful` | служебный файл apt |
| `tools/jdk25/` (пустой) | пустой каталог от прошлой установки |
| `docs/images/game-*.png`, `menu.png` | снимки **зависшего** экрана загрузки, а не интерфейса — вводят в заблуждение |
| `aurora/.gradle/` (74 МБ) | локальный кэш проекта; зависимости лежат в `GRADLE_USER_HOME`, поэтому перекачка не потребуется |
| `client/build/libs/aurora-client-*.jar`, `launcher/build/libs/aurora-launcher-*.jar` | устаревшие сборки со старыми именами |

**Освобождено: 74 МБ** (471 → 397 МБ).

Сохранено намеренно:

- (позже удалено) `tools/mc/` (`minecraft-named.jar` + `mappings.tiny`, 40 МБ) — эталон для проверки API через `javap`; больше ничем не используется, поэтому убрано при чистке 16.10.2026;
- `tools/jdk21/` — JDK для сборки;
- `aurora-sources.zip` — резервная копия исходников (воркспейс уже однажды терялся);
- (позже удалено) `docs/images/launcher-*.png` — снимки старого Swing-лаунчера; после перехода на нативный лаунчер они перестали соответствовать действительности.

**Проверка после очистки:** `./gradlew build` → `BUILD SUCCESSFUL`, 29 тестов, 0 провалов.
Очистка сборку не сломала.

---

## Итог после этапов 1–2

```
./gradlew build              → BUILD SUCCESSFUL
./gradlew :client:test       → 15 тестов, 0 провалов
./gradlew :launcher:test     → 14 тестов, 0 провалов
артефакты                    → AetherGlass.jar, AetherGlassLauncher-1.0.0.jar (+ tar/zip)
```
