package core

import (
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"strings"
)

// RequiredJavaMajor — минимальная версия Java для Minecraft 1.21.11.
const RequiredJavaMajor = 21

// JavaInfo — сведения о найденной Java.
type JavaInfo struct {
	Executable   string
	MajorVersion int
	VersionText  string
	Error        string
}

// Valid — Java найдена и отвечает. Пустая структура считается недействительной:
// иначе лаунчер «находил» бы Java по пустому пути.
func (info JavaInfo) Valid() bool {
	return info.Error == "" && info.Executable != ""
}

// MeetsRequirement — версия не ниже требуемой.
func (info JavaInfo) MeetsRequirement() bool {
	return info.Valid() && info.MajorVersion >= RequiredJavaMajor
}

var javaVersionPattern = regexp.MustCompile(`version "([0-9]+)(?:\.([0-9]+))?`)

// Inspect проверяет указанный исполняемый файл Java запуском «java -version».
func Inspect(executable string) JavaInfo {
	if strings.TrimSpace(executable) == "" {
		return JavaInfo{Error: "путь к Java не задан"}
	}
	absolute, err := filepath.Abs(executable)
	if err != nil {
		absolute = executable
	}
	info, err := os.Stat(absolute)
	if err != nil || info.IsDir() {
		return JavaInfo{Error: "файл не найден: " + absolute}
	}
	command := exec.Command(absolute, "-version")
	command.SysProcAttr = hiddenWindowAttr()
	output, err := command.CombinedOutput()
	if err != nil && len(output) == 0 {
		return JavaInfo{Executable: absolute, Error: "не удалось запустить Java: " + err.Error()}
	}
	parsed := ParseJavaVersion(absolute, string(output))
	if parsed.Valid() {
		return parsed
	}
	// javaw.exe — та же Java, только без консольного окна; иногда он не пишет
	// версию в канал. Тогда спрашиваем версию у соседнего java.exe, а в ответе
	// оставляем исходный путь: игра запускается именно javaw.exe, чтобы рядом
	// с ней не появлялось пустое чёрное окно.
	if sibling := consoleSibling(absolute); sibling != "" {
		if fallback := Inspect(sibling); fallback.Valid() {
			return JavaInfo{Executable: absolute, MajorVersion: fallback.MajorVersion,
				VersionText: fallback.VersionText}
		}
	}
	return parsed
}

// consoleSibling — консольный java.exe рядом с javaw.exe из того же каталога.
func consoleSibling(executable string) string {
	if !strings.EqualFold(filepath.Base(executable), "javaw.exe") {
		return ""
	}
	sibling := filepath.Join(filepath.Dir(executable), "java.exe")
	if info, err := os.Stat(sibling); err != nil || info.IsDir() {
		return ""
	}
	return sibling
}

// ParseJavaVersion разбирает вывод «java -version» и достаёт мажорную версию.
func ParseJavaVersion(executable string, output string) JavaInfo {
	match := javaVersionPattern.FindStringSubmatch(output)
	if match == nil {
		return JavaInfo{Error: "не удалось разобрать версию Java"}
	}
	major, err := strconv.Atoi(match[1])
	if err != nil {
		return JavaInfo{Error: "не удалось разобрать версию Java"}
	}
	text := match[1]
	if match[2] != "" {
		text += "." + match[2]
	}
	return JavaInfo{Executable: executable, MajorVersion: major, VersionText: text}
}

// Detect ищет системную Java: JAVA_HOME, PATH и каталоги известных сборок.
func Detect() JavaInfo {
	return DetectFrom(JavaCandidates(), Inspect)
}

// DetectWith — то же, но с подменяемой проверкой (нужна для тестов).
func DetectWith(inspect func(string) JavaInfo) JavaInfo {
	return DetectFrom(JavaCandidates(), inspect)
}

