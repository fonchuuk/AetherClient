package com.aetherglass.launcher.auth;

/**
 * Идентификатор приложения Azure (client id), используемый в потоке входа.
 *
 * <p>Вход в Minecraft выполняется через Microsoft Account. Для OAuth нужен идентификатор
 * приложения Azure AD, у которого есть разрешение на Xbox Live. По умолчанию используется
 * общеизвестный идентификатор официального лаунчера Minecraft
 * ({@value #DEFAULT_ID}) — он позволяет пройти поток без секрета клиента,
 * потому что это публичное приложение.</p>
 *
 * <p>Если вы хотите использовать собственное приложение Azure, укажите его идентификатор
 * в поле «Client ID» на вкладке настроек: приложению потребуется разрешение
 * {@code XboxLive.signin} и тип «публичный клиент».</p>
 */
public final class MicrosoftClientId {
    /** Идентификатор публичного приложения, который использует официальный лаунчер Minecraft. */
    public static final String DEFAULT_ID = "00000000402b5328";

    private MicrosoftClientId() {
    }
}
