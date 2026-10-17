package core

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// Реальная авторизация Microsoft → Xbox Live → XSTS → Minecraft.
//
// Поток:
//  1. локальный HTTP-сервер принимает код авторизации после входа в браузере
//     (адрес перенаправления http://localhost:<порт>/auth);
//  2. код обменивается на токен Microsoft (offline_access даёт токен обновления);
//  3. токен Microsoft обменивается на токен Xbox Live (XBL);
//  4. токен XBL обменивается на токен XSTS для rp://api.minecraftservices.com/;
//  5. токен XSTS обменивается на токен Minecraft;
//  6. запрашивается профиль (UUID и имя игрока).
//
// Пароль пользователя не запрашивается, не передаётся и не сохраняется: вход
// выполняется на стороне Microsoft в браузере.
const (
	authorizeURL      = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
	tokenURL          = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
	xblURL            = "https://user.auth.xboxlive.com/user/authenticate"
	xstsURL           = "https://xsts.auth.xboxlive.com/xsts/authorize"
	minecraftLoginURL = "https://api.minecraftservices.com/authentication/login_with_xbox"
	profileURL        = "https://api.minecraftservices.com/minecraft/profile"
	authScope         = "XboxLive.signin offline_access"
)

// DefaultClientID — идентификатор публичного приложения, который использует
// официальный лаунчер Minecraft.
const DefaultClientID = "00000000402b5328"

// Session — сохранённая сессия игрока.
type Session struct {
	PlayerName   string `json:"playerName"`
	UUID         string `json:"uuid"`
	AccessToken  string `json:"accessToken"`
	XUID         string `json:"xuid"`
	ClientID     string `json:"clientId"`
	ExpiresAtMs  int64  `json:"expiresAtMillis"`
	RefreshToken string `json:"refreshToken"`

	path string
}

// Valid — токен есть и его срок ещё не вышел (с запасом в минуту).
func (s *Session) Valid() bool {
	return s != nil && s.AccessToken != "" &&
		s.ExpiresAtMs > time.Now().Add(time.Minute).UnixMilli()
}

// Save сохраняет сессию рядом с настройками.
func (s *Session) Save(file string) error {
	if file != "" {
		s.path = file
	}
	if s.path == "" {
		return &Errorf{Message: "не задан файл сессии"}
	}
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return err
	}
	data, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(s.path, data, 0o600)
}

// LoadSession читает сохранённую сессию; если файла нет — возвращает nil.
func LoadSession(file string) *Session {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil
	}
	var session Session
	if json.Unmarshal(data, &session) != nil {
		return nil
	}
	session.path = file
	return &session
}

// Login выполняет полный вход через браузер.
//
// onURL получает адрес входа: лаунчер показывает его в окне и открывает в
// браузере. cancel позволяет прервать ожидание.
func Login(clientID string, onURL func(string), cancel *Cancel) (*Session, error) {
	return LoginWith(clientID, onURL, cancel, 10*time.Minute)
}