// DetectFrom перебирает кандидатов и берёт самую свежую подходящую Java.
// Если подходящей нет, но какая-то Java нашлась, об этом сообщается отдельно:
// пользователю важно понимать, что Java есть, но старая.
func DetectFrom(candidates []string, inspect func(string) JavaInfo) JavaInfo {
	var best JavaInfo
	var found JavaInfo
	suitable := false
	seen := map[string]bool{}
	for _, candidate := range candidates {
		if candidate == "" || seen[candidate] {
			continue
		}
		seen[candidate] = true
		info := inspect(candidate)
		if !info.Valid() {
			continue
		}
		if info.MajorVersion > found.MajorVersion {
			found = info
		}
		if info.MeetsRequirement() && info.MajorVersion > best.MajorVersion {
			best = info
			suitable = true
			if best.MajorVersion == RequiredJavaMajor {
				return best
			}
		}
	}
	if suitable {
		return best
	}
	if found.Valid() {
		return JavaInfo{Executable: found.Executable, MajorVersion: found.MajorVersion,
			VersionText: found.VersionText,
			Error: "найдена Java " + found.VersionText + ", но игре нужна " +
				strconv.Itoa(RequiredJavaMajor) + " или новее"}
	}
	return JavaInfo{Error: "Java " + strconv.Itoa(RequiredJavaMajor) + " не найдена на этом компьютере"}
}

// JavaCandidates — места, где обычно лежит Java. Пустые пути и повторы
// выброшены: один и тот же java иначе проверялся бы дважды, а каждая
// проверка — это отдельный запуск «java -version».
func JavaCandidates() []string {
	var candidates []string
	if runtime.GOOS != "windows" {
		candidates = unixCandidates()
	} else {
		candidates = windowsCandidates()
	}
	return uniquePaths(candidates)
}

// uniquePaths — убирает пустые пути и повторы, сохраняя порядок.
func uniquePaths(paths []string) []string {
	seen := make(map[string]bool, len(paths))
	result := make([]string, 0, len(paths))
	for _, path := range paths {
		if strings.TrimSpace(path) == "" || seen[path] {
			continue
		}
		seen[path] = true
		result = append(result, path)
	}
	return result
}

// Имена исполняемых файлов ищем оба: в установках без javaw.exe (так бывает
// у «чистых» JRE и у портативных сборок) искать только javaw — значит не найти
// ничего. Это и была одна из причин «лаунчер не видит Java».
var javaExecutableNames = []string{"javaw.exe", "java.exe"}

func windowsCandidates() []string {
	var candidates []string
	for _, variable := range []string{"JAVA_HOME", "JDK_HOME", "JAVA_PATH"} {
		home := os.Getenv(variable)
		if home == "" {
			continue
		}
		for _, name := range javaExecutableNames {
			candidates = append(candidates, filepath.Join(home, "bin", name))
		}
	}
	for _, entry := range filepath.SplitList(os.Getenv("PATH")) {
		if entry == "" {
			continue
		}
		for _, name := range javaExecutableNames {
			candidates = append(candidates, filepath.Join(entry, name))
		}
	}
	// Каталоги известных поставщиков Java перебирает платформенный код:
	// в Windows это Program Files, в Linux и macOS — свои пути.
	for _, name := range javaExecutableNames {
		candidates = append(candidates, vendorCandidates(name)...)
	}
	return candidates
}

func unixCandidates() []string {
	var candidates []string
	if home := os.Getenv("JAVA_HOME"); home != "" {
		candidates = append(candidates, filepath.Join(home, "bin", "java"))
	}
	for _, entry := range filepath.SplitList(os.Getenv("PATH")) {
		if entry != "" {
			candidates = append(candidates, filepath.Join(entry, "java"))
		}
	}
	candidates = append(candidates, vendorCandidates("java")...)
	return candidates
}

// ─────────────────────────── официальный рантайм Mojang ─────────────────────

