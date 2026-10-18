//go:build windows

// Package uiweb — интерфейс лаунчера на Chromium (WebView2).
//
// Главное отличие от ui: стекло здесь рисует не наш код на процессоре, а сам
// движок браузера на видеокарте — размытие подложки (backdrop-filter) и
// настоящее преломление (SVG feDisplacementMap). Логика лаунчера не меняется:
// она целиком в core, от установки игры до входа в аккаунт.
package uiweb

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sync"

	webview "github.com/jchv/go-webview2"
	"github.com/lxn/walk"

	"aetherglass/launcher/core"
)

//go:embed webui/index.html
var page string

// ConfigFile и AuthFile лежат рядом с игрой, как и в обычном интерфейсе:
// настройки и вход не теряются при переключении между ними.
func ConfigFile() string {
	return filepath.Join(core.DefaultGameDirectory(), "aurora-launcher.json")
}

func AuthFile() string {
	return filepath.Join(core.DefaultGameDirectory(), "aurora-auth.json")
}

// state — всё, что знает интерфейс. Передаётся в страницу одним объектом,
// чтобы не городить десятки отдельных вызовов.
type state struct {
	Player      string             `json:"player"`
	LoggedIn    bool               `json:"loggedIn"`
	Offline     bool               `json:"offline"`
	OfflineName string             `json:"offlineName"`
	GameDir     string             `json:"gameDir"`
	Version     string             `json:"version"`
	Loader      string             `json:"loader"`
	Java        string             `json:"java"`
	JavaPath    string             `json:"javaPath"`
	MinMemory   int                `json:"minMemory"`
	MaxMemory   int                `json:"maxMemory"`
	Width       int                `json:"width"`
	Height      int                `json:"height"`
	Mods        bool               `json:"mods"`
	ModRows     []core.ModStatus   `json:"modRows"`
	Busy        bool               `json:"busy"`
	Status      string             `json:"status"`
	Kind        string             `json:"kind"`
	Progress    float64            `json:"progress"`
	Installed   string             `json:"installed"`
	ModResults  []core.ModrinthHit `json:"modResults"`
	Build       string             `json:"build"`
	Update      *core.UpdateInfo   `json:"update"`
	ModReady    bool               `json:"modReady"`
	ModFiles    []string           `json:"modFiles"`
	ModOffset   int                `json:"modOffset"`
	ModTotal    int64              `json:"modTotal"`
	ModQuery    string             `json:"modQuery"`
}

type app struct {
	mutex        sync.Mutex
	config       *core.Config
	session      *core.Session
	installation *core.Installation
	java         core.Provision
	cancel       *core.Cancel
	launcher     core.Launcher
	view         webview.WebView
	owner        *walk.MainWindow
	busy         bool
	progress     float64
	modRows      []core.ModStatus
	modResults   []core.ModrinthHit
	modFiles     []string
	modQuery     string
	modOffset    int
	modTotal     int64
	statusText   string
	statusKind   string
	update       *core.UpdateInfo
}

// Run поднимает окно с интерфейсом и запускает цикл сообщений.
func Run() error {
	view := webview.New(false)
	if view == nil {
		return fmt.Errorf("WebView2 не создался")
	}
	application := &app{
		config:  core.LoadConfig(ConfigFile()),
		session: core.LoadSession(AuthFile()),
		view:    view,
	}
	view.SetTitle("AetherGlass")
	view.SetSize(1060, 720, webview.HintMin)
	application.bindAll()
	view.SetHtml(page)
	// Состояние страница запрашивает сама, когда загрузится: пока её скрипт
	// не выполнился, Eval ушёл бы в пустоту.
	view.Run()
	view.Destroy()
	return nil
}

// bind — привязать функцию. Сама функция уходит в отдельный поток: вызов
// приходит в потоке интерфейса, и блокировать его нельзя ни на секунду.
func (a *app) bind(name string, work func() error) {
	if err := a.view.Bind(name, func() error {
		go func() {
			defer func() { _ = recover() }()
			if err := work(); err != nil {
				a.report(err)
			}
		}()
		return nil
	}); err != nil {
		a.log("Не удалось привязать " + name + ": " + err.Error())
	}
}

