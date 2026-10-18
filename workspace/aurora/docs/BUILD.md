# Сборка и запуск

## Проверенное окружение

| | |
|---|---|
| ОС | Linux (x86_64) |
| JDK сборки | Temurin 21.0.12.1 |
| Gradle | 9.7.1 (обёртка `gradlew`, дистрибутив `gradle-9.7.1-bin.zip`) |
| Fabric Loom | 1.17.21 |
| Minecraft | 1.21.11 (маппинги `officialMojangMappings`) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |

Yarn для 1.21.11 отсутствует: `meta.fabricmc.net/v2/versions/mappings/1.21.11` отвечает
`Not Found`, поэтому используются официальные маппинги Mojang.

## Команды

```bash
./gradlew build              # всё: клиент (с ремапом) + лаунчер + установщик + тесты
./gradlew :client:build      # только мод клиента
./gradlew :launcher:build    # только лаунчер
./gradlew :installer:build   # только установщик
./gradlew test               # все тесты
./gradlew :launcher:run      # запуск окна лаунчера
./gradlew dist               # готовые файлы в dist/ + контрольные суммы
./gradlew --stop             # остановить демона Gradle
```

## Готовые файлы (`./gradlew dist`)

| Файл | Что это |
|---|---|
| `dist/AetherGlass.jar` | мод клиента для Fabric 1.21.11 |
| `dist/AetherGlassLauncher.exe` | настоящий Windows-исполняемый файл лаунчера |
| `dist/AetherGlass-Setup.exe` | установщик: установка, обновление, удаление |
| `dist/AetherGlassLauncher-1.0.0.jar` | тот же лаунчер в виде jar (для запуска на Linux/macOS) |
| `dist/CHECKSUMS.txt` | контрольные суммы SHA-256; проверка: `sha256sum -c CHECKSUMS.txt` |

## Как получаются .exe

`jar` в `.exe` не переименовывается. Оба файла собирает launch4j 3.50
(кросс-компиляция на Linux): он связывает Windows-загрузчик из `tools/launch4j`
(MinGW: `ld`, `windres`, `w32api`) и дописывает jar внутрь `.exe`.

```bash
./gradlew :launcher:exe      # → launcher/build/exe/AetherGlassLauncher.exe
./gradlew :installer:exe     # → installer/build/exe/AetherGlass-Setup.exe
```

Настройки обёртки лежат в `gradle/launch4j.gradle` и задаются пятью
свойствами модуля: `launch4jExeName`, `launch4jProductName`,
`launch4jDescription`, `launch4jIcon`, `launch4jHeapMax`. Иконки рисуются
своим скриптом: `python3 tools/make_icon.py`.

Тулчейн MinGW лежит в `tools/launch4j` (9 МБ) не случайно: в Maven-архиве
launch4j лежат 32-битные `ld` и `windres`, которые на современных 64-битных
системах не запускаются. Подробности — в `tools/launch4j/README.md`.

### Что внутри .exe

* заголовок PE (проверялся разбором заголовков: `MZ`, `PE\0\0`, секции `.text`,
  `.rsrc` с иконкой и версией);
* иконка и свойства файла (название продукта, версия, описание);
* проверка Java: если Java 21 не найдена — понятное сообщение со ссылкой на
  adoptium.net, а не «ничего не произошло»;
* защита от второго запуска (`singleInstance` с мьютексом);
* сам jar: запуск не требует ни PATH, ни ярлыка с ключами.

### Что делает установщик

`AetherGlass-Setup.exe` кладёт `AetherGlassLauncher.exe` и копию себя
(`AetherGlass-Uninstall.exe`) в `%LOCALAPPDATA%\AetherGlass`, создаёт ярлыки
на рабочем столе и в меню «Пуск» (VBScript + `cscript`) и записывает данные
в «Программы и компоненты» (`reg.exe`): название, версию, иконку, команду
удаления и восстановления. Повторный запуск — обновление: файлы заменяются,
настройки и аккаунты (`%APPDATA%\AetherGlass`) сохраняются. Удаление
доступно из «Программы и компоненты» и по ключу `--uninstall` (настройки
по умолчанию сохраняются).

Переменные окружения, которые использовались при проверке:

```bash
export JAVA_HOME=/путь/к/jdk-21
export GRADLE_USER_HOME=/var/tmp/gradle      # при желании — отдельный кэш
```

Память: в `gradle.properties` стоит `org.gradle.jvmargs=-Xmx900m`.
Этого хватает, но машина должна иметь **не меньше 2 ГБ свободной памяти** —
задача `remapJar` (перевод миксинов в intermediary) — самая тяжёлая.
Если памяти меньше, демон Gradle падает с
`Gradle build daemon disappeared unexpectedly` — это именно нехватка памяти,
лечится уменьшением `-Xmx` и закрытием других программ.

## Зависимости и версии

Всё, что скачивается при сборке, перечислено здесь явно. Ниже — ровно те
координаты, что стоят в `gradle.properties` и в `*.build.gradle`.

