package core

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// Золотые значения посчитаны независимой реализацией — модулем uuid из Python:
// uuid.UUID(bytes=md5("OfflinePlayer:" + имя), version=3). Совпадение значит,
// что UUID совпадает с тем, который вычисляет сам Minecraft и ванильный сервер.
func TestOfflineUUIDGolden(t *testing.T) {
	cases := map[string]string{
		"Steve":       "5627dd98-e6be-3c21-b8a8-e92344183641",
		"Alex":        "36532b5e-c442-3dbb-a24c-c7e55d0f979a",
		"Player":      "a01e3843-e521-3998-958a-f459800e4d11",
		"AetherGlass": "2c687b24-999c-3b8a-84ee-f23d3c8155e5",
	}
	for name, want := range cases {
		if got := OfflineUUID(name); got != want {
			t.Errorf("OfflineUUID(%q) = %s, ожидалось %s", name, got, want)
		}
	}
}

// Версия 3 и вариант RFC 4122 выставлены: иначе это был бы не UUID.
func TestOfflineUUIDVersionAndVariant(t *testing.T) {
	for _, name := range []string{"Steve", "a", "Player_1", "ZZZ999"} {
		got := OfflineUUID(name)
		compact := strings.ReplaceAll(got, "-", "")
		if len(compact) != 32 {
			t.Fatalf("OfflineUUID(%q): %s — не 32 шестнадцатеричных знака", name, got)
		}
		if compact[12] != '3' {
			t.Errorf("OfflineUUID(%q): версия %c, ожидалась 3", name, compact[12])
		}
		if !strings.ContainsRune("89ab", rune(compact[16])) {
			t.Errorf("OfflineUUID(%q): вариант %c, ожидался 8/9/a/b", name, compact[16])
		}
	}
}

func TestOfflineUUIDStable(t *testing.T) {
	if OfflineUUID("Steve") != OfflineUUID("Steve") {
		t.Error("одно и то же имя даёт разные UUID")
	}
	if OfflineUUID("Steve") == OfflineUUID("Alex") {
		t.Error("разные имена дали одинаковый UUID")
	}
}

func TestValidOfflineName(t *testing.T) {
	good := []string{"Steve", "Alex", "Player_1", "abc", "0123456789012345", "A_b_9", "  Steve  "}
	bad := []string{"", "ab", "01234567890123456", "Игрок", "Steve!", "имя", "Steve Alex", "a b"}
	for _, name := range good {
		if !ValidOfflineName(name) {
			t.Errorf("имя %q должно подходить", name)
		}
	}
	for _, name := range bad {
		if ValidOfflineName(name) {
			t.Errorf("имя %q не должно подходить", name)
		}
	}
}

func TestNewOfflineSession(t *testing.T) {
	session, err := NewOfflineSession("  Steve  ")
	if err != nil {
		t.Fatalf("неожиданная ошибка: %v", err)
	}
	if session.PlayerName != "Steve" {
		t.Errorf("имя не обрезано по краям: %q", session.PlayerName)
	}
	if session.UUID != OfflineUUID("Steve") {
		t.Errorf("UUID = %s, ожидался %s", session.UUID, OfflineUUID("Steve"))
	}
	if !session.Offline {
		t.Error("признак Offline не выставлен")
	}
	// Токена быть не должно: именно отсутствие токена и делает профиль офлайн.
	if session.AccessToken != "" {
		t.Errorf("у офлайн-сессии есть токен: %q", session.AccessToken)
	}
	if session.RefreshToken != "" {
		t.Error("у офлайн-сессии есть токен обновления")
	}
	if !session.Valid() {
		t.Error("офлайн-сессия с именем должна быть действительной")
	}
}

func TestNewOfflineSessionRejects(t *testing.T) {
	for _, name := range []string{"", "  ", "ab", "Игрок", "Steve!!!"} {
		if session, err := NewOfflineSession(name); err == nil {
			t.Errorf("имя %q принято, хотя не должно (сессия %+v)", name, session)
		} else if err.Error() == "" {
			t.Error("ошибка без текста")
		}
	}
}

// Сессия должна переживать запись на диск и чтение, иначе профиль теряется
// при каждом запуске лаунчера.
func TestOfflineSessionSaveLoad(t *testing.T) {
	file := filepath.Join(t.TempDir(), "auth.json")
	session, err := NewOfflineSession("AetherGlass")
	if err != nil {
		t.Fatal(err)
	}
	if err := session.Save(file); err != nil {
		t.Fatalf("запись: %v", err)
	}
	loaded := LoadSession(file)
	if loaded == nil {
		t.Fatal("сессия не прочиталась")
	}
	if !loaded.Offline {
		t.Error("признак Offline потерялся при сохранении")
	}
	if loaded.PlayerName != "AetherGlass" || loaded.UUID != session.UUID {
		t.Errorf("прочитано имя %q и UUID %s", loaded.PlayerName, loaded.UUID)
	}
	if !loaded.Valid() {
		t.Error("прочитанная офлайн-сессия недействительна")
	}
	// Офлайн-сессию нельзя «обновить» — у неё нет токена обновления.
	if _, err := Refresh(loaded); err == nil {
		t.Error("офлайн-сессию удалось обновить, хотя не должно")
	}
}

