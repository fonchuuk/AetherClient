package com.aetherglass.client.modules.impl;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Модуль Discord Rich Presence: активность «AetherVisuals» с логотипом (PNG / анимированный GIF).
 *
 * <p>Работает в отдельном daemon-потоке: соединение с Discord открывается один раз
 * (named pipe {@code discord-ipc-0..9} на Windows, unix-сокет на Linux/macOS), рукопожатие
 * отправляется одно, дальше уходят только кадры {@code SET_ACTIVITY} — и только когда статус
 * реально изменился. Рендер-поток и тики при этом не нагружаются вовсе.</p>
 */
public final class DiscordRpcModule extends Module {
    public static final String ACTIVITY_NAME = "AetherVisuals";

    /**
     * Application ID из Discord Developer Portal (General Information → Application ID).
     * Приложение должно называться {@code AetherVisuals} — именно это имя Discord показывает
     * в статусе. Свой ID можно вписать в {@code aetherglass.conf}: {@code discord.appId}.
     */
    public static final String DEFAULT_APP_ID = "1352684920194826340";

    /**
     * Объёмное вращающееся лого (GIF по внешней ссылке — Discord анимирует внешние URL
     * в {@code large_image}; загруженные в портал ассеты поддерживают только статичные форматы).
     */
    public static final String DEFAULT_LOGO_GIF = "https://x0.at/Bjhw.gif";

    /** Статичное лого по умолчанию пустое: тогда берётся иконка приложения из Art Assets. */
    public static final String DEFAULT_LOGO_STATIC = "";

    private static final int MAX_TEXT = 128;
    private static final long RETRY_MS = 10_000L;
    private static final long IDLE_MS = 2_000L;

    private final BooleanSetting showState = register(new BooleanSetting(
            "showState", "Показывать статус игры", "Отображать «В главном меню», «Одиночная игра» или сервер", true, this::onSettingChanged));
    private final BooleanSetting showNick = register(new BooleanSetting(
            "showNick", "Показывать ник", "Отображать текущий никнейм и версию 1.21.11", true, this::onSettingChanged));
    private final BooleanSetting showTimer = register(new BooleanSetting(
            "showTimer", "Таймер времени", "Отсчёт времени в активности AetherVisuals", true, this::onSettingChanged));
    private final BooleanSetting animatedLogo = register(new BooleanSetting(
            "animatedLogo", "Анимированное лого (GIF)", "Объёмное вращающееся лого AetherVisuals в статусе Discord", true, this::onSettingChanged));

    private final long startEpochSec = System.currentTimeMillis() / 1000L;
    private volatile boolean running;
    private volatile boolean dirty = true;
    private boolean hintShown;
    private Thread worker;

    public DiscordRpcModule() {
        super("discord_rpc", "Discord RPC",
                "Активность «AetherVisuals» и ваше лого (PNG / GIF) в профиле Discord",
                ModuleCategory.INTERFACE);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        this.dirty = true;
    }

    @Override
    protected void onEnable() {
        this.running = true;
        this.dirty = true;
        this.hintShown = false;
        if (this.worker == null || !this.worker.isAlive()) {
            this.worker = new Thread(this::runLoop, "Aether-DiscordRPC");
            this.worker.setDaemon(true);
            this.worker.start();
        }
    }