// bindString — привязать функцию, которая принимает одну строку.
func (a *app) bindString(name string, work func(string) error) {
	if err := a.view.Bind(name, func(value string) error {
		go func() {
			defer func() { _ = recover() }()
			if err := work(value); err != nil {
				a.report(err)
			}
		}()
		return nil
	}); err != nil {
		a.log("Не удалось привязать " + name + ": " + err.Error())
	}
}

func (a *app) bindAll() {
	a.bind("login", a.login)
	a.bind("logout", a.logout)
	// Офлайн-профиль приходит именем, поэтому у него своя привязка.
	if err := a.view.Bind("loginOffline", func(name string) error {
		go func() {
			defer func() { _ = recover() }()
			if err := a.loginOffline(name); err != nil {
				a.report(err)
			}
		}()
		return nil
	}); err != nil {
		a.log("Не удалось привязать loginOffline: " + err.Error())
	}
	a.bind("install", func() error { return a.install(false) })
	a.bind("play", a.play)
	a.bind("applyUpdate", a.applyUpdate)
	a.bind("cancelTask", a.cancelTask)
	a.bind("refreshMods", a.refreshMods)
	a.bind("refreshJava", a.refreshJava)
	a.bind("downloadJava", a.downloadJava)
	a.bind("detectJava", a.detectJava)
	a.bind("pickJava", a.pickJava)
	a.bind("autoJava", a.autoJava)
	a.bind("pickFolder", a.pickFolder)
	a.bind("openFolder", a.openFolder)
	a.bind("openModsFolder", func() error { return a.openGameFolder("mods") })
	a.bind("openLogsFolder", func() error { return a.openGameFolder("logs") })
	// Modrinth: популярное, поиск, догрузка и установка одной кнопкой.
	a.bind("browseMods", func() error { return a.loadMods("", 0, false) })
	a.bind("moreMods", func() error {
		a.mutex.Lock()
		query, offset := a.modQuery, a.modOffset
		a.mutex.Unlock()
		return a.loadMods(query, offset, true)
	})
	a.bindString("searchMods", func(query string) error { return a.loadMods(query, 0, false) })
	a.bindString("installMod", a.installMod)

	// ready — страница загрузилась и готова принимать состояние.
	a.bind("ready", a.ready)
	a.bind("clearLog", func() error {
		a.view.Dispatch(func() { a.view.Eval("AetherGlass.clearLog()") })
		return nil
	})

	// Настройки приходят одной строкой JSON: так проще добавлять поля.
	if err := a.view.Bind("saveSettings", func(payload string) error {
		go func() {
			defer func() { _ = recover() }()
			if err := a.saveSettings(payload); err != nil {
				a.report(err)
			}
		}()
		return nil
	}); err != nil {
		a.log("Не удалось привязать saveSettings: " + err.Error())
	}
}

// ready — страница загрузилась: отдаём состояние и обновляем список модов.
func (a *app) ready() error {
	a.push()
	go a.refreshMods()
	go a.refreshModFiles()
	// Каталог открывается сразу на популярном: ничего вводить не нужно,
	// чтобы увидеть, что вообще есть.
	go func() { _ = a.loadMods("", 0, false) }()
	// Проверка обновлений не должна мешать работе: сеть недоступна — и ладно.
	go a.checkUpdate()
	return nil
}

// ─────────────────────────────── аккаунт ───────────────────────────────