func TestSessionValidRules(t *testing.T) {
	offline := &Session{Offline: true}
	if offline.Valid() {
		t.Error("офлайн-сессия без имени не должна быть действительной")
	}
	offline.PlayerName = "Steve"
	offline.UUID = OfflineUUID("Steve")
	if !offline.Valid() {
		t.Error("офлайн-сессия с именем и UUID должна быть действительной")
	}

	expired := &Session{AccessToken: "tok", ExpiresAtMs: time.Now().Add(-time.Hour).UnixMilli()}
	if expired.Valid() {
		t.Error("просроченная сессия не должна быть действительной")
	}
	fresh := &Session{AccessToken: "tok", ExpiresAtMs: time.Now().Add(time.Hour).UnixMilli()}
	if !fresh.Valid() {
		t.Error("свежая сессия Microsoft должна быть действительной")
	}
	if (&Session{ExpiresAtMs: time.Now().Add(time.Hour).UnixMilli()}).Valid() {
		t.Error("сессия без токена не должна быть действительной")
	}
}

// Главная проверка: командная строка игры для офлайн-профиля.
//
// Пустых аргументов быть не должно ни в одном из режимов: пустой элемент
// угодил бы в разбор как лишний аргумент и поломал бы запуск.
func TestPlanOfflineArguments(t *testing.T) {
	directory := t.TempDir()
	if err := os.MkdirAll(directory, 0o755); err != nil {
		t.Fatal(err)
	}
	config := NewConfig()
	config.GameDirectory = directory
	config.MinMemoryMb = 512
	config.MaxMemoryMb = 2048

	installation := &Installation{
		VersionID:      DefaultMinecraftVer,
		FabricID:       "fabric-loader-0.19.5-1.21.11",
		MainClass:      "net.fabricmc.loader.impl.launch.knot.KnotClient",
		Classpath:      []string{filepath.Join(directory, "client.jar")},
		AssetIndexID:   "26",
		JavaExecutable: filepath.Join(directory, "javaw.exe"),
		// Аргументы — в точности того же вида, что в официальном JSON версии.
		GameArguments: []any{
			"--username", "${auth_player_name}",
			"--uuid", "${auth_uuid}",
			"--accessToken", "${auth_access_token}",
			"--clientId", "${clientid}",
			"--xuid", "${auth_xuid}",
			"--userType", "${user_type}",
			"--versionType", "${version_type}",
		},
	}

	launcher := &Launcher{}
	paths := NewPaths(directory)

	offline, err := NewOfflineSession("Steve")
	if err != nil {
		t.Fatal(err)
	}
	plan, err := launcher.Plan(installation, paths, config, offline)
	if err != nil {
		t.Fatalf("план для офлайн-профиля: %v", err)
	}
	for _, argument := range plan.Command {
		if argument == "" {
			t.Fatalf("в командной строке пустой аргумент: %v", plan.Command)
		}
	}
	want := map[string]string{
		"--username":    "Steve",
		"--uuid":        OfflineUUID("Steve"),
		"--accessToken": "0",
		"--xuid":        "0",
		"--userType":    "legacy",
		"--clientId":    OfflineUUID("Steve"),
	}
	assertFlags(t, plan.Command, want)

	// Вход через Microsoft: тип учётной записи и токен — свои.
	microsoft := &Session{
		PlayerName:  "Real",
		UUID:        "11111111-2222-3333-4444-555555555555",
		AccessToken: "real-token",
		XUID:        "2535467",
		ClientID:    DefaultClientID,
		ExpiresAtMs: time.Now().Add(time.Hour).UnixMilli(),
	}
	plan, err = launcher.Plan(installation, paths, config, microsoft)
	if err != nil {
		t.Fatalf("план для входа Microsoft: %v", err)
	}
	for _, argument := range plan.Command {
		if argument == "" {
			t.Fatalf("в командной строке пустой аргумент: %v", plan.Command)
		}
	}
	assertFlags(t, plan.Command, map[string]string{
		"--username":    "Real",
		"--uuid":        "11111111-2222-3333-4444-555555555555",
		"--accessToken": "real-token",
		"--userType":    "msa",
		"--xuid":        "2535467",
		"--clientId":    DefaultClientID,
	})

	// Без сессии игра не запускается вовсе.
	if _, err := launcher.Plan(installation, paths, config, nil); err == nil {
		t.Error("план без сессии построился, хотя не должен")
	}
}

// assertFlags проверяет, что после каждого флага идёт ожидаемое значение.
func assertFlags(t *testing.T, command []string, want map[string]string) {
	t.Helper()
	for flag, value := range want {
		found := false
		for i := 0; i+1 < len(command); i++ {
			if command[i] == flag {
				found = true
				if command[i+1] != value {
					t.Errorf("%s: получено %q, ожидалось %q", flag, command[i+1], value)
				}
				break
			}
		}
		if !found {
			t.Errorf("флаг %s не попал в командную строку", flag)
		}
	}
}
