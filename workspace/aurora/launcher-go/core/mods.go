package core

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// ModFile — файл мода из манифеста.
type ModFile struct {
	Name   string `json:"name"`
	URL    string `json:"url"`
	Size   int64  `json:"size"`
	SHA1   string `json:"sha1"`
	SHA512 string `json:"sha512"`
}

// Mod — запись манифеста модов. Name — то, как версия названа на Modrinth
// (часто это «Sodium 0.8.14 for Fabric»), Title — название проекта, его и
// показываем в списке.
type Mod struct {
	ID           string   `json:"id"`
	Title        string   `json:"title"`
	Name         string   `json:"name"`
	Version      string   `json:"version"`
	Purpose      string   `json:"purpose"`
	File         ModFile  `json:"file"`
	Dependencies []string `json:"dependencies"`
}

// ModManifest — встроенный список модов оптимизации.
type ModManifest struct {
	MinecraftVersion string   `json:"minecraft_version"`
	Loader           string   `json:"loader"`
	FabricAPI        *Mod     `json:"fabric_api"`
	APIRequiredBy    []string `json:"fabric_api_required_by"`
	Mods             []Mod    `json:"mods"`
}

// ParseModManifest разбирает манифест модов.
func ParseModManifest(data []byte) (*ModManifest, error) {
	var manifest ModManifest
	if err := json.Unmarshal(data, &manifest); err != nil {
		return nil, err
	}
	if manifest.MinecraftVersion == "" {
		return nil, &Errorf{Message: "в манифесте модов не указана версия игры"}
	}
	return &manifest, nil
}

// DisplayName — название мода для показа: название проекта, а если его нет —
// название версии из манифеста.
func (m Mod) DisplayName() string {
	if m.Title != "" {
		return m.Title
	}
	return m.Name
}

// Required — моды, которые нужно установить. Fabric API добавляется, только если
// он действительно нужен кому-то из списка: лишний файл в папке модов не нужен.
func (m *ModManifest) Required() []Mod {
	required := make([]Mod, 0, len(m.Mods)+1)
	for _, mod := range m.Mods {
		required = append(required, mod)
	}
	if m.FabricAPI != nil && m.apiNeeded() {
		required = append([]Mod{*m.FabricAPI}, required...)
	}
	return required
}

func (m *ModManifest) apiNeeded() bool {
	ids := map[string]bool{}
	for _, mod := range m.Mods {
		ids[mod.ID] = true
	}
	for _, id := range m.APIRequiredBy {
		if ids[id] {
			return true
		}
	}
	return false
}

// ModStatus — состояние одного мода для показа в списке.
type ModStatus struct {
	Name    string
	Version string
	Purpose string
	State   string
}

// ModInstaller ставит моды оптимизации и сам клиентский мод.
type ModInstaller struct {
	Paths    Paths
	Manifest *ModManifest
	Progress ProgressListener
	Cancel   *Cancel
}

// Status — список модов с их состоянием.
func (m *ModInstaller) Status() []ModStatus {
	var result []ModStatus
	for _, mod := range m.Manifest.Required() {
		state := "нужно скачать"
		target := filepath.Join(m.Paths.Mods(), mod.File.Name)
		if info, err := os.Stat(target); err == nil && info.Mode().IsRegular() {
			if sum, err := SHA512File(target); err == nil &&
				strings.EqualFold(sum, mod.File.SHA512) {
				state = "установлен"
			} else {
				state = "повреждён, скачается заново"
			}
		}
		result = append(result, ModStatus{Name: mod.DisplayName(), Version: mod.Version,
			Purpose: mod.Purpose, State: state})
	}
	name, state := m.clientModStatus()
	result = append(result, ModStatus{Name: "AetherGlass Client (мод клиента)",
		Version: "1.0.0", Purpose: "Главное меню, эффекты и экранные элементы", State: state})
	_ = name
	return result
}

func (m *ModInstaller) clientModStatus() (string, string) {
	target := filepath.Join(m.Paths.Mods(), ClientModName)
	if info, err := os.Stat(target); err == nil && info.Mode().IsRegular() {
		return target, "установлен"
	}
	return target, "нужно установить"
}

// ClientModName — имя файла клиентского мода в папке модов.
const ClientModName = "AetherGlass.jar"

// Install скачивает моды в каталог модов игры. Проверка целостности идёт по
// SHA-512 — прямо во время загрузки, без повторного чтения файла.
func (m *ModInstaller) Install() ([]string, error) {
	if err := os.MkdirAll(m.Paths.Mods(), 0o755); err != nil {
		return nil, err
	}
	installed := make([]string, 0, len(m.Manifest.Required()))
	var jobs []DownloadJob
	for _, mod := range m.Manifest.Required() {
		if m.Cancel.IsCancelled() {
			return nil, ErrCancelled
		}
		target := filepath.Join(m.Paths.Mods(), mod.File.Name)
		if info, err := os.Stat(target); err == nil && info.Mode().IsRegular() {
			if sum, err := SHA512File(target); err == nil &&
				strings.EqualFold(sum, mod.File.SHA512) {
				installed = append(installed, target)
				continue
			}
		}
		jobs = append(jobs, DownloadJob{URL: mod.File.URL, Target: target,
			Size: mod.File.Size, SHA1: mod.File.SHA1, SHA512: mod.File.SHA512,
			Name: mod.Name})
	}
	if len(jobs) > 0 {
		if m.Progress != nil {
			m.Progress.Stage("Моды: " + strconv.Itoa(len(jobs)) + " к загрузке")
		}
		if err := DownloadAll(jobs, downloadWorkers, &countingProgress{
			listener: m.Progress, total: int64(len(jobs))}, m.Cancel); err != nil {
			return nil, err
		}
		for _, job := range jobs {
			installed = append(installed, job.Target)
		}
	}
	return installed, nil
}

// InstallClientMod кладёт клиентский мод в каталог модов: без этого игра
// запустилась бы чистой версией, без самого клиента.
func (m *ModInstaller) InstallClientMod() (string, error) {
	if err := os.MkdirAll(m.Paths.Mods(), 0o755); err != nil {
		return "", err
	}
	target := filepath.Join(m.Paths.Mods(), ClientModName)
	if len(ClientModJar) == 0 {
		return "", &Errorf{Message: "в сборку лаунчера не вложен мод клиента"}
	}
	return target, os.WriteFile(target, ClientModJar, 0o644)
}
