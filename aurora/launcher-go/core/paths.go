// Package core — вся рабочая логика лаунчера без единой строчки интерфейса:
// каталоги, настройки, загрузки, установка игры, вход в аккаунт и запуск.
// Пакет одинаково собирается и под Windows, и под Linux, поэтому его логика
// проверяется обычными тестами.
package core

import (
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// Paths — раскладка каталогов. Совпадает с принятой в официальном лаунчере
// Mojang: libraries, assets, versions — поэтому файлы совместимы с другими
// инструментами и не скачиваются повторно.
type Paths struct {
	Base string
}

// NewPaths создаёт раскладку для указанного каталога игры.
func NewPaths(base string) Paths {
	absolute, err := filepath.Abs(base)
	if err != nil {
		absolute = base
	}
	return Paths{Base: filepath.Clean(absolute)}
}

// DefaultGameDirectory — каталог игры по умолчанию: ~/.aetherglass.
func DefaultGameDirectory() string {
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		return ".aetherglass"
	}
	return filepath.Join(home, ".aetherglass")
}

func (p Paths) Libraries() string    { return filepath.Join(p.Base, "libraries") }
func (p Paths) Assets() string       { return filepath.Join(p.Base, "assets") }
func (p Paths) AssetObjects() string { return filepath.Join(p.Assets(), "objects") }
func (p Paths) AssetIndexes() string { return filepath.Join(p.Assets(), "indexes") }

// LogConfigs — файлы конфигурации логирования, которые требует игра.
func (p Paths) LogConfigs() string { return filepath.Join(p.Assets(), "log_configs") }

func (p Paths) Versions() string { return filepath.Join(p.Base, "versions") }
func (p Paths) Mods() string     { return filepath.Join(p.Base, "mods") }
func (p Paths) Logs() string     { return filepath.Join(p.Base, "logs") }

// Runtimes — каталог скачанных Java-рантаймов Mojang.
func (p Paths) Runtimes() string { return filepath.Join(p.Base, "runtime") }

// VersionDir — каталог конкретной версии.
func (p Paths) VersionDir(versionID string) string {
	return filepath.Join(p.Versions(), Safe(versionID))
}

// VersionJSON — сохранённый JSON версии.
func (p Paths) VersionJSON(versionID string) string {
	name := Safe(versionID)
	return filepath.Join(p.VersionDir(versionID), name+".json")
}

// VersionJar — клиентский jar версии.
func (p Paths) VersionJar(versionID string) string {
	name := Safe(versionID)
	return filepath.Join(p.VersionDir(versionID), name+".jar")
}

// NativesDir — каталог распакованных нативных библиотек версии.
func (p Paths) NativesDir(versionID string) string {
	return filepath.Join(p.VersionDir(versionID), "natives-"+Safe(versionID))
}

// ConfigFile — настройки лаунчера.
func (p Paths) ConfigFile() string { return filepath.Join(p.Base, "aurora-launcher.json") }

// AuthFile — сохранённая сессия входа.
func (p Paths) AuthFile() string { return filepath.Join(p.Base, "aurora-auth.json") }

// AssetObject — путь к объекту ресурсов: первые два символа хэша — имя каталога.
func (p Paths) AssetObject(hash string) string {
	if len(hash) < 2 {
		return filepath.Join(p.AssetObjects(), hash)
	}
	return filepath.Join(p.AssetObjects(), hash[:2], hash)
}

// Library — путь к файлу библиотеки Maven по имени вида group:artifact:version.
func (p Paths) Library(name string, classifier string) (string, error) {
	parts := strings.SplitN(name, ":", 3)
	if len(parts) != 3 {
		return "", &Errorf{Message: "некорректное имя библиотеки: " + name}
	}
	group := strings.ReplaceAll(parts[0], ".", "/")
	artifact := parts[1]
	version := parts[2]
	file := artifact + "-" + version
	if classifier != "" {
		file += "-" + classifier
	}
	return filepath.Join(p.Libraries(), group, artifact, version, file+".jar"), nil
}

// LibraryByPath — путь к файлу библиотеки, заданному явным путём (так делает Mojang).
func (p Paths) LibraryByPath(path string) string {
	return filepath.Join(p.Libraries(), filepath.FromSlash(strings.ReplaceAll(path, "\\", "/")))
}

var unsafeName = regexp.MustCompile(`[^A-Za-z0-9._-]`)

// Safe — имя каталога без запрещённых символов.
func Safe(value string) string {
	return unsafeName.ReplaceAllString(value, "_")
}

// CreateDirectories создаёт все каталоги, нужные для работы.
func (p Paths) CreateDirectories() error {
	for _, directory := range []string{
		p.Base, p.Libraries(), p.AssetObjects(), p.AssetIndexes(),
		p.LogConfigs(), p.Versions(), p.Mods(), p.Logs(), p.Runtimes(),
	} {
		if err := os.MkdirAll(directory, 0o755); err != nil {
			return err
		}
	}
	return nil
}

// Errorf — простая ошибка с текстом: используется там, гдеfmt.Errorf не нужен.
type Errorf struct {
	Message string
}

func (e *Errorf) Error() string { return e.Message }