func (a *app) login() error {
	a.setBusy(true)
	defer a.setBusy(false)
	a.setStatus("Открываем страницу входа Microsoft…", "info")
	session, err := core.Login(a.config.ClientID, func(rawURL string) {
		a.log("Если браузер не открылся, перейдите по ссылке:")
		a.log(rawURL)
		openBrowser(rawURL)
		a.setStatus("Ждём подтверждения входа в браузере…", "info")
	}, nil)
	if err != nil {
		return err
	}
	if err := session.Save(AuthFile()); err != nil {
		a.log("Сессию сохранить не удалось: " + err.Error())
	}
	a.mutex.Lock()
	a.session = session
	a.mutex.Unlock()
	a.setStatus("Вход выполнен: "+session.PlayerName, "good")
	a.push()
	return nil
}

func (a *app) logout() error {
	_ = os.Remove(AuthFile())
	a.mutex.Lock()
	a.session = nil
	a.mutex.Unlock()
	a.setStatus("Выход выполнен", "muted")
	a.push()
	return nil
}

// loginOffline — офлайн-профиль: игра без входа в Microsoft.
//
// Никакого обхода защиты здесь нет: токена у такого профиля нет вообще,
// поэтому одиночная игра и локальная сеть работают, а серверы с проверкой
// лицензии игрока не пустят — подтвердить сессию будет нечем.
func (a *app) loginOffline(name string) error {
	session, err := core.NewOfflineSession(name)
	if err != nil {
		return err
	}
	if err := session.Save(AuthFile()); err != nil {
		a.log("Профиль сохранить не удалось: " + err.Error())
	}
	a.mutex.Lock()
	a.session = session
	a.config.OfflineName = session.PlayerName
	a.mutex.Unlock()
	if err := a.config.Save(ConfigFile()); err != nil {
		a.log("Настройки сохранить не удалось: " + err.Error())
	}
	a.setStatus("Офлайн-профиль: "+session.PlayerName+
		" — серверы с проверкой лицензии не пустят", "warn")
	a.push()
	return nil
}

func openBrowser(rawURL string) {
	command := exec.Command("rundll32", "url.dll,FileProtocolHandler", rawURL)
	_ = command.Start()
}

// ─────────────────────────────── установка и запуск ───────────────────────────────

func (a *app) install(thenPlay bool) error {
	a.runTask(func(cancel *core.Cancel) error {
		installer := &core.Installer{
			Paths:            a.config.Paths(),
			MinecraftVersion: a.config.SelectedVersion,
			LoaderVersion:    core.DefaultLoaderVersion,
			JavaExecutable:   a.config.JavaExecutable,
			Cancel:           cancel,
			Progress:         &webProgress{app: a},
		}
		if err := installer.Paths.CreateDirectories(); err != nil {
			return err
		}
		a.log("Устанавливаем Minecraft " + a.config.SelectedVersion +
			" (Fabric " + core.DefaultLoaderVersion + ")")
		installation, err := installer.Install()
		if err != nil {
			return err
		}
		a.mutex.Lock()
		a.installation = installation
		a.java = core.Provision{Executable: installation.JavaExecutable,
			Source: core.JavaSource(installation.JavaSource)}
		a.mutex.Unlock()
		a.log("Игра установлена")
		a.log("Java для игры: " + installation.JavaExecutable +
			" (" + installation.JavaSource + ")")
		if !a.config.InstallMods {
			return nil
		}
		manifest, err := core.EmbeddedModManifest()
		if err != nil {
			return err
		}
		mods := &core.ModInstaller{
			Paths:    a.config.Paths(),
			Manifest: manifest,
			Cancel:   cancel,
			Progress: &webProgress{app: a},
		}
		installed, err := mods.Install()
		if err != nil {
			return err
		}
		a.log("Модов установлено: " + fmt.Sprint(len(installed)))
		target, err := mods.InstallClientMod()
		if err != nil {
			return err
		}
		a.log("Клиент AetherGlass: " + target)
		return nil
	}, func(err error) {
		go a.refreshJava()
		go a.refreshMods()
		if err != nil {
			if err == core.ErrCancelled {
				a.setStatus("Отменено", "warn")
				a.log("Отменено")
				return
			}
			a.report(err)
			return
		}
		a.setProgress(100)
		if thenPlay {
			_ = a.play()
			return
		}
		a.setStatus("Готово: можно нажимать «Играть»", "good")
	})
	return nil
}