// RuntimeIndexURL — список Java-рантаймов Mojang. Структура проверена по
// живому ответу: сначала платформа, потом компонент, и только внутри — сборки.
const RuntimeIndexURL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"

// RuntimeEntry — запись рантайма в индексе Mojang.
type RuntimeEntry struct {
	Version struct {
		Name     string `json:"name"`
		Released string `json:"released"`
	} `json:"version"`
	Manifest struct {
		SHA1 string `json:"sha1"`
		Size int64  `json:"size"`
		URL  string `json:"url"`
	} `json:"manifest"`
}

// RuntimeIndex — разобранный индекс: платформа → компонент → сборки.
type RuntimeIndex map[string]map[string][]RuntimeEntry

// FetchRuntimeIndex запрашивает список рантаймов Mojang.
func FetchRuntimeIndex() (RuntimeIndex, error) {
	document, err := GetJSON(RuntimeIndexURL)
	if err != nil {
		return nil, err
	}
	raw, err := json.Marshal(document)
	if err != nil {
		return nil, err
	}
	var index RuntimeIndex
	if err := json.Unmarshal(raw, &index); err != nil {
		return nil, &Errorf{Message: "неожиданный формат списка рантаймов Mojang"}
	}
	return index, nil
}

// Latest — самая свежая сборка компонента для платформы.
func (index RuntimeIndex) Latest(platform string, component string) (RuntimeEntry, error) {
	components, ok := index[platform]
	if !ok {
		return RuntimeEntry{}, &Errorf{Message: "в списке Mojang нет платформы " + platform}
	}
	entries, ok := components[component]
	if !ok || len(entries) == 0 {
		return RuntimeEntry{}, &Errorf{Message: "в списке Mojang нет компонента " + component +
			" для " + platform}
	}
	return entries[0], nil
}

// RuntimeFile — один файл рантайма из манифеста Mojang.
type RuntimeFile struct {
	Type       string `json:"type"`
	Executable bool   `json:"executable"`
	Downloads  map[string]struct {
		SHA1 string `json:"sha1"`
		Size int64  `json:"size"`
		URL  string `json:"url"`
	} `json:"downloads"`
}

// FetchRuntimeManifest загружает манифест выбранной сборки: именно в нём
// перечислены файлы с контрольными суммами.
func FetchRuntimeManifest(entry RuntimeEntry) (map[string]RuntimeFile, error) {
	if entry.Manifest.URL == "" {
		return nil, &Errorf{Message: "в записи рантайма нет ссылки на манифест"}
	}
	document, err := GetJSON(entry.Manifest.URL)
	if err != nil {
		return nil, err
	}
	raw, err := json.Marshal(document)
	if err != nil {
		return nil, err
	}
	return ParseRuntimeManifest(raw)
}

// ParseRuntimeManifest разбирает список файлов рантайма. Отдельная функция —
// чтобы формат можно было проверить тестом без сети.
func ParseRuntimeManifest(data []byte) (map[string]RuntimeFile, error) {
	var manifest struct {
		Files map[string]RuntimeFile `json:"files"`
	}
	if err := json.Unmarshal(data, &manifest); err != nil {
		return nil, &Errorf{Message: "неожиданный формат манифеста рантайма"}
	}
	return manifest.Files, nil
}

// JavaSource — откуда взялась Java для игры.
type JavaSource string

// Варианты источника Java.
const (
	JavaSourceBundled JavaSource = "своя Java (рантайм Mojang)"
	JavaSourceSystem  JavaSource = "системная Java"
	JavaSourceCustom  JavaSource = "указанная вручную"
)

// Provision — найденная Java и её происхождение.
type Provision struct {
	Executable string
	Source     JavaSource
	Version    string
}

