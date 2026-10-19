package core

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// Адреса, которые использует установка игры. Все официальные, без выдумок.
const (
	// VersionManifestURL — список всех версий Minecraft.
	VersionManifestURL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
	// FabricProfileURL — профиль Fabric Loader для конкретной версии игры.
	FabricProfileURL = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json"
	// AssetBaseURL — хранилище ресурсов игры.
	AssetBaseURL = "https://resources.download.minecraft.net/"
)

// Installer устанавливает игру: сам Minecraft, библиотеки, ресурсы, Fabric Loader.
//
// Порядок полностью повторяет официальный лаунчер: JSON версии → клиентский jar →
// библиотеки (с учётом правил ОС) → ресурсы → профиль Fabric. Каждый файл
// проверяется по размеру и SHA-1, поэтому повреждённая загрузка будет замечена
// и файл скачается заново. Установку можно отменить в любой момент.
type Installer struct {
	Paths            Paths
	MinecraftVersion string
	LoaderVersion    string
	// JavaExecutable — путь из настроек; пусто значит «подобрать автоматически».
	JavaExecutable string
	Progress       ProgressListener
	Cancel         *Cancel
}

// Installation — описание установленной версии: всё, что нужно для запуска.
type Installation struct {
	VersionID         string
	FabricID          string
	MainClass         string
	Classpath         []string
	JVMArguments      []any
	GameArguments     []any
	AssetIndexID      string
	NativesDir        string
	LogConfigFile     string
	LogConfigArgument string
	JavaExecutable    string
	JavaSource        string
}

func (i *Installer) stage(text string) {
	if i.Progress != nil {
		i.Progress.Stage(text)
	}
}

func (i *Installer) download(url string, target string, size int64, sha1 string) error {
	return Download(url, target, size, sha1, i.Progress, i.Cancel)
}

// Install скачивает всё необходимое и возвращает описание установленной версии.
func (i *Installer) Install() (*Installation, error) {
	if err := i.Paths.CreateDirectories(); err != nil {
		return nil, err
	}
	i.stage("Готовим каталоги игры")
	version, err := i.ensureVersionJSON()
	if err != nil {
		return nil, err
	}

	i.stage("Клиент Minecraft " + i.MinecraftVersion)
	clientJar := i.Paths.VersionJar(i.MinecraftVersion)
	client := asObject(asObject(version["downloads"])["client"])
	if err := i.download(asString(client["url"], ""), clientJar,
		asInt64(client["size"], -1), asString(client["sha1"], "")); err != nil {
		return nil, err
	}

	i.stage("Библиотеки Minecraft")
	if err := os.MkdirAll(i.Paths.NativesDir(i.MinecraftVersion), 0o755); err != nil {
		return nil, err
	}
	classpath := []string{}
	libraries, err := i.downloadVanillaLibraries(version)
	if err != nil {
		return nil, err
	}
	classpath = append(classpath, libraries...)

	i.stage("Ресурсы игры")
	assetIndexID := asString(asObject(version["assetIndex"])["id"], i.MinecraftVersion)
	if err := i.ensureAssets(version); err != nil {
		return nil, err
	}

	i.stage("Профиль Fabric Loader " + i.LoaderVersion)
	fabric, err := i.ensureFabricProfile()
	if err != nil {
		return nil, err
	}
	fabricLibraries, err := i.downloadFabricLibraries(fabric)
	if err != nil {
		return nil, err
	}
	classpath = append(classpath, fabricLibraries...)

	versionArguments := asObject(version["arguments"])
	fabricArguments := asObject(fabric["arguments"])
	jvmArguments := append(asArray(versionArguments["jvm"]), asArray(fabricArguments["jvm"])...)
	gameArguments := append(asArray(versionArguments["game"]), asArray(fabricArguments["game"])...)

	logConfigFile, logConfigArgument := i.ensureLogging(version)

	// Java: своя подходит — берём её, нет — скачиваем официальный рантайм
	// Mojang, ровно тот, который требует эта версия игры.
	javaVersion := asObject(version["javaVersion"])
	component := asString(javaVersion["component"], DefaultRuntimeComponent)
	requiredMajor := int(asInt64(javaVersion["majorVersion"], RequiredJavaMajor))
	i.stage("Java " + fmt.Sprint(requiredMajor))
	provisioner := JavaProvisioner{Paths: i.Paths, Progress: i.Progress, Cancel: i.Cancel}
	java, err := provisioner.EnsureWith(i.JavaExecutable, component, requiredMajor)
	if err != nil {
		return nil, err
	}

	classpath = append(classpath, clientJar)
	return &Installation{
		VersionID:         i.MinecraftVersion,
		FabricID:          asString(fabric["id"], "fabric-loader-"+i.LoaderVersion+"-"+i.MinecraftVersion),
		MainClass:         asString(fabric["mainClass"], "net.fabricmc.loader.impl.launch.knot.KnotClient"),
		Classpath:         classpath,
		JVMArguments:      jvmArguments,
		GameArguments:     gameArguments,
		AssetIndexID:      assetIndexID,
		NativesDir:        i.Paths.NativesDir(i.MinecraftVersion),
		LogConfigFile:     logConfigFile,
		LogConfigArgument: logConfigArgument,
		JavaExecutable:    java.Executable,
		JavaSource:        string(java.Source),
	}, nil
}

