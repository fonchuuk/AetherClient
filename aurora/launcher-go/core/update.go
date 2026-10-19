package core

// Проверка обновлений и замена своего exe на Windows.
//
// Устройство, которое здесь решается: exe, который надо заменить, в этот
// момент запущен, а Windows не даёт перезаписать исполняемый файл работающего
// процесса. Поэтому замена происходит в два приёма: лаунчер скачивает новый
// файл рядом с собой, записывает рядом же командный файл, который подождёт
// завершения лаунчера, подменит exe и заново его запустит, — и выходит.

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
)

// Откуда брать обновления. Репозиторий приватный, поэтому при запросе
// передаётся токен — но только с правом чтения релизов.
const (
	UpdateOwner = "fonchuuk"
	UpdateRepo  = "AetherClient"
)

// UpdateToken — токен только на чтение релизов. Он попадает в exe, поэтому
// обязан иметь минимум прав: contents=read и ничего больше.
var UpdateToken = ""

// UpdateInfo — то, что лаунчер показывает в интерфейсе.
type UpdateInfo struct {
	Version   string `json:"version"`   // метка релиза, например v0.1.2
	Name      string `json:"name"`      // заголовок релиза
	Notes     string `json:"notes"`     // описание изменений
	AssetName string `json:"assetName"` // имя файла-вложения
	AssetSize int64  `json:"assetSize"` // размер в байтах
	URL       string `json:"url"`       // прямая ссылка на скачивание
}

type releasePayload struct {
	TagName string `json:"tag_name"`
	Name    string `json:"name"`
	Body    string `json:"body"`
	Assets  []struct {
		Name string `json:"name"`
		Size int64  `json:"size"`
		URL  string `json:"browser_download_url"`
	} `json:"assets"`
}

// CheckUpdate запрашивает последний релиз и сравнивает его с текущей версией.
//
// Возвращает nil, если обновления нет, — это нормальный исход, а не ошибка.
func CheckUpdate(current string) (*UpdateInfo, error) {
	if runtime.GOOS != "windows" {
		return nil, nil // замена exe имеет смысл только там, где он и запускался
	}
	// Берём список, а не /releases/latest: тот эндпоинт не отдаёт пререлизы,
	// а тестовые сборки помечаются именно ими — лаунчер остался бы без обновлений.
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases?per_page=1",
		UpdateOwner, UpdateRepo)

	request, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("Accept", "application/vnd.github+json")
	request.Header.Set("User-Agent", "AetherGlass-Launcher")
	if UpdateToken != "" {
		request.Header.Set("Authorization", "Bearer "+UpdateToken)
	}

	client := &http.Client{Timeout: 20 * time.Second}
	response, err := client.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GitHub ответил %d", response.StatusCode)
	}

	var payloads []releasePayload
	if err := json.NewDecoder(response.Body).Decode(&payloads); err != nil {
		return nil, err
	}
	if len(payloads) == 0 {
		return nil, nil // релизов пока нет — обновляться не на что
	}
	payload := payloads[0]
	if !newerThan(payload.TagName, current) {
		return nil, nil
	}

	info := &UpdateInfo{
		Version: payload.TagName,
		Name:    payload.Name,
		Notes:   payload.Body,
	}
	for _, asset := range payload.Assets {
		if strings.HasSuffix(strings.ToLower(asset.Name), ".exe") {
			info.AssetName = asset.Name
			info.AssetSize = asset.Size
			info.URL = asset.URL
			break
		}
	}
	if info.URL == "" {
		return nil, nil // релиз без exe — обновлять нечего
	}
	return info, nil
}

// newerThan сравнивает метки вида v0.1.2. Номера идут по частям, поэтому
// v0.10.0 корректно считается новее v0.9.0, чего не дало бы сравнение строк.
func newerThan(candidate string, current string) bool {
	parse := func(value string) []int {
		value = strings.TrimPrefix(strings.TrimSpace(value), "v")
		parts := strings.Split(value, ".")
		result := make([]int, 0, len(parts))
		for _, part := range parts {
			number, err := strconv.Atoi(part)
			if err != nil {
				result = append(result, 0)
				continue
			}
			result = append(result, number)
		}
		return result
	}
	a := parse(candidate)
	b := parse(current)
	for index := 0; index < 3; index++ {
		var left, right int
		if index < len(a) {
			left = a[index]
		}
		if index < len(b) {
			right = b[index]
		}
		if left != right {
			return left > right
		}
	}
	return false
}

// DownloadUpdate скачивает новый exe рядом с запущенным и возвращает его путь.
func DownloadUpdate(info *UpdateInfo, progress func(done int64, total int64)) (string, error) {
	if info == nil || info.URL == "" {
		return "", fmt.Errorf("обновление не описано")
	}
	own, err := os.Executable()
	if err != nil {
		return "", err
	}
	target := filepath.Join(filepath.Dir(own), "AetherGlass-Launcher.update.exe")

	request, err := http.NewRequest(http.MethodGet, info.URL, nil)
	if err != nil {
		return "", err
	}
	request.Header.Set("Accept", "application/octet-stream")
	request.Header.Set("User-Agent", "AetherGlass-Launcher")
	if UpdateToken != "" {
		request.Header.Set("Authorization", "Bearer "+UpdateToken)
	}
	response, err := (&http.Client{Timeout: 0}).Do(request)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return "", fmt.Errorf("файл обновления недоступен: %d", response.StatusCode)
	}

	file, err := os.Create(target)
	if err != nil {
		return "", err
	}
	defer file.Close()

	written := int64(0)
	buffer := make([]byte, 256*1024)
	for {
		count, readErr := response.Body.Read(buffer)
		if count > 0 {
			if _, writeErr := file.Write(buffer[:count]); writeErr != nil {
				return "", writeErr
			}
			written += int64(count)
			if progress != nil {
				progress(written, info.AssetSize)
			}
		}
		if readErr != nil {
			if readErr == io.EOF {
				break
			}
			return "", readErr
		}
	}
	if err := file.Close(); err != nil {
		return "", err
	}
	return target, nil
}

// ApplyUpdate подменяет запущенный exe скачанным и перезапускает лаунчер.
//
// Сам лаунчер сделать этого не может: пока он работает, его файл занят.
// Поэтому рядом пишется командный файл, который и выполняет всю работу уже
// после выхода лаунчера.
func ApplyUpdate(newExe string) error {
	own, err := os.Executable()
	if err != nil {
		return err
	}
	own, _ = filepath.EvalSymlinks(own)
	directory := filepath.Dir(own)

	script := filepath.Join(directory, "aetherglass-update.cmd")
	body := "@echo off\r\n" +
		"rem Подмена exe и перезапуск. Пишется лаунчером перед выходом.\r\n" +
		"ping -n 3 127.0.0.1 >nul\r\n" +
		":wait\r\n" +
		"tasklist /FI \"IMAGENAME eq " + filepath.Base(own) + "\" 2>nul | find /I \"" + filepath.Base(own) + "\" >nul\r\n" +
		"if not errorlevel 1 goto wait\r\n" +
		"move /Y \"" + newExe + "\" \"" + own + "\"\r\n" +
		"start \"\" \"" + own + "\"\r\n" +
		"del /F /Q \"%~f0\"\r\n"
	if err := os.WriteFile(script, []byte(body), 0o755); err != nil {
		return err
	}

	command := exec.Command("cmd", "/C", "start", "\"\"", "/MIN", script)
	command.Dir = directory
	return command.Start()
}
