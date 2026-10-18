package core

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Тесты проверяют логику лаунчера: каталоги, настройки, правила Mojang,
// подстановку аргументов, разбор версии Java и встроенный манифест модов.
// Интерфейс здесь не участвует — его нельзя проверить без Windows.

func TestPaths(t *testing.T) {
	paths := NewPaths(filepath.Join("C:", "games", "aether"))
	if got := paths.Mods(); !strings.HasSuffix(got, filepath.Join("aether", "mods")) {
		t.Fatalf("каталог модов: %s", got)
	}
	if got := paths.AssetObject("a1b2c3"); !strings.HasSuffix(got,
		filepath.Join("assets", "objects", "a1", "a1b2c3")) {
		t.Fatalf("путь к объекту ресурсов: %s", got)
	}
	library, err := paths.Library("net.fabricmc:fabric-loader:0.18.4", "")
	if err != nil {
		t.Fatalf("библиотека: %v", err)
	}
	if !strings.HasSuffix(library, filepath.Join("libraries", "net", "fabricmc",
		"fabric-loader", "0.18.4", "fabric-loader-0.18.4.jar")) {
		t.Fatalf("путь к библиотеке: %s", library)
	}
	if got := paths.VersionJar("1.21.11"); !strings.HasSuffix(got,
		filepath.Join("versions", "1.21.11", "1.21.11.jar")) {
		t.Fatalf("клиентский jar: %s", got)
	}
	if got := Safe("1.21.11-pre/rc"); got != "1.21.11-pre_rc" {
		t.Fatalf("очистка имени: %s", got)
	}
}

func TestConfigSaveAndLoad(t *testing.T) {
	directory := t.TempDir()
	file := filepath.Join(directory, "aurora-launcher.json")
	config := NewConfig()
	config.MaxMemoryMb = 6144
	config.MinMemoryMb = 2048
	config.WindowWidth = 1600
	config.WindowHeight = 900
	config.InstallMods = false
	config.GameDirectory = filepath.Join(directory, "game")
	if err := config.Save(file); err != nil {
		t.Fatalf("сохранение: %v", err)
	}
	loaded := LoadConfig(file)
	if loaded.MaxMemoryMb != 6144 || loaded.MinMemoryMb != 2048 {
		t.Fatalf("память не сохранилась: %d/%d", loaded.MinMemoryMb, loaded.MaxMemoryMb)
	}
	if loaded.WindowWidth != 1600 || loaded.WindowHeight != 900 {
		t.Fatalf("размер окна: %dx%d", loaded.WindowWidth, loaded.WindowHeight)
	}
	if loaded.InstallMods {
		t.Fatal("флаг установки модов должен был сохраниться выключенным")
	}
	if loaded.GameDirectory != config.GameDirectory {
		t.Fatalf("каталог игры: %s", loaded.GameDirectory)
	}

	// Испорченный файл не должен ломать лаунчер.
	if err := os.WriteFile(file, []byte("{это не json"), 0o644); err != nil {
		t.Fatal(err)
	}
	broken := LoadConfig(file)
	if broken.MaxMemoryMb != defaultMaxMemoryMb {
		t.Fatalf("после испорченного файла ждём значения по умолчанию: %d", broken.MaxMemoryMb)
	}

	// Выход за границы отсекается.
	clamped := LoadConfig(filepath.Join(directory, "нет-такого.json"))
	clamped.MinMemoryMb = 10
	clamped.MaxMemoryMb = 10_000_000
	clamped.MinMemoryMb = clampMemory(clamped.MinMemoryMb)
	clamped.MaxMemoryMb = clampMemory(clamped.MaxMemoryMb)
	if clamped.MinMemoryMb != minMemoryFloorMb || clamped.MaxMemoryMb != maxMemoryCeilingMb {
		t.Fatalf("ограничение памяти: %d/%d", clamped.MinMemoryMb, clamped.MaxMemoryMb)
	}
}

func TestMavenPath(t *testing.T) {
	path, err := MavenPath("com.mojang:authlib:6.0.54")
	if err != nil {
		t.Fatal(err)
	}
	if path != "com/mojang/authlib/6.0.54/authlib-6.0.54.jar" {
		t.Fatalf("путь Maven: %s", path)
	}
	if _, err := MavenPath("без-двоеточий"); err == nil {
		t.Fatal("неркорректное имя должно давать ошибку")
	}
}