// LoginWith — то же, с явным временем ожидания.
func LoginWith(clientID string, onURL func(string), cancel *Cancel, timeout time.Duration) (*Session, error) {
	if strings.TrimSpace(clientID) == "" {
		clientID = DefaultClientID
	}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	defer listener.Close()
	port := listener.Addr().(*net.TCPAddr).Port
	redirectURI := fmt.Sprintf("http://localhost:%d/auth", port)
	state := randomState()

	codes := make(chan string, 1)
	mux := http.NewServeMux()
	mux.HandleFunc("/auth", func(writer http.ResponseWriter, request *http.Request) {
		query := request.URL.Query()
		writer.Header().Set("Content-Type", "text/html; charset=utf-8")
		if code := query.Get("code"); code != "" && query.Get("state") == state {
			codes <- code
			_, _ = writer.Write([]byte("<html><head><meta charset='utf-8'></head>" +
				"<body style='font-family:sans-serif'><h2>Вход выполнен</h2>" +
				"<p>Можно закрыть эту вкладку и вернуться в лаунчер.</p></body></html>"))
			return
		}
		reason := query.Get("error_description")
		if reason == "" {
			reason = "код подтверждения не получен"
		}
		_, _ = writer.Write([]byte("<html><head><meta charset='utf-8'></head>" +
			"<body style='font-family:sans-serif'><h2>Вход не завершён</h2><p>" +
			escapeHTML(reason) + "</p></body></html>"))
	})
	server := &http.Server{Handler: mux, ReadHeaderTimeout: 30 * time.Second}
	go func() { _ = server.Serve(listener) }()
	defer server.Close()

	target := authorizeURL + "?client_id=" + url.QueryEscape(clientID) +
		"&response_type=code&redirect_uri=" + url.QueryEscape(redirectURI) +
		"&scope=" + url.QueryEscape(authScope) +
		"&response_mode=query&prompt=select_account&state=" + url.QueryEscape(state)
	if onURL != nil {
		onURL(target)
	}

	deadline := time.Now().Add(timeout)
	var code string
	for code == "" {
		if cancel.IsCancelled() {
			return nil, ErrCancelled
		}
		if time.Now().After(deadline) {
			return nil, &Errorf{Message: "вход не завершён: браузер не вернул код за " +
				formatMinutes(timeout)}
		}
		select {
		case received := <-codes:
			code = received
		case <-time.After(500 * time.Millisecond):
		}
	}

	token, err := postForm(tokenURL, url.Values{
		"client_id":    {clientID},
		"grant_type":   {"authorization_code"},
		"code":         {code},
		"redirect_uri": {redirectURI},
		"scope":        {authScope},
	})
	if err != nil {
		return nil, err
	}
	return finishLogin(clientID, asString(token["access_token"], ""),
		asString(token["refresh_token"], ""), "")
}

// Refresh обновляет сессию по токену обновления, не трогая браузер.
func Refresh(session *Session) (*Session, error) {
	if session == nil || strings.TrimSpace(session.RefreshToken) == "" {
		return nil, &Errorf{Message: "нет токена обновления — требуется повторный вход через Microsoft"}
	}
	clientID := session.ClientID
	if strings.TrimSpace(clientID) == "" {
		clientID = DefaultClientID
	}
	token, err := postForm(tokenURL, url.Values{
		"client_id":     {clientID},
		"grant_type":    {"refresh_token"},
		"refresh_token": {session.RefreshToken},
		"scope":         {authScope},
	})
	if err != nil {
		return nil, err
	}
	refreshed, err := finishLogin(clientID, asString(token["access_token"], ""),
		asString(token["refresh_token"], session.RefreshToken), session.XUID)
	if err != nil {
		return nil, err
	}
	refreshed.path = session.path
	return refreshed, nil
}

// Validate проверяет токен запросом профиля.
func Validate(session *Session) bool {
	if session == nil || !session.Valid() {
		return false
	}
	_, err := Profile(session.AccessToken)
	return err == nil
}

// Profile — профиль игрока: отсюда берутся UUID и имя.
func Profile(accessToken string) (map[string]any, error) {
	request, err := http.NewRequest(http.MethodGet, profileURL, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("Authorization", "Bearer "+accessToken)
	request.Header.Set("Accept", "application/json")
	response, err := httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, &Errorf{Message: "профиль недоступен (код " +
			fmt.Sprint(response.StatusCode) + ") — возможно, срок действия токена истёк"}
	}
	var result any
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return nil, err
	}
	return asObject(result), nil
}