// versionJSONMaxAge — как долго сохранённое описание версии считается свежим.
// Выпущенная версия игры не меняется, а ради одного документа перед каждым
// запуском качать ещё и общий список версий незачем: это лишние мегабайты
// и лишние секунды ожидания.
const versionJSONMaxAge = 24 * time.Hour

func (i *Installer) ensureVersionJSON() (map[string]any, error) {
	target := i.Paths.VersionJSON(i.MinecraftVersion)
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return nil, err
	}
	// Уже скачанное описание используем сразу, без обращения к сети.
	if cached, cacheError := readCachedJSON(target, versionJSONMaxAge); cacheError == nil {
		return cached, nil
	}
	i.stage("Список версий Minecraft")
	manifest, err := GetJSON(VersionManifestURL)
	if err != nil {
		// Нет сети — пробуем уже сохранённый JSON: лаунчер должен уметь запускать
		// установленную игру и без интернета. Тут срок давности не важен.
		if cached, readError := readCachedJSON(target, 0); readError == nil {
			return cached, nil
		}
		return nil, err
	}
	url := ""
	for _, item := range asArray(asObject(manifest)["versions"]) {
		entry := asObject(item)
		if asString(entry["id"], "") == i.MinecraftVersion {
			url = asString(entry["url"], "")
			break
		}
	}
	if url == "" {
		return nil, &Errorf{Message: "версия " + i.MinecraftVersion +
			" отсутствует в официальном манифесте Mojang"}
	}
	i.stage("Описание версии " + i.MinecraftVersion)
	document, err := GetJSON(url)
	if err != nil {
		return nil, err
	}
	encoded, err := json.MarshalIndent(document, "", "  ")
	if err == nil {
		_ = os.WriteFile(target, encoded, 0o644)
	}
	return asObject(document), nil
}