func (a *app) play() error {
	a.mutex.Lock()
	installation := a.installation
	a.mutex.Unlock()
	if installation == nil {
		return a.install(true)
	}
	a.setBusy(true)
	go func() {
		err := a.startGame()
		a.setBusy(false)
		go a.refreshJava()
		if err != nil {
			a.report(err)
			return
		}
		a.setStatus("Игра запущена", "good")
		// Игра пошла. Дальше лаунчер только ест память, пока игрок в мире,
		// поэтому окно закрывается, а вместе с ним выгружается и процесс.
		a.closeAfterLaunch()
	}()
	return nil
}

// checkUpdate спрашивает GitHub, не вышло ли обновление. Тихий исход —
// обновления нет или сеть недоступна: лаунчер обязан работать и так.
func (a *app) checkUpdate() {
	info, err := core.CheckUpdate(core.Version)
	if err != nil || info == nil {
		return
	}
	a.mutex.Lock()
	a.update = info
	a.mutex.Unlock()
	a.push()
}

// applyUpdate скачивает новый exe, подменяет им себя и перезапускается.
func (a *app) applyUpdate() error {
	a.mutex.Lock()
	info := a.update
	a.mutex.Unlock()
	if info == nil {
		return &core.Errorf{Message: "обновление не найдено — проверьте позже"}
	}
	a.setBusy(true)
	go func() {
		defer a.setBusy(false)
		path, err := core.DownloadUpdate(info, func(done int64, total int64) {
			if total > 0 {
				a.setProgress(float64(done) / float64(total))
			}
		})
		if err != nil {
			a.report(err)
			return
		}
		if err := core.ApplyUpdate(path); err != nil {
			a.report(err)
			return
		}
		a.setStatus("Обновление установлено, перезапуск", "good")
		a.closeAfterLaunch()
	}()
	return nil
}

// closeAfterLaunch закрывает окно лаунчера.
//
// Terminate останавливает главный цикл WebView2: Run возвращает управление,
// окно разрушается, и процесс завершается сам — без os.Exit, который оборвал
// бы фоновые горутины посреди записи файла.
func (a *app) closeAfterLaunch() {
	a.mutex.Lock()
	view := a.view
	a.mutex.Unlock()
	if view == nil {
		return
	}
	view.Dispatch(func() { view.Terminate() })
}

func (a *app) startGame() error {
	a.mutex.Lock()
	session := a.session
	installation := a.installation
	a.mutex.Unlock()

	if session == nil || !session.Valid() {
		if session != nil && session.RefreshToken != "" {
			refreshed, err := core.Refresh(session)
			if err == nil {
				_ = refreshed.Save(AuthFile())
				a.mutex.Lock()
				a.session = refreshed
				a.mutex.Unlock()
				session = refreshed
				a.log("Сессия обновлена")
			} else {
				a.log("Сессию обновить не удалось: " + err.Error())
			}
		}
	}
	if session == nil || !session.Valid() {
		return &core.Errorf{Message: "сначала войдите: через Microsoft или " +
			"создайте офлайн-профиль (кнопка «Сменить аккаунт»)"}
	}
	plan, err := a.launcher.Plan(installation, a.config.Paths(), a.config, session)
	if err != nil {
		return err
	}
	a.log("Запуск: " + plan.Executable)
	if err := a.launcher.Launch(plan, func(line string) { a.log(line) },
		func() { a.log("Игра завершена") }); err != nil {
		return err
	}
	return nil
}

// ─────────────────────────────── моды ───────────────────────────────

func (a *app) refreshMods() error {
	manifest, err := core.EmbeddedModManifest()
	if err != nil {
		return err
	}
	a.mutex.Lock()
	paths := a.config.Paths()
	a.mutex.Unlock()
	rows := (&core.ModInstaller{Paths: paths, Manifest: manifest}).Status()
	a.mutex.Lock()
	a.modRows = rows
	a.mutex.Unlock()
	a.push()
	return nil
}