func TestSubstitute(t *testing.T) {
	tokens := map[string]string{
		"game_directory":   "C:\\games",
		"auth_player_name": "Steve",
		"empty":            "",
	}
	got := Substitute("--gameDir ${game_directory} --user ${auth_player_name}${empty}", tokens)
	if got != "--gameDir C:\\games --user Steve" {
		t.Fatalf("подстановка: %s", got)
	}
}

func TestPlan(t *testing.T) {
	paths := NewPaths(filepath.Join(t.TempDir(), "game"))
	installation := &Installation{
		VersionID:      "1.21.11",
		FabricID:       "fabric-loader-0.18.4-1.21.11",
		MainClass:      "net.fabricmc.loader.impl.launch.knot.KnotClient",
		Classpath:      []string{"/lib/a.jar", "/lib/b.jar"},
		JVMArguments:   []any{"-DFabricMcEmu=net.minecraft.client.main.Main"},
		GameArguments:  []any{"--gameDir", "${game_directory}", "--username", "${auth_player_name}"},
		AssetIndexID:   "27",
		NativesDir:     paths.NativesDir("1.21.11"),
		JavaExecutable: filepath.Join("runtime", "bin", "javaw.exe"),
		JavaSource:     string(JavaSourceBundled),
	}
	config := NewConfig()
	config.MaxMemoryMb = 4096
	config.MinMemoryMb = 1024
	session := &Session{PlayerName: "Steve", UUID: "u-u-i-d", AccessToken: "token",
		ExpiresAtMs: 4_102_444_800_000}

	var launcher Launcher
	plan, err := launcher.Plan(installation, paths, config, session)
	if err != nil {
		t.Fatalf("план запуска: %v", err)
	}
	if plan.Command[0] != installation.JavaExecutable {
		t.Fatalf("первым должен быть путь к Java: %s", plan.Command[0])
	}
	joined := strings.Join(plan.Command, " ")
	for _, expected := range []string{
		"-Xms1024M", "-Xmx4096M", "-Dfile.encoding=UTF-8",
		installation.MainClass, "--gameDir " + paths.Base, "--username Steve",
	} {
		if !strings.Contains(joined, expected) {
			t.Fatalf("в команде нет %q: %s", expected, joined)
		}
	}
	if strings.Contains(joined, "${") {
		t.Fatalf("остались неподставленные токены: %s", joined)
	}

	// Без аккаунта запуск невозможен.
	if _, err := launcher.Plan(installation, paths, config, nil); err == nil {
		t.Fatal("без сессии план должен выдавать ошибку")
	}
}

func TestAllows(t *testing.T) {
	current := OSName()
	other := "windows"
	if current == "windows" {
		other = "linux"
	}
	library := map[string]any{
		"rules": []any{
			map[string]any{"action": "allow", "os": map[string]any{"name": other}},
			map[string]any{"action": "disallow"},
		},
	}
	if Allows(library, Features(false)) {
		t.Fatal("правило для другой ОС не должно пропускать библиотеку")
	}
	library = map[string]any{
		"rules": []any{map[string]any{"action": "allow", "os": map[string]any{"name": current}}},
	}
	if !Allows(library, Features(false)) {
		t.Fatal("правило для текущей ОС должно пропускать библиотеку")
	}
	// Признак, которого игра не ждёт, отбрасывает аргумент.
	argument := map[string]any{
		"rules": []any{map[string]any{"action": "allow",
			"features": map[string]any{"has_custom_resolution": true}}},
		"value": "--width ${resolution_width}",
	}
	if Allows(argument, Features(false)) {
		t.Fatal("признак has_custom_resolution=false не должен пропускать аргумент")
	}
	if !Allows(argument, Features(true)) {
		t.Fatal("при has_custom_resolution=true аргумент разрешён")
	}
	if !Allows(map[string]any{}, Features(false)) {
		t.Fatal("без правил библиотека разрешена")
	}
}

func TestParseJavaVersion(t *testing.T) {
	info := ParseJavaVersion("/usr/bin/java", `openjdk version "21.0.12.1" 2026-08-18 LTS`)
	if !info.Valid() || info.MajorVersion != 21 || info.VersionText != "21.0" {
		t.Fatalf("разбор версии: %+v", info)
	}
	if ParseJavaVersion("/usr/bin/java", "мусор вместо версии").Valid() {
		t.Fatal("на мусоре версия не должна разбираться")
	}
	if ParseJavaVersion("/usr/bin/java", `openjdk version "17.0.9"`).MeetsRequirement() {
		t.Fatal("Java 17 не подходит для 1.21.11")
	}
}