// readCachedJSON читает сохранённый документ, если он не старше maxAge
// (maxAge <= 0 — без проверки возраста).
func readCachedJSON(path string, maxAge time.Duration) (map[string]any, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	if maxAge > 0 && time.Since(info.ModTime()) > maxAge {
		return nil, &Errorf{Message: "сохранённый документ устарел: " + path}
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var parsed any
	if err := json.Unmarshal(data, &parsed); err != nil {
		return nil, err
	}
	object, ok := parsed.(map[string]any)
	if !ok {
		return nil, &Errorf{Message: "сохранённый документ испорчен: " + path}
	}
	return object, nil
}

func (i *Installer) downloadVanillaLibraries(version map[string]any) ([]string, error) {
	// Сначала собираем всё, что нужно скачать, а потом качаем параллельно:
	// библиотек больше сотни, по одной они забирают минуты.
	type nativeWork struct {
		archive string
		library map[string]any
		sha1    string
	}
	var jobs []DownloadJob
	var classpath []string
	var natives []nativeWork

	for _, item := range asArray(version["libraries"]) {
		if i.Cancel.IsCancelled() {
			return nil, ErrCancelled
		}
		library := asObject(item)
		if !Allows(library, Features(false)) {
			continue
		}
		downloads := asObject(library["downloads"])

		if artifact, ok := downloads["artifact"].(map[string]any); ok {
			target := i.Paths.LibraryByPath(asString(artifact["path"], ""))
			jobs = append(jobs, DownloadJob{URL: asString(artifact["url"], ""),
				Target: target, Size: asInt64(artifact["size"], -1),
				SHA1: asString(artifact["sha1"], "")})
			classpath = append(classpath, target)
		}

		natives_, ok := library["natives"].(map[string]any)
		if !ok {
			continue
		}
		classifier := asString(natives_[OSName()], "")
		if classifier == "" {
			continue
		}
		nativeArtifact, ok := asObject(downloads["classifiers"])[classifier].(map[string]any)
		if !ok {
			continue
		}
		target := i.Paths.LibraryByPath(asString(nativeArtifact["path"], ""))
		sum := asString(nativeArtifact["sha1"], "")
		jobs = append(jobs, DownloadJob{URL: asString(nativeArtifact["url"], ""),
			Target: target, Size: asInt64(nativeArtifact["size"], -1), SHA1: sum})
		classpath = append(classpath, target)
		natives = append(natives, nativeWork{archive: target, library: library, sha1: sum})
	}

	if err := DownloadAll(jobs, downloadWorkers, &countingProgress{
		listener: i.Progress, total: int64(len(jobs))}, i.Cancel); err != nil {
		return nil, err
	}
	// Распаковка — только после того, как все архивы точно целые.
	for _, work := range natives {
		if i.Cancel.IsCancelled() {
			return nil, ErrCancelled
		}
		if err := extractNatives(work.archive, work.library,
			i.Paths.NativesDir(i.MinecraftVersion), work.sha1); err != nil {
			return nil, err
		}
	}
	return classpath, nil
}

// nativeStamp — отметка о распаковке: в ней лежит контрольная сумма архива.
func nativeStamp(target string, archive string) string {
	return filepath.Join(target, ".aetherglass-"+Safe(filepath.Base(archive))+".stamp")
}

// extractNatives распаковывает нативные библиотеки. Если этот же архив уже
// распаковывали (отметка совпадает с его контрольной суммой), работа
// пропускается: распаковывать пять архивов при каждом запуске незачем.
func extractNatives(archive string, library map[string]any, target string, sha1 string) error {
	var excluded []string
	if extract, ok := library["extract"].(map[string]any); ok {
		for _, item := range asArray(extract["exclude"]) {
			excluded = append(excluded, fmt.Sprint(item))
		}
	}
	if err := os.MkdirAll(target, 0o755); err != nil {
		return err
	}
	stamp := nativeStamp(target, archive)
	if sha1 != "" {
		if recorded, err := os.ReadFile(stamp); err == nil &&
			strings.TrimSpace(string(recorded)) == sha1 {
			return nil
		}
	}
	reader, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer reader.Close()
	for _, file := range reader.File {
		if file.FileInfo().IsDir() {
			continue
		}
		name := file.Name
		if strings.HasPrefix(name, "META-INF/") || isExcluded(name, excluded) {
			continue
		}
		destination := filepath.Join(target, filepath.FromSlash(name))
		// Защита от записи за пределы каталога.
		relative, err := filepath.Rel(target, destination)
		if err != nil || strings.HasPrefix(relative, "..") {
			continue
		}
		if err := os.MkdirAll(filepath.Dir(destination), 0o755); err != nil {
			return err
		}
		if err := writeZipEntry(file, destination); err != nil {
			return err
		}
	}
	if sha1 != "" {
		_ = os.WriteFile(stamp, []byte(sha1), 0o644)
	}
	return nil
}

func writeZipEntry(file *zip.File, destination string) error {
	source, err := file.Open()
	if err != nil {
		return err
	}
	defer source.Close()
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o644)
	if err != nil {
		return err
	}
	defer output.Close()
	_, err = io.Copy(output, source)
	return err
}

func isExcluded(name string, excluded []string) bool {
	for _, prefix := range excluded {
		if strings.HasPrefix(name, prefix) {
			return true
		}
	}
	return false
}