// ─────────────────────────────── Modrinth ───────────────────────────────

// loadMods — загрузить страницу каталога.
//
// Пустой запрос означает «самые популярные»: Modrinth сам отсортирует по
// числу загрузок. keep=true дописывает следующую страницу к уже показанной —
// так список листается почти бесконечно: под нашу версию игры в каталоге
// почти восемнадцать тысяч модов.
func (a *app) loadMods(query string, offset int, keep bool) error {
	results, err := core.SearchModrinth(query, core.DefaultMinecraftVer,
		"fabric", 0, offset, nil)
	if err != nil {
		return err
	}
	a.mutex.Lock()
	if keep {
		a.modResults = append(a.modResults, results.Hits...)
	} else {
		a.modResults = results.Hits
	}
	a.modQuery = query
	a.modOffset = offset + len(results.Hits)
	a.modTotal = results.TotalHits
	shown := len(a.modResults)
	a.mutex.Unlock()

	if query == "" {
		a.log("Modrinth: показано " + fmt.Sprint(shown) + " из " +
			fmt.Sprint(results.TotalHits) + " популярных модов")
	} else {
		a.log("Modrinth по запросу «" + query + "»: всего " +
			fmt.Sprint(results.TotalHits) + ", показано " + fmt.Sprint(shown))
	}
	a.setStatus("Показано модов: "+fmt.Sprint(shown)+" из "+fmt.Sprint(results.TotalHits), "good")
	a.push()
	return nil
}

// installMod — одна кнопка: свежая подходящая версия → файл в папку модов.
func (a *app) installMod(projectID string) error {
	a.mutex.Lock()
	title := projectID
	for _, hit := range a.modResults {
		if hit.ProjectID == projectID {
			title = hit.Title
			break
		}
	}
	a.mutex.Unlock()
	a.runTask(func(cancel *core.Cancel) error {
		version, err := core.LatestModrinthVersion(projectID,
			core.DefaultMinecraftVer, "fabric", cancel)
		if err != nil {
			return err
		}
		file, ok := version.PrimaryFile()
		if !ok {
			return &core.Errorf{Message: "у этой версии мода нет файла"}
		}
		installer := &core.ModrinthInstaller{Paths: a.config.Paths(),
			Cancel: cancel, Progress: &webProgress{app: a}}
		if err := installer.Paths.CreateDirectories(); err != nil {
			return err
		}
		a.log("Ставим " + title + " " + version.VersionNumber + " (" + file.Name + ")")
		target, err := installer.Install(version)
		if err != nil {
			return err
		}
		a.log("Готово: " + target)
		// Зависимости Modrinth не тянет сам: честно говорим, чего не хватает.
		for _, dependency := range version.RequiredDependencies() {
			a.log("Этому моду нужна зависимость " + dependency.ProjectID +
				" — если игра не запустится, поставьте и её")
		}
		return nil
	}, func(err error) {
		go a.refreshModFiles()
		if err != nil {
			if err == core.ErrCancelled {
				a.setStatus("Отменено", "warn")
				return
			}
			a.report(err)
			return
		}
		a.setStatus("Мод установлен: "+title, "good")
	})
	return nil
}

// refreshModFiles — перечитать, что уже лежит в папке модов.
func (a *app) refreshModFiles() error {
	a.mutex.Lock()
	paths := a.config.Paths()
	a.mutex.Unlock()
	files, err := core.InstalledModFiles(paths)
	if err != nil {
		return err
	}
	a.mutex.Lock()
	a.modFiles = files
	a.mutex.Unlock()
	a.push()
	return nil
}

// ─────────────────────────────── Java ───────────────────────────────

// refreshJava — ядро не угадывает Java заново: показываем то, что уже
// подобрано при установке, либо путь, указанный игроком вручную.
func (a *app) refreshJava() error {
	a.push()
	return nil
}

