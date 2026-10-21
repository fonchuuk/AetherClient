import com.aetherglass.launcher.auth.AuthSession;
import com.aetherglass.launcher.core.GameInstaller;
import com.aetherglass.launcher.core.GameLauncher;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.LauncherPaths;
import com.aetherglass.launcher.core.ModInstaller;
import com.aetherglass.launcher.util.Http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Стенд для проверки без монитора: ставит игру через код лаунчера и запускает её.
 *
 * <p>Зачем: в сборочной песочнице нет экрана, но есть Xvfb и программный OpenGL
 * (llvmpipe). Этот класс пользуется теми же классами, что и интерфейс лаунчера
 * ({@code GameInstaller}, {@code ModInstaller}, {@code GameLauncher}), поэтому
 * проверка идёт по настоящему пути установки, а не по «похожему».</p>
 *
 * <p>Вход в аккаунт здесь не нужен: игра запускается в автономном режиме с
 * выдуманным именем игрока. Это не подделка авторизации — это запуск без
 * входа, который Minecraft поддерживает сам (сетевая игра при этом недоступна).</p>
 *
 * <p>Сборка и запуск:</p>
 * <pre>
 * javac -cp launcher/build/classes/java/main -d /tmp/harness tools/harness/HeadlessRun.java
 * DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 \
 *   java -cp launcher/build/classes/java/main:/tmp/harness HeadlessRun build/harness-run 854 480
 * </pre>
 *
 * <p>Когда игра дойдёт до главного меню, стенд напечатает {@code READY} и будет
 * держать игру запущенной, чтобы можно было снять экран, а затем завершит её.</p>
 */
public final class HeadlessRun {

    private static final String MC_VERSION = "1.21.11";
    private static final String LOADER_VERSION = "0.19.5";
    private static final String READY_MARKER = "главное меню заменено";
    private static final int HOLD_SECONDS = 1500;
    private static final int WAIT_SECONDS = 420;

    private HeadlessRun() {
    }

    public static void main(String[] args) throws Exception {
        Path base = Path.of(args.length > 0 ? args[0] : "build/harness-run");
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 854;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 480;

        LauncherPaths paths = new LauncherPaths(base);
        LauncherConfig config = LauncherConfig.defaults()
                .gameDirectory(base)
                .minMemoryMb(256)
                .maxMemoryMb(640)
                .windowWidth(width)
                .windowHeight(height)
                .javaExecutable(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                .selectedVersion(MC_VERSION);

        System.out.println("[стенд] каталог игры: " + base);
        System.out.println("[стенд] разрешение: " + width + "x" + height);

        Http.CancelFlag never = () -> false;
        Http.ProgressListener files = (name, done, total) -> {
            if (done == total) {
                System.out.println("[файл] " + name);
            }
        };
        GameInstaller.Progress progress = new GameInstaller.Progress() {
            @Override
            public void stage(String text) {
                System.out.println("[этап] " + text);
            }

            @Override
            public void onFile(String name, long done, long total) {
                files.onFile(name, done, total);
            }
        };

        long started = System.currentTimeMillis();
        GameInstaller installer = new GameInstaller(paths, MC_VERSION, LOADER_VERSION, progress, never);
        GameInstaller.Installation installation = installer.install();
        System.out.println("[стенд] игра установлена за " + (System.currentTimeMillis() - started) / 1000 + " с");
        System.out.println("[стенд] главный класс: " + installation.mainClass());
        System.out.println("[стенд] библиотек в пути: " + installation.classpath().size());

        ModInstaller mods = ModInstaller.fromResource(paths, files, never);
        List<Path> installed = mods.install();
        System.out.println("[стенд] модов установлено: " + installed.size());

        Path built = findClientMod();
        if (built != null) {
            Path target = mods.installClientMod(built);
            System.out.println("[стенд] мод клиента: " + target.getFileName());
        } else {
            System.out.println("[стенд] ВНИМАНИЕ: собранный мод клиента не найден");
        }

        // Автономный запуск: имя и идентификатор выдуманы, вход не имитируется.
        AuthSession offline = new AuthSession("AuroraTest", UUID.randomUUID().toString(),
                "offline", "", "", System.currentTimeMillis() + 3_600_000L, "");

        GameLauncher launcher = new GameLauncher();
        GameLauncher.LaunchPlan plan = launcher.plan(installation, paths, config, offline,
                Path.of(config.javaExecutable()));
        System.out.println("[стенд] команда запуска:");
        System.out.println(String.join(" ", plan.command()));

        StringBuilder tail = new StringBuilder();
        Process process = launcher.launch(plan, line -> {
            System.out.println("[игра] " + line);
            if (tail.length() > 4000) {
                tail.setLength(0);
            }
            tail.append(line).append('\n');
        }, () -> System.out.println("[стенд] игра завершилась"));

        boolean ready = false;
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (tail.indexOf(READY_MARKER) >= 0) {
                ready = true;
                break;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(500L);
        }

        if (ready) {
            System.out.println("READY");
            // Держим игру запущенной, пока снимается экран.
            Thread.sleep(HOLD_SECONDS * 1000L);
        } else {
            System.out.println("[стенд] главное меню не дождались: маркер «" + READY_MARKER + "» не найден");
        }

        launcher.stop();
        Thread.sleep(2000L);
        System.exit(ready ? 0 : 1);
    }

    /** Ищет собранный мод клиента рядом с проектом. */
    private static Path findClientMod() throws Exception {
        Path directory = Path.of("client/build/libs");
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> {
                        String n = path.getFileName().toString().toLowerCase();
                        return n.startsWith("aetherglass") && n.endsWith(".jar")
                                && !n.endsWith("-sources.jar") && !n.endsWith("-dev.jar");
                    })
                    .findFirst()
                    .orElse(null);
        }
    }
}