    @Override
    protected void onDisable() {
        this.running = false;
        Thread thread = this.worker;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public String resolveLogoAsset() {
        String custom = ModConfig.get().getString("discord.logo", "").trim();
        if (!custom.isEmpty()) {
            return custom;
        }
        return this.animatedLogo.get() ? DEFAULT_LOGO_GIF : DEFAULT_LOGO_STATIC;
    }

    public String resolveAppId() {
        String custom = ModConfig.get().getString("discord.appId", "").trim();
        if (!custom.isEmpty() && custom.chars().allMatch(Character::isDigit)) {
            return custom;
        }
        return DEFAULT_APP_ID;
    }

    /**
     * JSON ровно в формате Discord IPC: {@code {"cmd":"SET_ACTIVITY","args":{"pid":…,"activity":{…}},"nonce":"…"}}.
     * Никаких лишних полей: имя активности Discord берёт из названия приложения в портале.
     */
    public String buildActivityJson(String details, String state, long startSec) {
        StringBuilder sb = new StringBuilder(320);
        sb.append("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":").append(ProcessHandle.current().pid())
          .append(",\"activity\":{");
        boolean first = true;
        if (this.showState.get() && details != null && !details.isEmpty()) {
            sb.append("\"details\":\"").append(escape(trim(details))).append('"');
            first = false;
        }
        if (this.showNick.get() && state != null && !state.isEmpty()) {
            if (!first) {
                sb.append(',');
            }
            sb.append("\"state\":\"").append(escape(trim(state))).append('"');
            first = false;
        }
        if (this.showTimer.get() && startSec > 0L) {
            if (!first) {
                sb.append(',');
            }
            sb.append("\"timestamps\":{\"start\":").append(startSec).append('}');
            first = false;
        }
        String logo = resolveLogoAsset();
        if (!logo.isEmpty()) {
            if (!first) {
                sb.append(',');
            }
            sb.append("\"assets\":{\"large_image\":\"").append(escape(trim(logo)))
              .append("\",\"large_text\":\"").append(ACTIVITY_NAME).append(" • 1.21.11\"}");
        }
        sb.append("}},\"nonce\":\"").append(System.nanoTime()).append("\"}");
        return sb.toString();
    }

    /** Бинарный кадр IPC: 4 байта опкода + 4 байта длины (LE) + UTF-8 JSON. */
    public static byte[] packIpcFrame(int opcode, String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    private void runLoop() {
        Ipc ipc = null;
        String lastKey = "";
        while (this.running) {
            try {
                if (ipc == null) {
                    ipc = Ipc.connect();
                    if (ipc == null) {
                        notifyHintOnce();
                        Thread.sleep(RETRY_MS);
                        continue;
                    }
                    ipc.handshake(resolveAppId());
                    AuroraLog.info("Discord RPC: соединение установлено (app id " + resolveAppId() + ")");
                    lastKey = "";
                }
                String details = currentDetails();
                String state = currentState();
                String key = details + '|' + state + '|' + this.showState.get() + '|' + this.showNick.get()
                        + '|' + this.showTimer.get() + '|' + this.animatedLogo.get() + '|' + resolveLogoAsset();
                if (this.dirty || !key.equals(lastKey)) {
                    this.dirty = false;
                    lastKey = key;
                    ipc.send(buildActivityJson(details, state, this.startEpochSec));
                }
                Thread.sleep(IDLE_MS);
            } catch (InterruptedException ignored) {
                break;
            } catch (Throwable throwable) {
                AuroraLog.error("Discord RPC: обрыв соединения", throwable);
                if (ipc != null) {
                    ipc.close();
                    ipc = null;
                }
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
        if (ipc != null) {
            ipc.close();
        }
    }

    private void notifyHintOnce() {
        if (this.hintShown) {
            return;
        }
        this.hintShown = true;
        AuroraLog.warn("Discord RPC: Discord не найден (запущен ли клиент?) — проверьте discord.appId");
        try {
            NotificationManager.get().push("Discord RPC",
                    "Discord не найден или неверный Application ID", NotificationManager.Type.INFO, 2.6F);
        } catch (Throwable ignored) {
        }
    }

    private static String currentDetails() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return "В главном меню";
            }
            ServerData srv = mc.getCurrentServer();
            if (srv != null && srv.ip != null && !srv.ip.isEmpty()) {
                return "Сервер: " + srv.ip;
            }
            return "Одиночная игра";
        } catch (Throwable throwable) {
            return "В главном меню";
        }
    }

    private static String currentState() {
        try {
            Minecraft mc = Minecraft.getInstance();
            String nick = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "Player";
            return nick + " • 1.21.11";
        } catch (Throwable throwable) {
            return "1.21.11";
        }
    }

    private static String trim(String value) {
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Живое соединение с Discord: одно рукопожатие, дальше только кадры SET_ACTIVITY. */
    private static final class Ipc {
        private RandomAccessFile pipe;
        private SocketChannel socket;

        static Ipc connect() {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            for (int i = 0; i < 10; i++) {
                try {
                    Ipc ipc = new Ipc();
                    if (windows) {
                        ipc.pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    } else {
                        String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
                        Path path = Path.of(runtimeDir != null && !runtimeDir.isEmpty() ? runtimeDir : "/tmp",
                                "discord-ipc-" + i);
                        if (!Files.exists(path)) {
                            continue;
                        }
                        ipc.socket = SocketChannel.open(StandardProtocolFamily.UNIX);
                        ipc.socket.connect(UnixDomainSocketAddress.of(path));
                    }
                    return ipc;
                } catch (Throwable ignored) {
                    // пробуем следующий сокет
                }
            }
            return null;
        }

        void handshake(String appId) throws Exception {
            write(packIpcFrame(0, "{\"v\":1,\"client_id\":\"" + escape(appId) + "\"}"));
        }

        void send(String activityJson) throws Exception {
            write(packIpcFrame(1, activityJson));
        }

        private void write(byte[] data) throws Exception {
            if (this.pipe != null) {
                this.pipe.write(data);
                this.pipe.getFD().sync();
            } else if (this.socket != null) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    this.socket.write(buffer);
                }
            }
        }

        void close() {
            try {
                if (this.pipe != null) {
                    this.pipe.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (this.socket != null) {
                    this.socket.close();
                }
            } catch (Throwable ignored) {
            }
            this.pipe = null;
            this.socket = null;
        }
    }
}