func (a *app) downloadJava() error {
	a.runTask(func(cancel *core.Cancel) error {
		provisioner := core.JavaProvisioner{
			Paths:    a.config.Paths(),
			Cancel:   cancel,
			Progress: &webProgress{app: a},
		}
		if err := provisioner.Paths.CreateDirectories(); err != nil {
			return err
		}
		java, err := provisioner.DownloadRuntime(core.DefaultRuntimeComponent,
			core.RequiredJavaMajor)
		if err != nil {
			return err
		}
		a.mutex.Lock()
		a.java = java
		a.mutex.Unlock()
		a.log("Java готова: " + java.Executable + " (Java " + java.Version + ")")
		return nil
	}, func(err error) {
		go a.refreshJava()
		if err != nil && err != core.ErrCancelled {
			a.report(err)
			return
		}
		if err == nil {
			a.setStatus("Java готова", "good")
		}
	})
	return nil
}

func (a *app) detectJava() error {
	info := core.Detect()
	if info.Valid() {
		a.log("Системная Java: " + info.Executable + " (Java " + info.VersionText + ")")
		a.setStatus("Системная Java: "+info.VersionText, "good")
		return nil
	}
	a.log("Системная Java: " + info.Error)
	a.setStatus(info.Error, "warn")
	return nil
}

func (a *app) pickJava() error {
	path, err := a.openFile("javaw.exe или java.exe", "Программы (*.exe)|*.exe")
	if err != nil || path == "" {
		return nil
	}
	info := core.Inspect(path)
	if !info.Valid() {
		a.setStatus(info.Error, "bad")
		return nil
	}
	if !info.MeetsRequirement() {
		a.setStatus("Этой Java версии "+info.VersionText+", а игре нужна "+
			fmt.Sprint(core.RequiredJavaMajor)+" или новее", "warn")
		return nil
	}
	a.config.JavaExecutable = path
	_ = a.config.Save(ConfigFile())
	a.mutex.Lock()
	a.installation = nil
	a.mutex.Unlock()
	a.setStatus("Java выбрана: "+path, "good")
	return a.refreshJava()
}

func (a *app) autoJava() error {
	a.config.JavaExecutable = ""
	_ = a.config.Save(ConfigFile())
	a.mutex.Lock()
	a.installation = nil
	a.mutex.Unlock()
	a.setStatus("Java будет скачана автоматически при установке", "info")
	return a.refreshJava()
}

// ─────────────────────────────── настройки ───────────────────────────────

type settingsPayload struct {
	GameDirectory string `json:"gameDirectory"`
	JavaPath      string `json:"javaPath"`
	MinMemory     int    `json:"minMemory"`
	MaxMemory     int    `json:"maxMemory"`
	Width         int    `json:"width"`
	Height        int    `json:"height"`
	Mods          bool   `json:"mods"`
}

func (a *app) saveSettings(payload string) error {
	var incoming settingsPayload
	if err := json.Unmarshal([]byte(payload), &incoming); err != nil {
		return err
	}
	a.mutex.Lock()
	if incoming.GameDirectory != "" && incoming.GameDirectory != a.config.GameDirectory {
		a.config.GameDirectory = incoming.GameDirectory
		a.installation = nil
	}
	a.config.JavaExecutable = incoming.JavaPath
	if incoming.MinMemory > 0 {
		a.config.MinMemoryMb = incoming.MinMemory
	}
	if incoming.MaxMemory > 0 {
		a.config.MaxMemoryMb = incoming.MaxMemory
	}
	if incoming.Width > 0 {
		a.config.WindowWidth = incoming.Width
	}
	if incoming.Height > 0 {
		a.config.WindowHeight = incoming.Height
	}
	a.config.InstallMods = incoming.Mods
	a.mutex.Unlock()
	if err := a.config.Save(ConfigFile()); err != nil {
		return err
	}
	a.setStatus("Настройки сохранены", "good")
	go a.refreshMods()
	a.push()
	return nil
}

