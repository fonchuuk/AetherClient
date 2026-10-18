# Архитектура

## Разделение на части

Проект — это один Gradle-проект с двумя модулями:

| Модуль | Что делает | Результат |
|--------|-----------|-----------|
| `client` | мод Fabric: меню, модули, эффекты, HUD, конфиг | `client/build/libs/aetherglass-1.0.0.jar` (ремаплен в intermediary) |
| `launcher` | отдельное приложение: вход, установка игры и модов, запуск процесса | `launcher/build/libs/aurora-launcher-1.0.0.jar` |

Они **не зависят друг от друга по коду**: лаунчер находит собранный jar клиента по маске
`client/build/libs/aetherglass-*.jar` и копирует его в каталог `mods` игры.

---

## Клиент: слои

Зависимости направлены строго в одну сторону:

```
gui  ──▶  modules  ──▶  render  ──▶  utils
  │          │            │
  └──────────┴────────────┴──────▶  config
```

- `render` **не знает** про `gui` и про `modules` (получает данные через колбэки и `ColorController`);
- `modules` **не знает** про `gui` (экран настроек строится по списку `Setting`, а не вручную);
- `config` знает только про себя и `utils`;
- циклов между пакетами нет ни одного.

### Пакеты

```
com.aetherglass.client
├── AetherGlassClient                    точка входа мода (ClientModInitializer)
│
├── config/
│   └── ModConfig                   один JSON-файл: module.<id>.enabled, module.<id>.setting.<key>, hud.pos.<id>.x/y
│
├── modules/
│   ├── Module                      база: имя, описание, категория, настройки, включение, проверка ресурсов
│   ├── ModuleCategory              визуальные / интерфейс / утилиты
│   ├── ModuleManager               регистрация, включение/выключение, покадровое обновление
│   ├── ModuleRegistry              список конкретных модулей
│   ├── settings/                   Setting (база), Boolean, Int, Float, Color, Enum + фабрики
│   └── impl/                       Interface, Particles, Blocks, Hands, Sky, Rainbow, Crosshair, Hud
│
├── render/
│   ├── WorldRenderDispatcher       подписки на WorldRenderEvents, один вызов модулей за кадр
│   ├── ColorController             единый источник радужного цвета (учитывает скорость и насыщенность)
│   ├── RenderResources             один общий буфер вершин на все эффекты, освобождается при выходе
│   ├── particle/                   ParticleSystem (пул), ParticleShape, ParticlePreset
│   ├── block/BlockEffectRenderer   контур блока по его VoxelShape + частицы вокруг
│   ├── hand/                       HandEffectRenderer (оверлей поверх ванильной модели), HandStyle, HandAnimation
│   ├── sky/                        SkyEffectBlender (цвет неба в мире), SkyPreset
│   ├── crosshair/                  CrosshairRenderer, CrosshairStyle
│   ├── hud/                        HudPainter (15 элементов), HudLayout, HudElementType, PerformanceProbe
│   └── util/                       ColorUtil, Easing, AnimationTimer, FrameClock, ShapeBuffer
│
├── gui/
│   ├── theme/AuroraTheme           палитра, hue-токен, плотность, радиус, скорость
│   ├── render/DrawUtil             скруглённые прямоугольники, градиенты, «жидкое стекло», свечение
│   ├── widget/                     AuroraWidget (база), Button, Toggle, Slider, List, Label,
│   │                               ColorPicker, EnumButton, ModuleRow
│   └── screen/                     AuroraScreen (база), AuroraMenuScreen, ModuleSettingsScreen,
│                                   ClientInfoScreen, HudEditorScreen, SettingWidgets
│
├── mixin/                          MinecraftMixin, ItemInHandRendererMixin, SkyRendererMixin
├── optimization/OptimizationReport какие оптимизационные моды реально стоят
└── utils/AuroraLog                 лог с префиксом
```

Объём: **64 файла, ~7 300 строк, самый крупный класс — 366 строк** (`HudPainter`).

---

## Лаунчер: слои