// ensureAssets качает индекс ресурсов и сами объекты. Объектов больше четырёх
// тысяч, поэтому они загружаются параллельно; повторы (один и тот же файл под
// разными именами) отбрасываются ещё до начала загрузки.
func (i *Installer) ensureAssets(version map[string]any) error {
	index := asObject(version["assetIndex"])
	id := asString(index["id"], i.MinecraftVersion)
	indexFile := filepath.Join(i.Paths.AssetIndexes(), id+".json")
	if err := os.MkdirAll(filepath.Dir(indexFile), 0o755); err != nil {
		return err
	}
	if err := i.download(asString(index["url"], ""), indexFile,
		asInt64(index["size"], -1), asString(index["sha1"], "")); err != nil {
		return err
	}
	data, err := os.ReadFile(indexFile)
	if err != nil {
		return err
	}
	var parsed any
	if err := json.Unmarshal(data, &parsed); err != nil {
		return err
	}
	objects := asObject(asObject(parsed)["objects"])

	var jobs []DownloadJob
	for _, value := range objects {
		object := asObject(value)
		hash := asString(object["hash"], "")
		if hash == "" {
			continue
		}
		jobs = append(jobs, DownloadJob{
			URL:    AssetBaseURL + hash[:2] + "/" + hash,
			Target: i.Paths.AssetObject(hash),
			Size:   asInt64(object["size"], -1),
			SHA1:   hash,
		})
	}
	// Один и тот же объект в индексе встречается под разными именами —
	// качать его дважды бессмысленно.
	jobs = dedupeJobs(jobs)
	total := int64(len(jobs))
	ready := int64(0)
	for _, job := range jobs {
		if FileMatches(job.Target, job.Size, job.SHA1) {
			ready++
		}
	}
	if i.Progress != nil {
		i.Progress.File("ресурсы", ready, total)
	}
	if ready == total {
		return nil
	}
	return DownloadAll(jobs, downloadWorkers, &countingProgress{
		listener: i.Progress, total: total}, i.Cancel)
}

func (i *Installer) ensureFabricProfile() (map[string]any, error) {
	url := fmt.Sprintf(FabricProfileURL, i.MinecraftVersion, i.LoaderVersion)
	directory := i.Paths.VersionDir("fabric-loader-" + i.LoaderVersion + "-" + i.MinecraftVersion)
	target := filepath.Join(directory, "fabric-profile.json")
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return nil, err
	}
	document, err := GetJSON(url)
	if err != nil {
		if cached, readError := os.ReadFile(target); readError == nil {
			var parsed any
			if json.Unmarshal(cached, &parsed) == nil {
				if object, ok := parsed.(map[string]any); ok {
					return object, nil
				}
			}
		}
		return nil, err
	}
	if encoded, err := json.MarshalIndent(document, "", "  "); err == nil {
		_ = os.WriteFile(target, encoded, 0o644)
	}
	return asObject(document), nil
}

func (i *Installer) downloadFabricLibraries(profile map[string]any) ([]string, error) {
	var jobs []DownloadJob
	var classpath []string
	for _, item := range asArray(profile["libraries"]) {
		if i.Cancel.IsCancelled() {
			return nil, ErrCancelled
		}
		library := asObject(item)
		name := asString(library["name"], "")
		url := asString(library["url"], "")
		if name == "" || url == "" {
			continue
		}
		target, err := i.Paths.Library(name, "")
		if err != nil {
			return nil, err
		}
		if !strings.HasSuffix(url, "/") {
			url += "/"
		}
		path, err := MavenPath(name)
		if err != nil {
			return nil, err
		}
		jobs = append(jobs, DownloadJob{URL: url + path, Target: target,
			Size: asInt64(library["size"], -1), SHA1: asString(library["sha1"], "")})
		classpath = append(classpath, target)
	}
	if err := DownloadAll(jobs, downloadWorkers, &countingProgress{
		listener: i.Progress, total: int64(len(jobs))}, i.Cancel); err != nil {
		return nil, err
	}
	return classpath, nil
}

// MavenPath — путь к файлу библиотеки в репозитории Maven:
// group/artifact/version/artifact-version.jar
func MavenPath(name string) (string, error) {
	parts := strings.SplitN(name, ":", 3)
	if len(parts) != 3 {
		return "", &Errorf{Message: "некорректное имя библиотеки: " + name}
	}
	group := strings.ReplaceAll(parts[0], ".", "/")
	return group + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar", nil
}

func (i *Installer) ensureLogging(version map[string]any) (string, string) {
	logging, ok := version["logging"].(map[string]any)
	if !ok {
		return "", ""
	}
	client, ok := logging["client"].(map[string]any)
	if !ok {
		return "", ""
	}
	argument := asString(client["argument"], "")
	file := asObject(client["file"])
	id := asString(file["id"], "")
	if id == "" {
		return "", argument
	}
	target := filepath.Join(i.Paths.LogConfigs(), id)
	if err := i.download(asString(file["url"], ""), target,
		asInt64(file["size"], -1), asString(file["sha1"], "")); err != nil {
		return "", argument
	}
	return target, argument
}