| Зависимость | Координата | Где указана |
|-------------|-----------|-------------|
| Minecraft | `com.mojang:minecraft:1.21.11` | `client/build.gradle` |
| Маппинги Mojang | `loom.officialMojangMappings()` | `client/build.gradle` |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.5` | `client/build.gradle` |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11` | `client/build.gradle` |
| Плагин Fabric Loom | `net.fabricmc.fabric-loom-remap:1.17.21` | `client/build.gradle` |
| JUnit (только тесты) | `org.junit.jupiter:junit-jupiter:5.11.4` | `client/build.gradle`, `launcher/build.gradle` |
| Загрузчик JUnit (только тесты) | `org.junit.platform:junit-platform-launcher` | `client/build.gradle`, `launcher/build.gradle` |

Репозитории плагинов: `maven.fabricmc.net`, `mavenCentral()`, `gradlePluginPortal()`
(`settings.gradle`). Репозитории зависимостей: `mavenCentral()` (`build.gradle`).

**Лаунчер не имеет внешних зависимостей вообще**: только JDK
(Swing, `java.net.http`, свой JSON-парсер). Это сделано намеренно,
чтобы установка лаунчера не тянула за собой полсотни библиотек.

## Что получается после сборки

```
client/build/libs/aetherglass-1.0.0.jar          # мод (203 КБ, ремаплен в intermediary)
client/build/libs/aetherglass-1.0.0-sources.jar  # исходники мода
launcher/build/libs/aurora-launcher-1.0.0.jar      # лаунчер (115 КБ)
launcher/build/distributions/                      # архивы лаунчера (tar/zip)
```

Мод нужно положить в каталог `mods` игры — лаунчер делает это сам
(ищет `client/build/libs/aetherglass-*.jar`).

## Результаты реальной проверки

### 1. Сборка

```
BUILD SUCCESSFUL
17 actionable tasks: 17 executed
BUILD SUCCESSFUL in 2m 55s
```

Это прогон **с полностью пустым кэшем** (каталог Gradle и все артефакты
Minecraft были удалены): скачаны Gradle 9.7.1, Minecraft 1.21.11, маппинги,
Fabric Loader, Fabric API, Loom — и всё собрано заново. Повторные сборки идут
за секунды, потому что всё закэшировано.

### 2. Тесты

`./gradlew :launcher:test` — **14 тестов, все пройдены**:

- разбор JSON (объекты, массивы, escape-последовательности, понятная ошибка на битом JSON);
- обратное преобразование JSON без потери структуры;
- правила библиотек и аргументов (платформенные правила и признаки запуска);
- подстановка токенов в аргументы запуска;
- разбор версии Java из вывода `java -version`;
- построение пути Maven из координат библиотеки;
- границы объёма памяти в настройках;
- манифест модов: ровно 10 записей, у каждой SHA-512 и ссылка на Modrinth, плюс Fabric API;
- каталоги и пути установки;
- разбор ответа ошибки Microsoft (поле `error`/`error_description` превращается в текст);
- подстановка токенов в аргументы игры;
- отмена скачивания;
- установщик модов ставит ровно 11 файлов (десять модов + Fabric API) —
  именно этот тест ловит ошибку, из-за которой игра не запускалась;
- установщик модов отвергает манифест с неудовлетворённой зависимостью
  и называет, чего именно не хватает.

`./gradlew :client:test` — **11 тестов, все пройдены** (чистая логика без запуска игры):

- цвет: разбор и запись `#RRGGBBAA`, прозрачность, затухание, смешивание в границах;
- цвет HSV: перевод вручную (без `java.awt`) совпадает с основными цветами,
  а прямое и обратное преобразование не расходятся на 6 образцах;
- кривые: границы `0→0`, `1→1` и монотонность;
- анимация: **60 мелких шагов дают тот же результат, что один крупный** —
  это и есть проверка независимости от частоты кадров (расхождение < 0.001);
- часы кадра: шаг увеличивается, но обрезается сверху;
- пул частиц: не создаёт больше предела, не удаляет раньше времени,
  освобождает место по истечении времени жизни, сохраняет частицы при
  изменении предела, минимум пула — 16;
- пресеты частиц: все значения в допустимых пределах;
- тема: смена палитры и плотности реально меняет цвета, радиус и скорость
  обрезаются по границам.

Итого: **25 тестов, 0 провалов.**

### 3. Установка игры «вживую»

Лаунчер установил полную копию игры в тестовый каталог:

| | |
|---|---|
| Клиентский jar 1.21.11 | скачан, SHA-1 проверен |
| Библиотек в classpath | 70 файлов (включая Fabric Loader и его зависимости) |
| Ресурсов | 4591 файл, индекс `29` |
| Время установки | 27 с (ресурсы — параллельно, 16 потоков) |
| `mainClass` | `net.fabricmc.loader.impl.launch.knot.KnotClient` |
| Незаменённых токенов `${…}` в командной строке | 0 |

Пример сформированной команды (сокращено):

```
java -Xms1024M -Xmx4096M -Dfile.encoding=UTF-8
     -Dlog4j.configurationFile=…/assets/log_configs/client-1.21.2.xml
     -Djava.library.path=…/versions/1.21.11/natives-1.21.11
     -cp <70 библиотек>  net.fabricmc.loader.impl.launch.knot.KnotClient
     --username … --uuid … --accessToken … --userType msa --version fabric-loader-0.19.5-1.21.11
     --gameDir … --assetsDir … --assetIndex 29
```