// finishLogin — общий обмен: Microsoft → XBL → XSTS → Minecraft → профиль.
func finishLogin(clientID string, microsoftToken string, refreshToken string, knownXUID string) (*Session, error) {
	if microsoftToken == "" {
		return nil, &Errorf{Message: "Microsoft не выдала токен доступа"}
	}
	xbl, err := PostJSON(xblURL, map[string]any{
		"Properties": map[string]any{
			"AuthMethod": "RPS",
			"SiteName":   "user.auth.xboxlive.com",
			"RpsTicket":  "d=" + microsoftToken,
		},
		"RelyingParty": "http://auth.xboxlive.com",
		"TokenType":    "JWT",
	}, "")
	if err != nil {
		return nil, err
	}
	xblToken := asString(xbl["Token"], "")
	if xblToken == "" {
		return nil, &Errorf{Message: "Xbox Live не выдал токен — возможно, у аккаунта нет профиля Xbox"}
	}
	userHash := firstUserHash(xbl)

	xsts, err := PostJSON(xstsURL, map[string]any{
		"Properties": map[string]any{
			"SandboxId":  "RETAIL",
			"UserTokens": []string{xblToken},
		},
		"RelyingParty": "rp://api.minecraftservices.com/",
		"TokenType":    "JWT",
	}, "")
	if err != nil {
		return nil, err
	}
	xstsToken := asString(xsts["Token"], "")
	if xstsToken == "" {
		return nil, &Errorf{Message: xstsErrorMessage(xsts)}
	}
	uhs := firstUserHash(xsts)
	if uhs == "" {
		uhs = userHash
	}

	minecraft, err := PostJSON(minecraftLoginURL, map[string]any{
		"identityToken": "XBL3.0 x=" + uhs + ";" + xstsToken,
	}, "")
	if err != nil {
		return nil, err
	}
	accessToken := asString(minecraft["access_token"], "")
	if accessToken == "" {
		return nil, &Errorf{Message: "Minecraft не выдал токен доступа"}
	}
	expiresIn := asInt64(minecraft["expires_in"], 86400)
	xuid := asString(minecraft["username"], knownXUID)

	profile, err := Profile(accessToken)
	if err != nil {
		return nil, err
	}
	return &Session{
		PlayerName:   asString(profile["name"], ""),
		UUID:         asString(profile["id"], ""),
		AccessToken:  accessToken,
		XUID:         xuid,
		ClientID:     clientID,
		ExpiresAtMs:  time.Now().UnixMilli() + expiresIn*1000,
		RefreshToken: refreshToken,
	}, nil
}

func postForm(rawURL string, form url.Values) (map[string]any, error) {
	response, err := PostForm(rawURL, form)
	if err != nil {
		return nil, err
	}
	if message, ok := response["error"].(string); ok {
		description, _ := response["error_description"].(string)
		if description == "" {
			description = message
		}
		return nil, &Errorf{Message: "Microsoft отклонила запрос: " + description}
	}
	return response, nil
}

func firstUserHash(response map[string]any) string {
	claims, ok := response["DisplayClaims"].(map[string]any)
	if !ok {
		return ""
	}
	list, ok := claims["xui"].([]any)
	if !ok || len(list) == 0 {
		return ""
	}
	first, ok := list[0].(map[string]any)
	if !ok {
		return ""
	}
	return asString(first["uhs"], "")
}

func xstsErrorMessage(xsts map[string]any) string {
	code := fmt.Sprint(xsts["XErr"])
	message := fmt.Sprint(xsts["Message"])
	switch code {
	case "2148916233":
		return "у этой учётной записи Microsoft нет профиля Xbox Live — создайте его на xbox.com и повторите вход"
	case "2148916235":
		return "Xbox Live недоступен в вашем регионе"
	case "2148916236", "2148916237":
		return "требуется подтверждение аккаунта Xbox (введите код на сайте)"
	case "2148916238":
		return "аккаунт привязан к семейной группе Microsoft; разрешите вход в настройках семьи"
	default:
		return "XSTS не выдал токен (код " + code + "): " + message
	}
}

func randomState() string {
	buffer := make([]byte, 24)
	if _, err := rand.Read(buffer); err != nil {
		return "aetherglass"
	}
	return base64.RawURLEncoding.EncodeToString(buffer)
}

func escapeHTML(value string) string {
	value = strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;").Replace(value)
	return value
}

func formatMinutes(timeout time.Duration) string {
	return fmt.Sprint(int(timeout.Minutes())) + " мин"
}