// JavaProvisioner достаёт Java для игры. Порядок такой:
//
//  1. путь, указанный игроком в настройках;
//  2. уже скачанный официальный рантайм Mojang в каталоге игры;
//  3. скачивание этого рантайма (один раз, ~50 МБ);
//  4. системная Java — только если скачать не удалось вовсе.
//
// От игрока не требуется ничего:Java ставится сама, в каталог игры, и системой
// не владеет.
type JavaProvisioner struct {
	Paths    Paths
	Progress ProgressListener
	Cancel   *Cancel
}

// PlatformKey — ключ платформы в терминах Mojang.
func PlatformKey(os string, arch string) (string, error) {
	switch os + "-" + arch {
	case "windows-x86_64":
		return "windows-x64", nil
	case "windows-x86":
		return "windows-x86", nil
	case "windows-arm64":
		return "windows-arm64", nil
	case "linux-x86_64":
		return "linux", nil
	case "linux-x86":
		return "linux-i386", nil
	case "osx-x86_64":
		return "mac-os", nil
	case "osx-arm64":
		return "mac-os-arm64", nil
	default:
		return "", &Errorf{Message: "у Mojang нет готового рантайма Java для " + os + " (" + arch + ")"}
	}
}

// RuntimeDir — каталог рантайма: <игра>/runtime/<компонент>/<платформа>.
func (p JavaProvisioner) RuntimeDir(component string, platform string) string {
	return filepath.Join(p.Paths.Runtimes(), component, platform)
}

// Ensure возвращает Java для запуска игры.
func (p JavaProvisioner) Ensure(component string, requiredMajor int) (Provision, error) {
	return p.EnsureWith("", component, requiredMajor)
}

// EnsureWith — то же, но с путём из настроек (он имеет приоритет).
func (p JavaProvisioner) EnsureWith(explicit string, component string, requiredMajor int) (Provision, error) {
	if strings.TrimSpace(explicit) != "" {
		info := Inspect(explicit)
		if !info.Valid() {
			return Provision{}, &Errorf{Message: "Java из настроек не работает: " + info.Error}
		}
		if info.MajorVersion < requiredMajor {
			return Provision{}, &Errorf{Message: "Java из настроек версии " + info.VersionText +
				", а игре нужна " + strconv.Itoa(requiredMajor) + " или новее"}
		}
		return Provision{Executable: info.Executable, Source: JavaSourceCustom, Version: info.VersionText}, nil
	}

	platform, err := PlatformKey(OSName(), OSArch())
	if err == nil {
		directory := p.RuntimeDir(component, platform)
		if cached := ExecutableIn(directory); cached != "" {
			if info := Inspect(cached); info.Valid() && info.MajorVersion >= requiredMajor {
				return Provision{Executable: cached, Source: JavaSourceBundled, Version: info.VersionText}, nil
			}
		}
	}
	// Если в системе уже установлена подходящая Java 21+, используем её автоматически без лишнего скачивания.
	if sys := Detect(); sys.MeetsRequirement() && sys.MajorVersion >= requiredMajor {
		return Provision{Executable: sys.Executable, Source: JavaSourceSystem, Version: sys.VersionText}, nil
	}
	if err != nil {
		return p.systemFallback(requiredMajor, err)
	}
	if provision, err := p.downloadRuntime(component, platform, requiredMajor); err == nil {
		return provision, nil
	} else if p.Cancel.IsCancelled() {
		return Provision{}, err
	}
	return p.systemFallback(requiredMajor, nil)
}

func (p JavaProvisioner) systemFallback(requiredMajor int, reason error) (Provision, error) {
	info := Detect()
	if info.MeetsRequirement() && info.MajorVersion >= requiredMajor {
		return Provision{Executable: info.Executable, Source: JavaSourceSystem, Version: info.VersionText}, nil
	}
	if reason != nil {
		return Provision{}, &Errorf{Message: "не удалось получить Java " +
			strconv.Itoa(requiredMajor) + ": " + reason.Error() + "; " + info.Error}
	}
	return Provision{}, &Errorf{Message: "не удалось скачать официальную Java и не нашлась " +
		"системная: проверьте подключение к интернету"}
}