```
com.aetherglass.launcher
├── LauncherMain            точка входа
├── core/
│   ├── VersionIndex            манифест версий Mojang
│   ├── GameInstaller           клиент, библиотеки, ресурсы, профиль Fabric Loader
│   ├── ModInstaller            моды из `launcher/src/main/resources/mods.json` + собранный мод клиента
│   ├── GameLauncher            аргументы JVM и игры, запуск процесса, защита от повторного запуска
│   ├── Rules                   правила библиотек и аргументов версии
│   ├── LauncherPaths           раскладка каталогов
│   ├── LauncherConfig          настройки с проверкой значений (память, Java, каталог)
│   └── JavaRuntime             поиск и проверка версии Java
├── auth/                    Microsoft → Xbox Live → XSTS → Minecraft
│   ├── MicrosoftAuth           device code + обмен токенами
│   ├── DeviceCodeFlow          опрос токен-эндпоинта
│   ├── AuthSession / AuthStore результат входа и его хранение
│   └── MicrosoftClientId       идентификатор приложения Azure
├── ui/                      Swing: окно, вкладки, логи, тема
└── util/Json, util/Http     свой JSON-парсер и HTTP с прогрессом и отменой
```

---

## Правила, заложенные в код

1. **Никаких файлов «на тысячи строк».** В клиенте максимум — 366 строк (`HudPainter`);
   в лаунчере максимум — 932 строки (`LauncherUi`, разметка окна и четырёх вкладок).
2. **Нет циклических зависимостей** между пакетами (см. схему выше).
3. **Нет дублирования.** Всё рисование идёт через `DrawUtil` + `AuroraTheme`;
   вся работа с сетью и файлами в лаунчере — через `Http` и `Json`;
   вся анимация — через `AnimationTimer` + `Easing`.
4. **Минимум вмешательства в игру: ровно 3 миксина**, каждый — в метод, проверенный
   по байткоду самой игры (`javap` по `minecraft-named.jar`):

| Миксин | Куда | Зачем |
|--------|------|-------|
| `MinecraftMixin` | `Minecraft.setScreen` → `class_310.method_1507` | подмена стандартного главного меню нашим |
| `ItemInHandRendererMixin` | `ItemInHandRenderer.renderHandsWithItems` → `class_759.method_22976` | эффекты рук **поверх** нарисованной ванильной руки |
| `SkyRendererMixin` | `SkyRenderer.extractRenderState` → `class_9975.method_74926` | изменение цвета неба в мире |

5. **Только существующие события Fabric API.** Используются
   `WorldRenderEvents.START_MAIN` / `BEFORE_TRANSLUCENT` / `BEFORE_DEBUG_RENDER`,
   `ClientTickEvents.START_CLIENT_TICK` и Key Binding API.
   Сигнатуры проверены по исходникам Fabric API 0.141.6+1.21.11.
6. **Никаких шейдеров.** Нет ни Iris, ни OptiFine, ни шейдерных пакетов:
   все эффекты — геометрия в `MultiBufferSource` и цвета в состоянии рендера.

---

## Производительность

- Анимации считаются от дельты времени (`AnimationTimer`, `Easing`, `FrameClock`),
  поэтому выглядят одинаково и на 30, и на 240 кадрах в секунду. Доказано тестом
  «60 мелких шагов = 1 крупный».
- В цикле отрисовки **нет выделений памяти**:
  - частицы — массив фиксированного размера, переиспользуются, удаление через swap;
  - один `ByteBufferBuilder` на все эффекты (`RenderResources`), он создаётся лениво
    и освобождается при выходе из мира/закрытии клиента;
  - промежуточные `Vector3f`/`PoseStack` не создаются в цикле.
- Жёсткие пределы: число частиц (по умолчанию 1 500), дистанция отсечения (32 блока),
  лимит целевых блоков (24), предел длины контура блока.
- Конфигурация пишется на диск **только при изменении** и не чаще раза в секунду,
  запись идёт во временный файл с последующей подменой.
- При смене разрешения или масштаба GUI пересчитывается только кэш разметки;
  позиции HUD хранятся в долях экрана, поэтому не «уезжают».

---

## Обработка ошибок

- Пустых `catch`-блоков нет: везде либо запись в лог с причиной, либо сообщение пользователю.
- Отрисовка любого эффекта обёрнута: исключение пишется в лог один раз, эффект отключается,
  игра продолжает работать.
- Миксины не ломают ванильный рендер: они **дополняют** результат, а не заменяют его.
- Конфиг при повреждении не «падает», а читается по ключам: битое значение
  заменяется на значение по умолчанию, файл перезаписывается.
- В лаунчере каждая сетевая и файловая ошибка превращается в конкретный текст:
  что не так, где и что делать.
