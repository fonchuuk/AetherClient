package core

import (
	"encoding/json"
	"os"
	"path/filepath"
)

// Config — настройки лаунчера. Ключи файла совпадают с прежней версией
// лаунчера, поэтому уже установленная игра подхватывается без переустановки.
type Config struct {
	GameDirectory   string `json:"gameDirectory"`
	JavaExecutable  string `json:"javaExecutable"`
	MinMemoryMb     int    `json:"minMemoryMb"`
	MaxMemoryMb     int    `json:"maxMemoryMb"`
	WindowWidth     int    `json:"windowWidth"`
	WindowHeight    int    `json:"windowHeight"`
	SelectedVersion string `json:"selectedVersion"`
	InstallMods     bool   `json:"installMods"`
	ClientID        string `json:"clientId"`
	// OfflineName — последнее имя офлайн-профиля. Хранится отдельно от
	// сессии, чтобы не заставлять игрока набирать его заново после входа
	// через Microsoft и обратно.
	OfflineName string `json:"offlineName"`

	path string
}

const (
	defaultMinMemoryMb  = 1024
	defaultMaxMemoryMb  = 4096
	minMemoryFloorMb    = 256
	maxMemoryCeilingMb  = 65536
	defaultClientID     = "00000000402b5328"
	DefaultMinecraftVer = "1.21.11"
	// DefaultLoaderVersion — последняя стабильная версия Fabric Loader для 1.21.11
	// (проверено по meta.fabricmc.net: 0.20.0 на этой версии помечена нестабильной).
	DefaultLoaderVersion = "0.19.5"
	// DefaultRuntimeComponent — компонент Java, который требует версия 1.21.11
	// (поле javaVersion.component в официальном JSON версии).
	DefaultRuntimeComponent = "java-runtime-delta"
)

// NewConfig — настройки со значениями по умолчанию.
func NewConfig() *Config {
	return &Config{
		GameDirectory:   DefaultGameDirectory(),
		MinMemoryMb:     defaultMinMemoryMb,
		MaxMemoryMb:     defaultMaxMemoryMb,
		SelectedVersion: DefaultMinecraftVer,
		InstallMods:     true,
		ClientID:        defaultClientID,
	}
}

// Load читает настройки; если файла нет или он испорчен — возвращает значения
// по умолчанию, лаунчер при этом остаётся рабочим.
func LoadConfig(file string) *Config {
	config := NewConfig()
	config.path = file
	data, err := os.ReadFile(file)
	if err != nil {
		return config
	}
	var raw map[string]json.RawMessage
	if json.Unmarshal(data, &raw) != nil {
		return config
	}
	config.GameDirectory = stringValue(raw, "gameDirectory", config.GameDirectory)
	config.JavaExecutable = stringValue(raw, "javaExecutable", config.JavaExecutable)
	config.SelectedVersion = stringValue(raw, "selectedVersion", config.SelectedVersion)
	config.ClientID = stringValue(raw, "clientId", config.ClientID)
	config.OfflineName = stringValue(raw, "offlineName", config.OfflineName)
	config.MinMemoryMb = clampMemory(intValue(raw, "minMemoryMb", defaultMinMemoryMb))
	config.MaxMemoryMb = clampMemory(intValue(raw, "maxMemoryMb", defaultMaxMemoryMb))
	config.WindowWidth = intValue(raw, "windowWidth", 0)
	if config.WindowWidth < 0 {
		config.WindowWidth = 0
	}
	config.WindowHeight = intValue(raw, "windowHeight", 0)
	if config.WindowHeight < 0 {
		config.WindowHeight = 0
	}
	if value, ok := raw["installMods"]; ok {
		_ = json.Unmarshal(value, &config.InstallMods)
	}
	if config.MaxMemoryMb < config.MinMemoryMb {
		config.MaxMemoryMb = config.MinMemoryMb
	}
	if config.GameDirectory == "" {
		config.GameDirectory = DefaultGameDirectory()
	}
	return config
}

// Save записывает настройки обратно.
func (c *Config) Save(file string) error {
	if file != "" {
		c.path = file
	}
	if c.path == "" {
		return &Errorf{Message: "не задан файл настроек"}
	}
	if err := os.MkdirAll(filepath.Dir(c.path), 0o755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(c.path, data, 0o644)
}

// Paths — раскладка каталогов игры по текущим настройкам.
func (c *Config) Paths() Paths {
	return NewPaths(c.GameDirectory)
}

func clampMemory(value int) int {
	if value < minMemoryFloorMb {
		return minMemoryFloorMb
	}
	if value > maxMemoryCeilingMb {
		return maxMemoryCeilingMb
	}
	return value
}

func stringValue(raw map[string]json.RawMessage, key string, fallback string) string {
	value, ok := raw[key]
	if !ok {
		return fallback
	}
	var text string
	if json.Unmarshal(value, &text) != nil || text == "" {
		return fallback
	}
	return text
}

func intValue(raw map[string]json.RawMessage, key string, fallback int) int {
	value, ok := raw[key]
	if !ok {
		return fallback
	}
	var number int
	if json.Unmarshal(value, &number) != nil {
		return fallback
	}
	return number
}