// DownloadRuntime принудительно скачивает официальный рантайм (кнопка в настройках).
func (p JavaProvisioner) DownloadRuntime(component string, requiredMajor int) (Provision, error) {
	platform, err := PlatformKey(OSName(), OSArch())
	if err != nil {
		return Provision{}, err
	}
	return p.downloadRuntime(component, platform, requiredMajor)
}

func (p JavaProvisioner) downloadRuntime(component string, platform string, requiredMajor int) (Provision, error) {
	p.stage("Список рантаймов Java (Mojang)")
	index, err := FetchRuntimeIndex()
	if err != nil {
		return Provision{}, err
	}
	entry, err := index.Latest(platform, component)
	if err != nil {
		return Provision{}, err
	}
	p.stage("Манифест Java " + entry.Version.Name)
	files, err := FetchRuntimeManifest(entry)
	if err != nil {
		return Provision{}, err
	}
	directory := p.RuntimeDir(component, platform)
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return Provision{}, err
	}

	// Сначала создаём все каталоги и собираем список файлов: качать будем
	// параллельно и в произвольном порядке, а каталог должен существовать
	// до записи в него первого файла.
	var jobs []DownloadJob
	var executables []string
	for path, file := range files {
		if p.Cancel.IsCancelled() {
			return Provision{}, ErrCancelled
		}
		target := filepath.Join(directory, filepath.FromSlash(path))
		if file.Type == "directory" {
			_ = os.MkdirAll(target, 0o755)
			continue
		}
		source, ok := file.Downloads["raw"]
		if !ok || source.URL == "" {
			continue
		}
		jobs = append(jobs, DownloadJob{URL: source.URL, Target: target,
			Size: source.Size, SHA1: source.SHA1})
		if file.Executable {
			executables = append(executables, target)
		}
	}
	// Порядок обхода map в Go случайный, а загружать крупные файлы раньше
	// мелких выгоднее: полоса прогресса движется ровнее.
	sort.Slice(jobs, func(left int, right int) bool {
		return jobs[left].Size > jobs[right].Size
	})

	total := int64(len(jobs))
	progress := &countingProgress{listener: p.Progress, total: total}
	progress.Stage("Java " + entry.Version.Name + ": файлов " + strconv.FormatInt(total, 10))
	if err := DownloadAll(jobs, downloadWorkers, progress, p.Cancel); err != nil {
		return Provision{}, err
	}
	for _, target := range executables {
		_ = os.Chmod(target, 0o755)
	}
	executable := ExecutableIn(directory)
	info := Inspect(executable)
	if !info.Valid() {
		return Provision{}, &Errorf{Message: "скачанная Java не запускается: " + info.Error}
	}
	if info.MajorVersion < requiredMajor {
		return Provision{}, &Errorf{Message: "скачанная Java версии " + info.VersionText +
			", а игре нужна " + strconv.Itoa(requiredMajor)}
	}
	if p.Progress != nil {
		p.Progress.Stage("Java " + info.VersionText + " готова")
	}
	return Provision{Executable: executable, Source: JavaSourceBundled, Version: info.VersionText}, nil
}

func (p JavaProvisioner) stage(text string) {
	if p.Progress != nil {
		p.Progress.Stage(text)
	}
}

// ExecutableIn — исполняемый файл Java внутри каталога рантайма.
func ExecutableIn(root string) string {
	candidates := []string{
		filepath.Join(root, "bin", "javaw.exe"),
		filepath.Join(root, "bin", "java.exe"),
		filepath.Join(root, "bin", "java"),
		filepath.Join(root, "jre.bundle", "Contents", "Home", "bin", "java"),
	}
	for _, candidate := range candidates {
		if info, err := os.Stat(candidate); err == nil && !info.IsDir() {
			return candidate
		}
	}
	return ""
}