### 4. Запуск игры

Игра действительно стартует с этой командой. В отчёте (`Launcher name: AuroraLauncher`)
перечислены все моды, включая `aetherglass: AetherGlass Client 1.0.0`,
Fabric API и 10 оптимизационных модов.

Единственная ошибка в этой песочнице — `Failed to initialize GLFW: Failed to detect any
supported platform`: в контейнере нет ни экрана, ни GPU. Это ограничение окружения, а не кода,
и оно находится строго после загрузки модов.

### 5. Проверка миксинов

Миксины применены — это проверено не «на глаз»:

- Fabric Loader 0.19.5 загрузил мод и выполнил точку входа `client`
  (`com.aetherglass.client.AetherGlassClient`) — именно эта проверка изначально нашла настоящую ошибку:
  в `fabric.mod.json` была лишняя точка входа `main`, для которой класс обязан реализовывать
  `ModInitializer`; ошибка исправлена, после чего загрузка пошла дальше.
- С включённым `-Dmixin.debug.export=true` в каталоге `.mixin.out` появились
  преобразованные классы игры: `net/minecraft/class_310.class` (Minecraft) и
  `net/minecraft/class_638.class` (ClientLevel) содержат наш код.
- В собранном jar все три миксина содержат **уже переведённые в intermediary** ссылки:

  | Миксин | Целевой класс | Метод |
  |--------|---------------|-------|
  | `MinecraftMixin` | `class_310` | `method_1507` (`setScreen`) |
  | `ItemInHandRendererMixin` | `class_759` | `method_22976` (`renderHandsWithItems`) |
  | `SkyRendererMixin` | `class_9975` | `method_74926` (`extractRenderState`) |

  То есть ремап выполнен Fabric Loom'ом, а не «на доверии».

  **Про refmap:** Fabric Loom 1.17 печатает
  «The mixin annotation is no longer enabled by default…» — это информационное
  сообщение, а не ошибка. Попытка включить аннотационный процессор миксинов
  через `annotationProcessor "net.fabricmc:sponge-mixin:…"` **не работает**
  с официальными маппингами Mojang: процессор не находит notch-маппинги и
  падает с `Unable to locate obfuscation mapping for @Inject target`.
  Правильный путь — тот, что используется: `remapJar` самого Loom'а.
  Это проверено обеими сторонами, поэтому конфигурация оставлена такой, какая она есть.

## Как проверяется работа (и почему игра не запускается вручную)

Запуск Minecraft в песочнице без видеокарты даёт ложные выводы: игра
упирается в создание текстурного атласа на программном OpenGL (llvmpipe) и не
доходит даже до главного меню. Поэтому такой путь **не используется**: стенд
`tools/harness/HeadlessRun.java` оставлен в репозитории, но результатом он не
считается, и никакие «проверено в игре» утверждения на нём не строятся.

Вместо этого работает проверка по коду, причём в несколько проходов:

1. **Соответствие реальному API.** Все классы и методы Minecraft сверяются
   разбором байткода (`javap` по `tools/mc/minecraft-named.jar`, версия
   1.21.11). Так проверены, например, `RealmsMainScreen`, `LanguageSelectScreen`,
   `ConfirmScreen`, `RenderPipelines.GUI_TEXTURED`, `Screen.resize(int,int)`.
2. **Автотесты.** 81 тест: логика атмосферных надписей, геометрия блоков,
   установка и удаление на настоящих файлах, разбор версий Java, хранение
   токенов, загрузка модов. Запуск: `./gradlew test`.
3. **Разбор результатов.** Готовые `.exe` разбираются как PE-файлы (заголовок,
   машина, секции, ресурсы), контрольные суммы проверяются `sha256sum -c`.
4. **Сборка.** `./gradlew build` — зелёная, без подавленных ошибок.

Что **не** проверено и потому не выдаётся за работающее: внешний вид меню на
живом экране, работа ярлыков и записи в реестр Windows (сборка идёт на Linux),
поведение установщика в настоящей Windows.

## Частые проблемы

| Симптом | Причина | Что делать |
|---------|---------|-----------|
| `Cannot use Mojang mappings in a non-obfuscated environment` | использован плагин `net.fabricmc.fabric-loom` | в `client/build.gradle` должен быть `net.fabricmc.fabric-loom-remap` |
| `Gradle build daemon disappeared unexpectedly` | не хватило памяти на `remapJar` | в `gradle.properties` уменьшено `-Xmx` до 900m; нужно ≥2 ГБ свободной памяти |
| Loom требует Java 25 | взята версия Loom 1.18.x | используется 1.17.21 (JVM 21) |
| Лаунчер пишет «Java не найдена» | в системе нет Java 21+ | указать путь на вкладке «Настройки» |
| «Мод клиента не найден» | не выполнена сборка | `./gradlew :client:build` |
| Игра не запускается на сервере без монитора | нет GLFW/OpenGL | запускать на обычном рабочем столе |