func TestDetectFrom(t *testing.T) {
	candidates := []string{"/opt/java/17/bin/java", "/opt/java/21/bin/java", "/opt/java/25/bin/java"}
	inspect := func(candidate string) JavaInfo {
		switch {
		case strings.Contains(candidate, "/17/"):
			return JavaInfo{Executable: candidate, MajorVersion: 17, VersionText: "17.0"}
		case strings.Contains(candidate, "/21/"):
			return JavaInfo{Executable: candidate, MajorVersion: 21, VersionText: "21.0"}
		case strings.Contains(candidate, "/25/"):
			return JavaInfo{Executable: candidate, MajorVersion: 25, VersionText: "25.0"}
		}
		return JavaInfo{Error: "файл не найден"}
	}
	// Подходит и 21, и 25 — берём минимально достаточную, без лишнего риска.
	info := DetectFrom(candidates, inspect)
	if !info.MeetsRequirement() || info.MajorVersion != 21 {
		t.Fatalf("поиск Java: %+v", info)
	}
	// Только старая Java: сообщение должно объяснять, что именно не так.
	old := DetectFrom([]string{"/opt/java/17/bin/java"}, inspect)
	if old.MeetsRequirement() || !strings.Contains(old.Error, "17.0") {
		t.Fatalf("старая Java: %+v", old)
	}
	// Нет ничего.
	missing := DetectFrom([]string{"/opt/java/нет/bin/java"}, inspect)
	if missing.Valid() || missing.Error == "" {
		t.Fatalf("без Java результат должен быть недействительным: %+v", missing)
	}
}

func TestPlatformKey(t *testing.T) {
	key, err := PlatformKey("windows", "x86_64")
	if err != nil || key != "windows-x64" {
		t.Fatalf("ключ платформы: %s %v", key, err)
	}
	if _, err := PlatformKey("plan9", "mips"); err == nil {
		t.Fatal("для неизвестной платформы должна быть ошибка")
	}
}

func TestEmbeddedModManifest(t *testing.T) {
	manifest, err := EmbeddedModManifest()
	if err != nil {
		t.Fatalf("манифест модов: %v", err)
	}
	if manifest.MinecraftVersion != "1.21.11" {
		t.Fatalf("версия игры в манифесте: %s", manifest.MinecraftVersion)
	}
	if len(manifest.Mods) != 10 {
		t.Fatalf("ожидалось 10 модов оптимизации, в манифесте %d", len(manifest.Mods))
	}
	required := manifest.Required()
	if len(required) < 10 {
		t.Fatalf("обязательных модов %d, должно быть не меньше 10", len(required))
	}
	for _, mod := range required {
		if mod.File.URL == "" || mod.File.SHA1 == "" || mod.File.SHA512 == "" || mod.File.Size <= 0 {
			t.Fatalf("у мода %s не заполнен файл: %+v", mod.ID, mod.File)
		}
		if !strings.HasPrefix(mod.File.URL, "https://") {
			t.Fatalf("у мода %s не https-ссылка: %s", mod.ID, mod.File.URL)
		}
	}
	// Fabric API нужен не всем, а только части модов — проверяем, что он добавился.
	if manifest.FabricAPI != nil && manifest.apiNeeded() && required[0].ID != "fabric-api" {
		t.Fatal("Fabric API должен стоять первым, раз он кому-то нужен")
	}
}

func TestEmbeddedClientMod(t *testing.T) {
	if len(ClientModJar) < 1024 {
		t.Fatalf("клиентский мод не вложен в сборку (%d байт)", len(ClientModJar))
	}
	if string(ClientModJar[:2]) != "PK" {
		t.Fatal("вложенный файл клиента — не jar")
	}
}

func TestHashesAndFileMatches(t *testing.T) {
	directory := t.TempDir()
	file := filepath.Join(directory, "sample.jar")
	if err := os.WriteFile(file, []byte("AetherGlass"), 0o644); err != nil {
		t.Fatal(err)
	}
	sum, err := SHA1File(file)
	if err != nil {
		t.Fatal(err)
	}
	if !FileMatches(file, 11, sum) {
		t.Fatal("файл должен совпадать со своим SHA-1 и размером")
	}
	if FileMatches(file, 12, sum) {
		t.Fatal("несовпавший размер должен давать false")
	}
	if FileMatches(file, 11, strings.Repeat("0", 40)) {
		t.Fatal("несовпавший SHA-1 должен давать false")
	}
	big, err := SHA512File(file)
	if err != nil || len(big) != 128 {
		t.Fatalf("SHA-512: %s %v", big, err)
	}
}
