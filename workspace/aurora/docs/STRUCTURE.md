# Структура проекта

Дерево исходников (без каталогов сборки). Сгенерировано из рабочей копии.

```
LICENSE
README.md
build.gradle
client/build.gradle
client/src/main/java/com/aurora/client/AetherGlassClient.java
client/src/main/java/com/aurora/client/config/ModConfig.java
client/src/main/java/com/aurora/client/gui/render/DrawUtil.java
client/src/main/java/com/aurora/client/gui/screen/AuroraMenuScreen.java
client/src/main/java/com/aurora/client/gui/screen/AuroraScreen.java
client/src/main/java/com/aurora/client/gui/screen/ClientInfoScreen.java
client/src/main/java/com/aurora/client/gui/screen/HudEditorScreen.java
client/src/main/java/com/aurora/client/gui/screen/ModuleSettingsScreen.java
client/src/main/java/com/aurora/client/gui/screen/SettingWidgets.java
client/src/main/java/com/aurora/client/gui/theme/AuroraTheme.java
client/src/main/java/com/aurora/client/gui/widget/AuroraButton.java
client/src/main/java/com/aurora/client/gui/widget/AuroraLabel.java
client/src/main/java/com/aurora/client/gui/widget/AuroraList.java
client/src/main/java/com/aurora/client/gui/widget/AuroraSlider.java
client/src/main/java/com/aurora/client/gui/widget/AuroraToggle.java
client/src/main/java/com/aurora/client/gui/widget/AuroraWidget.java
client/src/main/java/com/aurora/client/gui/widget/ColorPicker.java
client/src/main/java/com/aurora/client/gui/widget/EnumButton.java
client/src/main/java/com/aurora/client/gui/widget/ModuleRow.java
client/src/main/java/com/aurora/client/mixin/ItemInHandRendererMixin.java
client/src/main/java/com/aurora/client/mixin/MinecraftMixin.java
client/src/main/java/com/aurora/client/mixin/SkyRendererMixin.java
client/src/main/java/com/aurora/client/modules/Module.java
client/src/main/java/com/aurora/client/modules/ModuleCategory.java
client/src/main/java/com/aurora/client/modules/ModuleManager.java
client/src/main/java/com/aurora/client/modules/ModuleRegistry.java
client/src/main/java/com/aurora/client/modules/impl/BlockEffectsModule.java
client/src/main/java/com/aurora/client/modules/impl/CrosshairModule.java
client/src/main/java/com/aurora/client/modules/impl/HandEffectsModule.java
client/src/main/java/com/aurora/client/modules/impl/HudModule.java
client/src/main/java/com/aurora/client/modules/impl/InterfaceModule.java
client/src/main/java/com/aurora/client/modules/impl/ParticleModule.java
client/src/main/java/com/aurora/client/modules/impl/RainbowModule.java
client/src/main/java/com/aurora/client/modules/impl/SkyEffectsModule.java
client/src/main/java/com/aurora/client/modules/settings/BooleanSetting.java
client/src/main/java/com/aurora/client/modules/settings/ColorSetting.java
client/src/main/java/com/aurora/client/modules/settings/EnumSetting.java
client/src/main/java/com/aurora/client/modules/settings/FloatSetting.java
client/src/main/java/com/aurora/client/modules/settings/IntSetting.java
client/src/main/java/com/aurora/client/modules/settings/Setting.java
client/src/main/java/com/aurora/client/render/ColorController.java
client/src/main/java/com/aurora/client/render/RenderResources.java
client/src/main/java/com/aurora/client/render/WorldRenderDispatcher.java
client/src/main/java/com/aurora/client/render/block/BlockEffectRenderer.java
client/src/main/java/com/aurora/client/render/crosshair/CrosshairRenderer.java
client/src/main/java/com/aurora/client/render/crosshair/CrosshairStyle.java
client/src/main/java/com/aurora/client/render/hand/HandAnimation.java
client/src/main/java/com/aurora/client/render/hand/HandEffectRenderer.java
client/src/main/java/com/aurora/client/render/hand/HandStyle.java
client/src/main/java/com/aurora/client/render/hud/HudElementType.java
client/src/main/java/com/aurora/client/render/hud/HudLayout.java
client/src/main/java/com/aurora/client/render/hud/HudPainter.java
client/src/main/java/com/aurora/client/render/hud/PerformanceProbe.java
client/src/main/java/com/aurora/client/render/particle/ParticlePreset.java
client/src/main/java/com/aurora/client/render/particle/ParticleShape.java
client/src/main/java/com/aurora/client/render/particle/ParticleSystem.java
client/src/main/java/com/aurora/client/render/sky/SkyEffectBlender.java
client/src/main/java/com/aurora/client/render/sky/SkyPreset.java
client/src/main/java/com/aurora/client/render/util/AnimationTimer.java
client/src/main/java/com/aurora/client/render/util/ColorUtil.java
client/src/main/java/com/aurora/client/render/util/Easing.java
client/src/main/java/com/aurora/client/render/util/FrameClock.java
client/src/main/java/com/aurora/client/render/util/ShapeBuffer.java
client/src/main/java/com/aurora/client/utils/AuroraLog.java
client/src/main/resources/assets/aetherglass/lang/en_us.json
client/src/main/resources/assets/aetherglass/lang/ru_ru.json
client/src/main/resources/assets/aetherglass/textures/particle/circle.png
client/src/main/resources/assets/aetherglass/textures/particle/heart.png
client/src/main/resources/assets/aetherglass/textures/particle/ring.png
client/src/main/resources/assets/aetherglass/textures/particle/spark.png
client/src/main/resources/assets/aetherglass/textures/particle/square.png
client/src/main/resources/assets/aetherglass/textures/particle/star.png
client/src/main/resources/assets/aetherglass/textures/particle/white.png
client/src/main/resources/aetherglass.mixins.json
client/src/main/resources/fabric.mod.json
client/src/test/java/com/aurora/client/ClientCoreTest.java
docs/ARCHITECTURE.md
docs/BUILD.md
docs/FEATURES.md
docs/LIMITATIONS.md
docs/MODS.md
docs/STRUCTURE.md
gradle.properties
gradle/wrapper/gradle-wrapper.properties
launcher/build.gradle
launcher/src/main/java/com/aurora/launcher/LauncherMain.java
launcher/src/main/java/com/aurora/launcher/auth/AuthSession.java
launcher/src/main/java/com/aurora/launcher/auth/AuthStore.java
launcher/src/main/java/com/aurora/launcher/auth/MicrosoftAuth.java
launcher/src/main/java/com/aurora/launcher/auth/MicrosoftClientId.java
launcher/src/main/java/com/aurora/launcher/core/GameInstaller.java
launcher/src/main/java/com/aurora/launcher/core/GameLauncher.java
launcher/src/main/java/com/aurora/launcher/core/JavaRuntime.java
launcher/src/main/java/com/aurora/launcher/core/LauncherConfig.java
launcher/src/main/java/com/aurora/launcher/core/LauncherPaths.java
launcher/src/main/java/com/aurora/launcher/core/ModInstaller.java
launcher/src/main/java/com/aurora/launcher/core/Rules.java
launcher/src/main/java/com/aurora/launcher/core/VersionIndex.java
launcher/src/main/java/com/aurora/launcher/ui/LauncherUi.java
launcher/src/main/java/com/aurora/launcher/ui/LauncherWindow.java
launcher/src/main/java/com/aurora/launcher/ui/LogPanel.java
launcher/src/main/java/com/aurora/launcher/ui/UiPreview.java
launcher/src/main/java/com/aurora/launcher/ui/UiTheme.java
launcher/src/main/java/com/aurora/launcher/util/Http.java
launcher/src/main/java/com/aurora/launcher/util/Json.java
launcher/src/main/resources/mods.json
launcher/src/test/java/com/aurora/launcher/LauncherCoreTest.java
settings.gradle
tools/gen_particle_textures.py
tools/pinmods.py
tools/verify_mods.py
```

## Объём кода

| Метрика | Значение |
|---------|----------|
| Файлов Java в клиенте | 64 |
| Файлов Java в лаунчере | 20 |
| Строк в клиенте | 7360 |
| Строк в лаунчере | 3964 |
| Самый крупный файл | 932 LauncherUi.java |
| Второй по размеру | 408 GameInstaller.java |
| Файлов больше 500 строк | 1 |
| Файлов больше 1000 строк | 0 |

Средний файл — около 100 строк. Разбиение по пакетам и правила зависимостей —
в [ARCHITECTURE.md](ARCHITECTURE.md).
