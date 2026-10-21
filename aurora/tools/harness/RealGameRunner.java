import com.aetherglass.launcher.auth.AuthSession;
import com.aetherglass.launcher.core.GameInstaller;
import com.aetherglass.launcher.core.GameLauncher;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.LauncherPaths;
import com.aetherglass.launcher.core.ModInstaller;
import com.aetherglass.launcher.util.Http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class RealGameRunner {
    private static final String MC_VERSION = "1.21.11";
    private static final String LOADER_VERSION = "0.19.5";

    public static void main(String[] args) throws Exception {
        Path base = Path.of("/var/tmp/mc-run");
        int width = 1280;
        int height = 720;

        LauncherPaths paths = new LauncherPaths(base);
        LauncherConfig config = LauncherConfig.defaults()
                .gameDirectory(base)
                .minMemoryMb(384)
                .maxMemoryMb(640)
                .windowWidth(width)
                .windowHeight(height)
                .javaExecutable(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                .selectedVersion(MC_VERSION);

        Http.CancelFlag never = () -> false;
        Http.ProgressListener files = (name, done, total) -> {};
        GameInstaller.Progress progress = new GameInstaller.Progress() {
            @Override
            public void stage(String text) {
                System.out.println("[этап] " + text);
            }
            @Override
            public void onFile(String name, long done, long total) {}
        };

        GameInstaller installer = new GameInstaller(paths, MC_VERSION, LOADER_VERSION, progress, never);
        GameInstaller.Installation installation = installer.install();

        ModInstaller mods = ModInstaller.fromResource(paths, files, never);
        mods.install();
        Path modsDir = base.resolve("mods");
        try (var s = Files.list(modsDir)) {
            for (Path p : s.toList()) {
                String fn = p.getFileName().toString().toLowerCase();
                if (!fn.startsWith("fabric-api")) {
                    Files.deleteIfExists(p);
                }
            }
        }
        Files.copy(Path.of("/var/tmp/workspace/AetherGlass.jar"),
                modsDir.resolve("aetherglass-1.21.11.jar"),
                StandardCopyOption.REPLACE_EXISTING);

        AuthSession offline = new AuthSession("del1very", UUID.randomUUID().toString(),
                "offline", "", "", System.currentTimeMillis() + 3_600_000L, "");

        GameLauncher launcher = new GameLauncher();
        GameLauncher.LaunchPlan plan = launcher.plan(installation, paths, config, offline,
                Path.of(config.javaExecutable()));

        Process process = launcher.launch(plan, line -> {
            System.out.println("[игра] " + line);
        }, () -> System.out.println("[стенд] игра завершилась"));

        long deadline = System.currentTimeMillis() + 300_000L;
        while (System.currentTimeMillis() < deadline && process.isAlive()) {
            Thread.sleep(1000L);
        }
        launcher.stop();
    }
}