func (a *app) pickFolder() error {
	path, err := a.browseFolder()
	if err != nil || path == "" {
		return nil
	}
	a.config.GameDirectory = path
	_ = a.config.Save(ConfigFile())
	a.mutex.Lock()
	a.installation = nil
	a.mutex.Unlock()
	go a.refreshMods()
	a.push()
	return nil
}

func (a *app) openFolder() error {
	return a.openGameFolder("")
}

// Используем ту же раскладку каталогов, что установка и запуск игры.
// Положение exe не влияет на выбор папки.
func (a *app) openGameFolder(subdirectory string) error {
	a.mutex.Lock()
	paths := a.config.Paths()
	a.mutex.Unlock()
	directory := paths.Base
	switch subdirectory {
	case "":
	case "mods":
		directory = paths.Mods()
	case "logs":
		directory = paths.Logs()
	default:
		return fmt.Errorf("неизвестная папка игры: %q", subdirectory)
	}
	// Можно открыть папку ещё до первой установки игры.
	if err := os.MkdirAll(directory, 0755); err != nil {
		return fmt.Errorf("не удалось создать папку %q: %w", directory, err)
	}
	command := exec.Command("explorer.exe", directory)
	if err := command.Start(); err != nil {
		return fmt.Errorf("не удалось открыть папку %q: %w", directory, err)
	}
	// Освобождаем дескриптор: ждать закрытия Проводника не нужно.
	if err := command.Process.Release(); err != nil {
		return fmt.Errorf("папка %q передана Проводнику, но не удалось освободить процесс: %w", directory, err)
	}
	a.log("Открытие папки: " + directory)
	return nil
}

// ─────────────────────────────── общая работа ───────────────────────────────

// runTask — единая точка долгой работы: отмена, занятость кнопок и итог.
func (a *app) runTask(task func(cancel *core.Cancel) error, done func(error)) {
	a.mutex.Lock()
	if a.cancel != nil {
		a.mutex.Unlock()
		return
	}
	cancel := &core.Cancel{}
	a.cancel = cancel
	a.mutex.Unlock()

	a.setBusy(true)
	go func() {
		err := task(cancel)
		a.mutex.Lock()
		a.cancel = nil
		a.mutex.Unlock()
		a.setBusy(false)
		done(err)
	}()
}

func (a *app) cancelTask() error {
	a.mutex.Lock()
	cancel := a.cancel
	a.mutex.Unlock()
	if cancel != nil {
		cancel.Cancel()
		a.log("Отменяем…")
	}
	return nil
}

func (a *app) setBusy(busy bool) {
	a.mutex.Lock()
	a.busy = busy
	a.mutex.Unlock()
	a.push()
}

func (a *app) setProgress(percent float64) {
	if percent < 0 {
		percent = 0
	}
	if percent > 100 {
		percent = 100
	}
	a.mutex.Lock()
	a.progress = percent
	a.mutex.Unlock()
	a.push()
}

func (a *app) setStatus(text string, kind string) {
	a.log("→ " + text)
	a.mutex.Lock()
	a.statusText = text
	a.statusKind = kind
	a.mutex.Unlock()
	a.push()
}

func (a *app) report(err error) {
	text := err.Error()
	a.log("Ошибка: " + text)
	a.setStatus("Ошибка: "+text, "bad")
}

func (a *app) log(line string) {
	if a.view == nil {
		return
	}
	encoded, err := json.Marshal(line)
	if err != nil {
		return
	}
	javascript := "AetherGlass.log(" + string(encoded) + ")"
	a.view.Dispatch(func() { a.view.Eval(javascript) })
}

// push — отправить странице текущее состояние.
func (a *app) push() {
	if a.view == nil {
		return
	}
	snapshot := a.snapshot()
	encoded, err := json.Marshal(snapshot)
	if err != nil {
		return
	}
	javascript := "AetherGlass.state(" + string(encoded) + ")"
	a.view.Dispatch(func() { a.view.Eval(javascript) })
}

