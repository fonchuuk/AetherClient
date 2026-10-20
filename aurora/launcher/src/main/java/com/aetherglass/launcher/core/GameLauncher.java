package com.aetherglass.launcher.core;

import com.aetherglass.launcher.auth.AuthSession;
import com.aetherglass.launcher.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Формирует командную строку и запускает игру.
 *
 * <p>Аргументы берутся из официального JSON версии Mojang и из профиля Fabric — ничего не выдумано:
 * подставляются только значения токенов ({@code ${classpath}} и подобные), как это делает
 * официальный лаунчер.</p>
 *
 * <p>Одновременно может быть запущен только один экземпляр игры: повторный запуск
 * отклоняется, пока предыдущий процесс жив.</p>
 */
public final class GameLauncher {
    public static final String LAUNCHER_NAME = "AetherGlassLauncher";
    public static final String LAUNCHER_VERSION = "1.0.8";

    private final AtomicReference<Process> current = new AtomicReference<>();

    public record LaunchPlan(Path javaExecutable, List<String> command, Path workingDirectory) {
    }

    /** Запущена ли игра в данный момент. */
    public boolean isRunning() {
        Process process = this.current.get();
        return process != null && process.isAlive();
    }

    /** Завершает запущенный процесс игры, если он есть. */
    public void stop() {
        Process process = this.current.getAndSet(null);
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public LaunchPlan plan(GameInstaller.Installation installation,
                           LauncherPaths paths,
                           LauncherConfig config,
                           AuthSession session,
                           Path javaExecutable) throws IOException {
        if (session == null || !session.valid()) {
            throw new IOException("нет действительной учётной записи: войдите через Microsoft");
        }
        if (javaExecutable == null) {
            throw new IOException("не выбрана Java для запуска");
        }
        Path gameDir = paths.base();
        Files.createDirectories(gameDir);

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("launcher_name", LAUNCHER_NAME);
        tokens.put("launcher_version", LAUNCHER_VERSION);
        tokens.put("classpath", classpathValue(installation.classpath()));
        tokens.put("classpath_separator", java.io.File.pathSeparator);
        tokens.put("library_directory", paths.libraries().toString());
        tokens.put("natives_directory", installation.nativesDir().toString());
        tokens.put("version_name", installation.fabricId());
        tokens.put("game_directory", gameDir.toString());
        tokens.put("assets_root", paths.assets().toString());
        tokens.put("assets_index_name", installation.assetIndexId());
        tokens.put("auth_player_name", session.playerName());
        tokens.put("auth_uuid", session.uuid());
        tokens.put("auth_access_token", session.offline() ? "0" : session.accessToken());
        tokens.put("user_type", session.offline() ? "legacy" : "msa");
        tokens.put("auth_xuid", session.xuid() == null ? "" : session.xuid());
        tokens.put("clientid", session.clientId() == null ? "" : session.clientId());
        tokens.put("version_type", LAUNCHER_NAME);
        tokens.put("resolution_width", String.valueOf(config.windowWidth()));
        tokens.put("resolution_height", String.valueOf(config.windowHeight()));

        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("is_demo_user", Boolean.FALSE);
        features.put("has_custom_resolution", config.windowWidth() > 0 && config.windowHeight() > 0);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-Xms" + config.minMemoryMb() + "M");
        command.add("-Xmx" + config.maxMemoryMb() + "M");
        command.add("-Dfile.encoding=UTF-8");
        if (installation.logConfigFile() != null && installation.logConfigArgument() != null) {
            command.add(substitute(installation.logConfigArgument(),
                    Map.of("path", installation.logConfigFile().toString())));
        }
        appendArguments(command, installation.jvmArguments(), tokens, features);
        command.add(installation.mainClass());
        appendArguments(command, installation.gameArguments(), tokens, features);
        return new LaunchPlan(javaExecutable, List.copyOf(command), gameDir);
    }

    /**
     * Запускает игру.
     *
     * @param outputConsumer получает строки вывода игры; может быть null
     * @throws IllegalStateException если игра уже запущена
     */
    public Process launch(LaunchPlan plan, Consumer<String> outputConsumer,
                          Runnable onExit) throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("игра уже запущена — дождитесь её завершения");
        }
        Path logsDir = plan.workingDirectory().resolve("logs");
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("launcher-game.log");
        Files.writeString(logFile, "", StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(plan.command());
        builder.directory(plan.workingDirectory().toFile());
        builder.redirectErrorStream(true);
        // Перенаправляем вывод в файл на диске, а не в pipe родительского процесса:
        // когда лаунчер закрывается после запуска игры (System.exit(0)), у процесса
        // Minecraft не обрывается stdout/stderr pipe в Windows/Linux.
        builder.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
        Process process = builder.start();
        this.current.set(process);
        Thread reader = new Thread(() -> readOutput(process, logFile, outputConsumer, onExit), "aurora-game-output");
        reader.setDaemon(true);
        reader.start();
        return process;
    }

    private void readOutput(Process process, Path logFile, Consumer<String> outputConsumer, Runnable onExit) {
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            while (process.isAlive()) {
                String line = reader.readLine();
                if (line != null) {
                    if (outputConsumer != null) {
                        outputConsumer.accept(line);
                    }
                } else {
                    try {
                        Thread.sleep(60L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (outputConsumer != null) {
                    outputConsumer.accept(line);
                }
            }
        } catch (IOException ignored) {
        }
        try {
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        this.current.compareAndSet(process, null);
        if (onExit != null) {
            onExit.run();
        }
    }

    private static void appendArguments(List<String> target, List<Object> arguments,
                                        Map<String, String> tokens, Map<String, Boolean> features) {
        for (Object entry : arguments) {
            if (entry instanceof String text) {
                target.add(substitute(text, tokens));
            } else if (entry instanceof Map) {
                Map<String, Object> object = Json.asObject(entry);
                if (!Rules.allows(object, features)) {
                    continue;
                }
                Object value = object.get("value");
                if (value instanceof String text) {
                    target.add(substitute(text, tokens));
                } else if (value instanceof List<?> list) {
                    for (Object item : list) {
                        target.add(substitute(String.valueOf(item), tokens));
                    }
                }
            }
        }
    }

    /** Подставляет значения токенов {@code ${…}} в аргумент. */
    public static String substitute(String text, Map<String, String> tokens) {
        String result = text;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            String placeholder = "${" + token.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, token.getValue() == null ? "" : token.getValue());
            }
        }
        return result;
    }

    private static String classpathValue(List<Path> entries) {
        StringBuilder builder = new StringBuilder();
        for (Path entry : entries) {
            if (builder.length() > 0) {
                builder.append(java.io.File.pathSeparator);
            }
            builder.append(entry.toString());
        }
        return builder.toString();
    }
}
