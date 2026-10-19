package com.aetherglass.launcher.auth;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Реальная авторизация Microsoft → Xbox Live → XSTS → Minecraft.
 *
 * <p>Поток:</p>
 * <ol>
 *   <li>локальный HTTP-сервер принимает код авторизации после входа в браузере
 *       (адрес перенаправления {@code http://localhost:<порт>/auth});</li>
 *   <li>код обменивается на токен Microsoft ({@code offline_access} даёт токен обновления);</li>
 *   <li>токен Microsoft обменивается на токен Xbox Live (XBL);</li>
 *   <li>токен XBL обменивается на токен XSTS для {@code rp://api.minecraftservices.com/};</li>
 *   <li>токен XSTS обменивается на токен Minecraft;</li>
 *   <li>запрашивается профиль (UUID и имя игрока).</li>
 * </ol>
 *
 * <p>Пароль пользователя не запрашивается, не передаётся и не сохраняется: вход выполняется
 * на стороне Microsoft в браузере.</p>
 */
public final class MicrosoftAuth {
    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String SCOPE = "XboxLive.signin offline_access";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    private MicrosoftAuth() {
    }

    /** Способ показать ссылку пользователю: открыть браузер или выдать адрес в интерфейс. */
    public interface UrlHandler {
        void open(String url);
    }

    /** Открывает ссылку в системном браузере. */
    public static final UrlHandler SYSTEM_BROWSER = url -> {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | RuntimeException exception) {
            // Браузер не открылся — ссылка уже показана в окне, пользователь может открыть её сам.
        }
    };

    /**
     * Выполняет полный вход.
     *
     * @param clientId   идентификатор приложения Azure (публичный клиент)
     * @param urlHandler открывает ссылку входа; получает адрес даже если браузер недоступен
     * @param cancel     позволяет прервать ожидание
     * @return сессия с именем, UUID и токеном Minecraft
     */
    public static AuthSession login(String clientId, UrlHandler urlHandler, Http.CancelFlag cancel)
            throws IOException, InterruptedException {
        return login(clientId, urlHandler, cancel, DEFAULT_TIMEOUT);
    }

    public static AuthSession login(String clientId, UrlHandler urlHandler, Http.CancelFlag cancel, Duration timeout)
            throws IOException, InterruptedException {
        String effectiveClientId = clientId == null || clientId.isBlank()
                ? MicrosoftClientId.DEFAULT_ID : clientId.trim();
        BlockingQueue<String> codes = new ArrayBlockingQueue<>(1);
        String state = randomState();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirectUri = "http://localhost:" + server.getAddress().getPort() + "/auth";
        server.createContext("/auth", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            Map<String, String> parameters = parseQuery(query);
            String response;
            if (parameters.containsKey("code") && state.equals(parameters.get("state"))) {
                codes.offer(parameters.get("code"));
                response = "<html><head><meta charset='utf-8'></head><body style='font-family:sans-serif'>"
                        + "<h2>Вход выполнен</h2><p>Можно закрыть эту вкладку и вернуться в лаунчер.</p></body></html>";
            } else {
                response = "<html><head><meta charset='utf-8'></head><body style='font-family:sans-serif'>"
                        + "<h2>Вход не завершён</h2><p>" + escape(parameters.getOrDefault("error_description",
                        "код подтверждения не получен"))
                        + "</p></body></html>";
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            String authorizeUrl = AUTHORIZE_URL
                    + "?client_id=" + encode(effectiveClientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&scope=" + encode(SCOPE)
                    + "&response_mode=query"
                    + "&prompt=select_account"
                    + "&state=" + encode(state);
            if (urlHandler != null) {
                urlHandler.open(authorizeUrl);
            }

            String code = null;
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (code == null && System.currentTimeMillis() < deadline) {
                code = codes.poll(500, TimeUnit.MILLISECONDS);
                if (cancel != null && cancel.isCancelled()) {
                    throw new Http.CancelledException();
                }
            }
            if (code == null) {
                throw new IOException("вход не завершён: браузер не вернул код за "
                        + timeout.toMinutes() + " мин");
            }
            Map<String, Object> token = exchangeCode(effectiveClientId, code, redirectUri);
            String refreshToken = Json.string(token, "refresh_token", null);
            return finishLogin(effectiveClientId, Json.requireString(token, "access_token"), refreshToken, null);
        } finally {
            server.stop(0);
        }
    }

    /** Обменивает код авторизации на токен Microsoft. */
    private static Map<String, Object> exchangeCode(String clientId, String code, String redirectUri)
            throws IOException, InterruptedException {
        String form = "client_id=" + encode(clientId)
                + "&grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(SCOPE);
        Map<String, Object> response = Http.postForm(TOKEN_URL, form);
        if (response.containsKey("error")) {
            throw new IOException("Microsoft отклонила код входа: "
                    + Json.string(response, "error_description", Json.string(response, "error", "?")));
        }
        return response;
    }

    /** Обновляет сессию по токену обновления Microsoft. */
    public static AuthSession refresh(AuthSession session) throws IOException, InterruptedException {
        String refreshToken = session.refreshToken();
        String clientId = session.clientId() == null || session.clientId().isBlank()
                ? MicrosoftClientId.DEFAULT_ID : session.clientId();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IOException("нет токена обновления — требуется повторный вход через Microsoft");
        }
        String form = "client_id=" + encode(clientId)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + encode(refreshToken)
                + "&scope=" + encode(SCOPE);
        Map<String, Object> response = Http.postForm(TOKEN_URL, form);
        if (response.containsKey("error")) {
            throw new IOException("не удалось обновить токен: "
                    + Json.string(response, "error_description", Json.string(response, "error", "?")));
        }
        String newRefresh = Json.string(response, "refresh_token", refreshToken);
        return finishLogin(clientId, Json.requireString(response, "access_token"), newRefresh, session.xuid());
    }

    /** Проверяет токен Minecraft запросом профиля. */
    public static boolean validate(AuthSession session) {
        if (session == null || !session.valid()) {
            return false;
        }
        try {
            profile(session.accessToken());
            return true;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            return false;
        }
    }

    /** Общий обмен: Microsoft → XBL → XSTS → Minecraft → профиль. */
    private static AuthSession finishLogin(String clientId, String microsoftToken, String refreshToken, String knownXuid)
            throws IOException, InterruptedException {
        Map<String, Object> xbl = Json.asObject(Http.postJson(XBL_URL, Map.of(
                "Properties", Map.of(
                        "AuthMethod", "RPS",
                        "SiteName", "user.auth.xboxlive.com",
                        "RpsTicket", "d=" + microsoftToken),
                "RelyingParty", "http://auth.xboxlive.com",
                "TokenType", "JWT"), null));
        if (xbl == null || xbl.get("Token") == null) {
            throw new IOException("Xbox Live не выдал токен — возможно, у аккаунта нет профиля Xbox");
        }
        String xblToken = Json.requireString(xbl, "Token");
        String userHash = firstUserHash(xbl);

        Map<String, Object> xsts = Json.asObject(Http.postJson(XSTS_URL, Map.of(
                "Properties", Map.of(
                        "SandboxId", "RETAIL",
                        "UserTokens", List.of(xblToken)),
                "RelyingParty", "rp://api.minecraftservices.com/",
                "TokenType", "JWT"), null));
        if (xsts == null || xsts.get("Token") == null) {
            throw new IOException(xstsErrorMessage(xsts));
        }
        String xstsToken = Json.requireString(xsts, "Token");
        String uhs = firstUserHash(xsts);
        if (uhs == null || uhs.isEmpty()) {
            uhs = userHash;
        }

        Map<String, Object> minecraft = Json.asObject(Http.postJson(MINECRAFT_LOGIN_URL,
                Map.of("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken), null));
        if (minecraft == null || minecraft.get("access_token") == null) {
            throw new IOException("Minecraft не выдал токен: " + String.valueOf(minecraft));
        }
        String accessToken = Json.requireString(minecraft, "access_token");
        long expiresIn = Json.longValue(minecraft, "expires_in", 86400L);
        String xuid = minecraft.get("username") instanceof String name ? name : knownXuid;

        Map<String, Object> profile = profile(accessToken);
        String uuid = Json.requireString(profile, "id");
        String playerName = Json.requireString(profile, "name");
        return new AuthSession(playerName, uuid, accessToken, xuid, clientId,
                System.currentTimeMillis() + expiresIn * 1000L, refreshToken);
    }

    private static String firstUserHash(Map<String, Object> response) {
        Object claims = response.get("DisplayClaims");
        if (!(claims instanceof Map)) {
            return null;
        }
        Object xui = Json.asObject(claims).get("xui");
        if (!(xui instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map)) {
            return null;
        }
        return Json.string(Json.asObject(first), "uhs", null);
    }

    private static String xstsErrorMessage(Map<String, Object> xsts) {
        if (xsts != null) {
            String code = String.valueOf(xsts.get("XErr"));
            return switch (code) {
                case "2148916233" -> "у этой учётной записи Microsoft нет профиля Xbox Live — "
                        + "создайте его на xbox.com и повторите вход";
                case "2148916235" -> "Xbox Live недоступен в вашем регионе";
                case "2148916236", "2148916237" -> "требуется подтверждение аккаунта Xbox (введите код на сайте)";
                case "2148916238" -> "аккаунт привязан к семейной группе Microsoft; "
                        + "разрешите вход в настройках семьи";
                default -> "XSTS не выдал токен (код " + code + "): " + String.valueOf(xsts.get("Message"));
            };
        }
        return "XSTS не выдал токен";
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            result.put(urlDecode(pair.substring(0, index)), urlDecode(pair.substring(index + 1)));
        }
        return result;
    }

    private static String urlDecode(String value) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '+') {
                output.write(' ');
            } else if (character == '%' && i + 2 < value.length()) {
                output.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                output.write(character);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Запрашивает профиль Minecraft: именно отсюда берутся UUID и имя игрока. */
    static Map<String, Object> profile(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = Http.client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("профиль недоступен (код " + response.statusCode()
                    + ") — возможно, срок действия токена истёк");
        }
        return Json.asObject(Json.parse(response.body()));
    }
}