func (a *app) snapshot() state {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	player := ""
	loggedIn := false
	offline := false
	if a.session != nil && a.session.Valid() {
		player = a.session.PlayerName
		loggedIn = true
		offline = a.session.Offline
	}
	installed := ""
	if a.installation != nil {
		installed = a.installation.VersionID
	}
	// Клиент встроен в лаунчер и раскладывается при установке: плитка должна
	// показывать правду — файл либо есть в папке модов, либо его нет.
	modReady := false
	if info, err := os.Stat(filepath.Join(a.config.Paths().Mods(), core.ClientModName)); err == nil {
		modReady = !info.IsDir() && info.Size() > 0
	}
	java := a.java.Executable
	manual := a.config.JavaExecutable
	if manual != "" {
		java = manual + " (указана вручную)"
	}
	if java == "" {
		java = "подберётся при установке"
	}
	rows := a.modRows
	if rows == nil {
		rows = []core.ModStatus{}
	}
	name := a.config.OfflineName
	if name == "" && offline {
		name = player
	}
	return state{
		Player:      player,
		LoggedIn:    loggedIn,
		Offline:     offline,
		OfflineName: name,
		GameDir:     a.config.Paths().Base,
		Version:     core.DefaultMinecraftVer,
		Loader:      core.DefaultLoaderVersion,
		Java:        java,
		JavaPath:    a.config.JavaExecutable,
		MinMemory:   a.config.MinMemoryMb,
		MaxMemory:   a.config.MaxMemoryMb,
		Width:       a.config.WindowWidth,
		Height:      a.config.WindowHeight,
		Mods:        a.config.InstallMods,
		ModRows:     rows,
		Busy:        a.busy,
		Status:      a.statusText,
		Kind:        a.statusKind,
		Progress:    a.progress,
		Installed:   installed,
		ModReady:    modReady,
		Build:       core.BuildVersion(),
		Update:      a.update,
		ModResults:  a.modResults,
		ModFiles:    a.modFiles,
		ModOffset:   a.modOffset,
		ModTotal:    a.modTotal,
		ModQuery:    a.modQuery,
	}
}

// webProgress — ход загрузки: этапы и проценты уходят на страницу.
type webProgress struct {
	app *app
}

func (p *webProgress) Stage(text string) {
	p.app.log("• " + text)
	p.app.setStatus(text, "info")
}

func (p *webProgress) File(name string, done int64, total int64) {
	if total <= 0 {
		return
	}
	p.app.setProgress(float64(done) * 100 / float64(total))
}

// ─────────────────────────────── диалоги ───────────────────────────────

// hiddenOwner — невидимое окно: системным диалогам нужен владелец, а само
// окно лаунчера принадлежит браузеру, а не walk.
func (a *app) hiddenOwner() *walk.MainWindow {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	if a.owner != nil {
		return a.owner
	}
	window, err := walk.NewMainWindow()
	if err != nil {
		return nil
	}
	window.SetVisible(false)
	a.owner = window
	return window
}

func (a *app) browseFolder() (string, error) {
	owner := a.hiddenOwner()
	if owner == nil {
		return "", fmt.Errorf("не удалось открыть диалог выбора каталога")
	}
	dialog := &walk.FileDialog{Title: "Каталог игры"}
	accepted, err := dialog.ShowBrowseFolder(owner)
	if err != nil || !accepted {
		return "", err
	}
	return dialog.FilePath, nil
}

func (a *app) openFile(title string, filter string) (string, error) {
	owner := a.hiddenOwner()
	if owner == nil {
		return "", fmt.Errorf("не удалось открыть диалог выбора файла")
	}
	dialog := &walk.FileDialog{Title: title, Filter: filter}
	accepted, err := dialog.ShowOpen(owner)
	if err != nil || !accepted {
		return "", err
	}
	return dialog.FilePath, nil
}
