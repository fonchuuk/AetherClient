//go:build windows

// Package ui — нативный интерфейс лаунчера на Win32 (библиотека walk).
// Никакой Java для самого лаунчера и никаких jar: обычные окна Windows,
// нарисованные в одном стиле с игрой — тёмное «жидкое стекло».
package ui

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"

	"github.com/lxn/walk"
	. "github.com/lxn/walk/declarative"

	"aetherglass/launcher/core"
)

// ConfigFile и AuthFile лежат в одном и том же месте, независимо от того,
// куда пользователь переставил каталог игры.
func ConfigFile() string {
	return filepath.Join(core.DefaultGameDirectory(), "aurora-launcher.json")
}

// AuthFile — сохранённый вход.
func AuthFile() string {
	return filepath.Join(core.DefaultGameDirectory(), "aurora-auth.json")
}

// controller — состояние лаунчера: настройки, сессия, установленная игра и
// та Java, которую он подобрал для запуска.
type controller struct {
	mutex        sync.Mutex
	config       *core.Config
	session      *core.Session
	installation *core.Installation
	java         core.Provision
	cancel       *core.Cancel
	launcher     core.Launcher
}

// creatable — виджет, который нужно донастроить сразу после создания окна.
type creatable interface {
	afterCreate()
}

// Run создаёт окно лаунчера и запускает цикл сообщений Windows.
func Run() error {
	initTheme()

	app := &controller{config: core.LoadConfig(ConfigFile())}
	app.session = core.LoadSession(AuthFile())

	var mainWindow *walk.MainWindow
	var logView *walk.TextEdit
	var dirEdit, javaEdit *walk.LineEdit
	var minMemory, maxMemory, widthEdit, heightEdit *walk.NumberEdit

	// ── подписи ──
	titleText := newGlassText("AetherGlass", fontTitle, cText, 34)
	subtitleText := newGlassText("Minecraft "+core.DefaultMinecraftVer+" · Fabric "+
		core.DefaultLoaderVersion+" · свой клиент", fontSmall, cMuted, 16)
	footerText := newGlassText("Лаунчеру Java не нужна: он нативный. Java скачивается "+
		"только для игры — официальный рантайм Mojang.", fontSmall, cMuted, 16)

	accountTitle := newGlassText("Аккаунт Microsoft", fontHead, cText, 24)
	playerText := newGlassText("Игрок: вход не выполнен", fontHead, cText, 26)
	accountHint := newGlassText("Вход открывается в браузере: пароль вводится на сайте "+
		"Microsoft, лаунчер его не видит и не хранит.", fontSmall, cMuted, 28)
	accountHint.wrap = true

	gameTitle := newGlassText("Minecraft "+core.DefaultMinecraftVer+" + Fabric "+
		core.DefaultLoaderVersion, fontHead, cText, 24)
	gameInfo := newGlassText("Игра ещё не установлена: нажмите «Установить».", fontBody, cMuted, 32)
	gameInfo.wrap = true
	javaInfo := newGlassText("Java для игры: подберётся при установке", fontBody, cMuted, 20)

	javaTitle := newGlassText("Java для игры", fontHead, cText, 24)
	javaState := newGlassText("Java не выбрана — лаунчер поставит официальную Java 21 сам",
		fontBody, cText, 30)
	javaState.wrap = true
	javaHint := newGlassText("Лаунчеру Java не нужна вообще: он нативный exe. Java нужна "+
		"только самой игре, и лаунчер кладёт её в каталог игры, не трогая систему.",
		fontSmall, cMuted, 42)
	javaHint.wrap = true

	settingsTitle := newGlassText("Настройки", fontHead, cText, 24)
	dirLabel := newGlassText("Каталог игры", fontBody, cText, 18)
	memoryLabel := newGlassText("Память для игры, МБ", fontBody, cText, 18)
	windowLabel := newGlassText("Размер окна игры (0 — как в Minecraft)", fontBody, cText, 18)
	minLabel := newGlassText("минимум", fontSmall, cMuted, 18)
	maxLabel := newGlassText("максимум", fontSmall, cMuted, 18)
	widthLabel := newGlassText("ширина", fontSmall, cMuted, 18)
	heightLabel := newGlassText("высота", fontSmall, cMuted, 18)

	modsTitle := newGlassText("Моды оптимизации (10 штук + Fabric API)", fontHead, cText, 24)
	modsHint := newGlassText("Список собирается из встроенного манифеста: версии, размеры "+
		"и контрольные суммы проверены по Modrinth.", fontSmall, cMuted, 26)
	modsHint.wrap = true

	logTitle := newGlassText("Лог", fontHead, cText, 24)

	progress := newGlassProgress()
	pill := newStatusPill("Готов к работе")
	modsList := newModsPanel(14)

	// ── кнопки ──
	loginButton := newGlassButton("Войти через Microsoft", buttonAccent, 260, 40, nil)
	logoutButton := newGlassButton("Выйти из аккаунта", buttonGlass, 190, 34, nil)
	installButton := newGlassButton("Установить / обновить", buttonGlass, 210, 38, nil)
	playButton := newGlassButton("Играть", buttonAccent, 180, 44, nil)
	cancelButton := newGlassButton("Отменить", buttonDanger, 150, 38, nil)
	javaDownloadButton := newGlassButton("Скачать официальную Java 21 заново",
		buttonAccent, 300, 36, nil)
	javaCheckButton := newGlassButton("Что нашлось в системе", buttonGlass, 220, 36, nil)
	javaPickButton := newGlassButton("Выбрать…", buttonGlass, 110, 30, nil)
	javaAutoButton := newGlassButton("Автоматически", buttonGlass, 140, 30, nil)
	dirPickButton := newGlassButton("Выбрать…", buttonGlass, 110, 30, nil)
	modsRefreshButton := newGlassButton("Обновить список", buttonGlass, 170, 32, nil)
	logClearButton := newGlassButton("Очистить", buttonGlass, 120, 32, nil)
	logOpenButton := newGlassButton("Открыть каталог игры", buttonGlass, 200, 32, nil)

	tabAccount := newGlassButton("Аккаунт", buttonTab, 0, 40, nil)
	tabGame := newGlassButton("Игра", buttonTab, 0, 40, nil)
	tabJava := newGlassButton("Java", buttonTab, 0, 40, nil)
	tabSettings := newGlassButton("Настройки", buttonTab, 0, 40, nil)
	tabMods := newGlassButton("Моды", buttonTab, 0, 40, nil)
	tabLog := newGlassButton("Лог", buttonTab, 0, 40, nil)

	modsSwitch := newGlassSwitch("Ставить моды оптимизации вместе с игрой",
		app.config.InstallMods, func(on bool) {
			app.config.InstallMods = on
			saveConfig(app.config)
		})

	// ── служебные функции окна ──
	appendLog := func(text string) {
		if logView == nil {
			return
		}
		mainWindow.Synchronize(func() { logView.AppendText(text + "\r\n") })
	}
	setStatus := func(text string, dot walk.Color) {
		pill.Set(text, dot)
	}
	setProgress := func(percent float64) {
		progress.Set(percent)
	}
	refreshAccount := func() {
		app.mutex.Lock()
		session := app.session
		app.mutex.Unlock()
		if session != nil && session.PlayerName != "" {
			playerText.SetText("Игрок: " + session.PlayerName)
			loginButton.SetText("Войти заново")
			logoutButton.SetEnabled(true)
		} else {
			playerText.SetText("Игрок: вход не выполнен")
			loginButton.SetText("Войти через Microsoft")
			logoutButton.SetEnabled(false)
		}
	}
	refreshJava := func() {
		app.mutex.Lock()
		java := app.java
		explicit := app.config.JavaExecutable
		app.mutex.Unlock()
		if explicit != "" {
			javaState.SetText("Java указана вручную: " + explicit)
			javaInfo.SetText("Java для игры: " + explicit + " (указана вручную)")
			return
		}
		if java.Executable != "" {
			javaState.SetText(java.Executable + "\nJava " + java.Version +
				" · источник: " + string(java.Source))
			javaInfo.SetText("Java для игры: Java " + java.Version + " (" + string(java.Source) + ")")
			return
		}
		javaState.SetText("Java не выбрана — лаунчер поставит официальную Java 21 сам")
		javaInfo.SetText("Java для игры: подберётся при установке")
	}
	refreshMods := func() {
		go func() {
			manifest, err := core.EmbeddedModManifest()
			if err != nil {
				return
			}
			app.mutex.Lock()
			paths := app.config.Paths()
			app.mutex.Unlock()
			mods := &core.ModInstaller{Paths: paths, Manifest: manifest}
			rows := mods.Status()
			mainWindow.Synchronize(func() { modsList.Set(rows) })
		}()
	}
	setBusy := func(busy bool) {
		installButton.SetEnabled(!busy)
		playButton.SetEnabled(!busy)
		loginButton.SetEnabled(!busy)
		javaDownloadButton.SetEnabled(!busy)
		javaCheckButton.SetEnabled(!busy)
		cancelButton.SetEnabled(busy)
	}

	// Единая точка выполнения долгой работы: отмена, прогресс и возврат кнопок.
	runTask := func(task func(cancel *core.Cancel) error, onDone func(error)) {
		app.mutex.Lock()
		if app.cancel != nil {
			app.mutex.Unlock()
			return
		}
		cancel := &core.Cancel{}
		app.cancel = cancel
		app.mutex.Unlock()
		setBusy(true)
		go func() {
			err := task(cancel)
			app.mutex.Lock()
			app.cancel = nil
			app.mutex.Unlock()
			setBusy(false)
			onDone(err)
		}()
	}

	install := func(thenPlay bool) {
		runTask(func(cancel *core.Cancel) error {
			installer := &core.Installer{
				Paths:            app.config.Paths(),
				MinecraftVersion: app.config.SelectedVersion,
				LoaderVersion:    core.DefaultLoaderVersion,
				JavaExecutable:   app.config.JavaExecutable,
				Cancel:           cancel,
				Progress:         &taskProgress{stage: setStatus, file: setProgress, log: appendLog},
			}
			if err := installer.Paths.CreateDirectories(); err != nil {
				return err
			}
			appendLog("Устанавливаем Minecraft " + app.config.SelectedVersion +
				" (Fabric " + core.DefaultLoaderVersion + ")")
			installation, err := installer.Install()
			if err != nil {
				return err
			}
			app.mutex.Lock()
			app.installation = installation
			app.java = core.Provision{Executable: installation.JavaExecutable,
				Source: core.JavaSource(installation.JavaSource)}
			app.mutex.Unlock()
			appendLog("Игра установлена")
			appendLog("Java для игры: " + installation.JavaExecutable +
				" (" + installation.JavaSource + ")")
			gameInfo.SetText("Игра установлена: версия " + installation.VersionID +
				", библиотек " + fmt.Sprint(len(installation.Classpath)))
			if !app.config.InstallMods {
				return nil
			}
			manifest, err := core.EmbeddedModManifest()
			if err != nil {
				return err
			}
			mods := &core.ModInstaller{
				Paths:    app.config.Paths(),
				Manifest: manifest,
				Cancel:   cancel,
				Progress: &taskProgress{stage: setStatus, file: setProgress, log: appendLog},
			}
			installed, err := mods.Install()
			if err != nil {
				return err
			}
			appendLog("Модов установлено: " + fmt.Sprint(len(installed)))
			target, err := mods.InstallClientMod()
			if err != nil {
				return err
			}
			appendLog("Клиент AetherGlass: " + target)
			return nil
		}, func(err error) {
			refreshJava()
			refreshMods()
			if err != nil {
				if err == core.ErrCancelled {
					setStatus("Отменено", cWarn)
					appendLog("Отменено")
					return
				}
				setStatus("Ошибка: "+err.Error(), cBad)
				appendLog("Ошибка: " + err.Error())
				walk.MsgBox(mainWindow, "AetherGlass", err.Error(), walk.MsgBoxIconError)
				return
			}
			setProgress(100)
			if thenPlay {
				play()
			} else {
				setStatus("Готово: можно нажимать «Играть»", cGood)
			}
		})
	}

	play = func() {
		app.mutex.Lock()
		installation := app.installation
		app.mutex.Unlock()
		if installation == nil {
			install(true)
			return
		}
		setBusy(true)
		go func() {
			err := startGame(app, appendLog)
			setBusy(false)
			refreshJava()
			if err != nil {
				setStatus("Ошибка запуска: "+err.Error(), cBad)
				appendLog("Ошибка запуска: " + err.Error())
				walk.MsgBox(mainWindow, "AetherGlass", err.Error(), walk.MsgBoxIconError)
				return
			}
			setStatus("Игра запущена", cGood)
		}()
	}

	// ── обработчики кнопок ──
	loginButton.onClick = func() {
		setBusy(true)
		go func() {
			session, err := login(app, appendLog, setStatus)
			setBusy(false)
			if err != nil {
				if err == core.ErrCancelled {
					return
				}
				appendLog("Ошибка входа: " + err.Error())
				walk.MsgBox(mainWindow, "Вход", err.Error(), walk.MsgBoxIconError)
				return
			}
			app.session = session
			refreshAccount()
			setStatus("Вход выполнен: "+session.PlayerName, cGood)
		}()
	}
	logoutButton.onClick = func() {
		_ = os.Remove(AuthFile())
		app.session = nil
		refreshAccount()
		setStatus("Выход выполнен", cMuted)
	}
	installButton.onClick = func() { install(false) }
	playButton.onClick = func() { play() }
	cancelButton.onClick = func() {
		app.mutex.Lock()
		cancel := app.cancel
		app.mutex.Unlock()
		if cancel != nil {
			cancel.Cancel()
			appendLog("Отменяем…")
		}
	}
	javaDownloadButton.onClick = func() {
		runTask(func(cancel *core.Cancel) error {
			provisioner := core.JavaProvisioner{
				Paths:    app.config.Paths(),
				Cancel:   cancel,
				Progress: &taskProgress{stage: setStatus, file: setProgress, log: appendLog},
			}
			if err := provisioner.Paths.CreateDirectories(); err != nil {
				return err
			}
			java, err := provisioner.DownloadRuntime(
				core.DefaultRuntimeComponent, core.RequiredJavaMajor)
			if err != nil {
				return err
			}
			app.mutex.Lock()
			app.java = java
			app.mutex.Unlock()
			appendLog("Java готова: " + java.Executable + " (Java " + java.Version + ")")
			return nil
		}, func(err error) {
			refreshJava()
			if err != nil && err != core.ErrCancelled {
				setStatus("Не удалось скачать Java: "+err.Error(), cBad)
				walk.MsgBox(mainWindow, "Java", err.Error(), walk.MsgBoxIconError)
			} else if err == nil {
				setStatus("Java готова", cGood)
			}
		})
	}
	javaCheckButton.onClick = func() {
		setBusy(true)
		go func() {
			info := core.Detect()
			setBusy(false)
			if info.Valid() {
				appendLog("Системная Java: " + info.Executable + " (Java " + info.VersionText + ")")
				walk.MsgBox(mainWindow, "Системная Java",
					info.Executable+"\nJava "+info.VersionText, walk.MsgBoxIconInformation)
			} else {
				appendLog("Системная Java: " + info.Error)
				walk.MsgBox(mainWindow, "Системная Java", info.Error, walk.MsgBoxIconWarning)
			}
		}()
	}
	javaPickButton.onClick = func() {
		dialog := &walk.FileDialog{}
		dialog.Title = "javaw.exe или java.exe"
		dialog.Filter = "Программы (*.exe)|*.exe"
		accepted, err := dialog.ShowOpen(mainWindow)
		if err != nil || !accepted || dialog.FilePath == "" {
			return
		}
		info := core.Inspect(dialog.FilePath)
		if !info.Valid() {
			walk.MsgBox(mainWindow, "Java", info.Error, walk.MsgBoxIconError)
			return
		}
		if !info.MeetsRequirement() {
			walk.MsgBox(mainWindow, "Java", "Эта Java версии "+info.VersionText+
				", а игре нужна "+fmt.Sprint(core.RequiredJavaMajor)+" или новее",
				walk.MsgBoxIconWarning)
			return
		}
		app.config.JavaExecutable = dialog.FilePath
		javaEdit.SetText(dialog.FilePath)
		saveConfig(app.config)
		app.installation = nil
		refreshJava()
	}
	javaAutoButton.onClick = func() {
		app.config.JavaExecutable = ""
		javaEdit.SetText("")
		saveConfig(app.config)
		app.installation = nil
		refreshJava()
		setStatus("Java будет скачана автоматически при установке", cAccent2)
	}
	dirPickButton.onClick = func() {
		dialog := &walk.FileDialog{}
		dialog.Title = "Каталог игры"
		accepted, err := dialog.ShowBrowseFolder(mainWindow)
		if err != nil || !accepted || dialog.FilePath == "" {
			return
		}
		app.config.GameDirectory = dialog.FilePath
		dirEdit.SetText(dialog.FilePath)
		saveConfig(app.config)
		app.installation = nil
		refreshMods()
		refreshJava()
	}
	modsRefreshButton.onClick = func() { refreshMods() }
	logClearButton.onClick = func() { logView.SetText("") }
	logOpenButton.onClick = func() {
		command := exec.Command("explorer", app.config.GameDirectory)
		command.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
		_ = command.Start()
	}

	// Картинки, у которых есть прозрачные уголки: их тоже надо перерисовывать
	// вместе с фоном, иначе по краям останутся застывшие кусочки.
	sheens := make([]*walk.CustomWidget, 6)
	logoMarkWidget := newLogoMark(46)

	// ── составные части окна ──
	var headerRow, bodyRow, railBox, pagesBox, titleBox *walk.Composite
	var accountPage, gamePage, javaPage, settingsPage, modsPage, logPage *walk.Composite
	var gameButtons, javaButtons, javaPathRow, dirRow, memoryRow, windowRow *walk.Composite
	var logButtons *walk.Composite

	tabs := []*glassButton{tabAccount, tabGame, tabJava, tabSettings, tabMods, tabLog}
	pageList := func() []*walk.Composite {
		return []*walk.Composite{accountPage, gamePage, javaPage, settingsPage, modsPage, logPage}
	}
	var glassRefresh func()
	showPage := func(index int) {
		for position, page := range pageList() {
			if page != nil {
				page.SetVisible(position == index)
			}
		}
		for position, tab := range tabs {
			tab.SetActive(position == index)
		}
		// Стекло только что показанной страницы собирается сразу: ждать
		// следующего кадра фона незачем, иначе полсекунды виден градиент.
		if glassRefresh != nil {
			glassRefresh()
		}
	}
	for index, tab := range tabs {
		number := index
		tab.onClick = func() { showPage(number) }
	}

	if err := (MainWindow{
		AssignTo: &mainWindow,
		Title:    "AetherGlass",
		MinSize:  Size{Width: 940, Height: 620},
		// Составная отрисовка: окно и все его элементы рисуются в буфер,
		// поэтому живой фон и анимации не мерцают.
		DoubleBuffering: true,
		Layout:          VBox{Margins: Margins{Left: 16, Top: 14, Right: 16, Bottom: 14}, Spacing: 12},
		Children: []Widget{
			Composite{
				AssignTo: &headerRow,
				Layout:   HBox{MarginsZero: true, Spacing: 12},
				Children: []Widget{
					logoMarkWidget.Declarative(),
					Composite{
						AssignTo: &titleBox,
						Layout:   VBox{MarginsZero: true, SpacingZero: true},
						Children: []Widget{titleText.Declarative(), subtitleText.Declarative()},
					},
					HSpacer{},
					pill.Declarative(),
				},
			},
			Composite{
				AssignTo:      &bodyRow,
				StretchFactor: 1,
				Layout:        HBox{MarginsZero: true, Spacing: 14},
				Children: []Widget{
					Composite{
						AssignTo: &railBox,
						Layout:   VBox{MarginsZero: true, Spacing: 8},
						MinSize:  Size{Width: 176},
						MaxSize:  Size{Width: 176},
						Children: []Widget{
							tabAccount.Declarative(),
							tabGame.Declarative(),
							tabJava.Declarative(),
							tabSettings.Declarative(),
							tabMods.Declarative(),
							tabLog.Declarative(),
							VSpacer{},
						},
					},
					Composite{
						AssignTo: &pagesBox,
						Layout:   VBox{MarginsZero: true, SpacingZero: true},
						Children: []Widget{
							Composite{
								AssignTo: &accountPage,
								Layout:   VBox{Margins: Margins{Left: 20, Top: 16, Right: 20, Bottom: 18}, Spacing: 10},
								Children: []Widget{
									sheenWidget(2, &sheens[0]),
									accountTitle.Declarative(),
									playerText.Declarative(),
									accountHint.Declarative(),
									VSpacer{},
									Composite{
										Layout: HBox{MarginsZero: true, Spacing: 10},
										Children: []Widget{
											loginButton.Declarative(),
											logoutButton.Declarative(),
											HSpacer{},
										},
									},
								},
							},
							Composite{
								AssignTo: &gamePage,
								Visible:  false,
								Layout:   VBox{Margins: Margins{Left: 20, Top: 16, Right: 20, Bottom: 18}, Spacing: 10},
								Children: []Widget{
									sheenWidget(2, &sheens[1]),
									gameTitle.Declarative(),
									gameInfo.Declarative(),
									javaInfo.Declarative(),
									VSpacer{},
									Composite{
										AssignTo: &gameButtons,
										Layout:   HBox{MarginsZero: true, Spacing: 10},
										Children: []Widget{
											installButton.Declarative(),
											playButton.Declarative(),
											cancelButton.Declarative(),
											HSpacer{},
										},
									},
								},
							},
							Composite{
								AssignTo: &javaPage,
								Visible:  false,
								Layout:   VBox{Margins: Margins{Left: 20, Top: 16, Right: 20, Bottom: 18}, Spacing: 10},
								Children: []Widget{
									sheenWidget(2, &sheens[2]),
									javaTitle.Declarative(),
									javaState.Declarative(),
									javaHint.Declarative(),
									VSpacer{},
									Composite{
										AssignTo: &javaButtons,
										Layout:   HBox{MarginsZero: true, Spacing: 10},
										Children: []Widget{
											javaDownloadButton.Declarative(),
											javaCheckButton.Declarative(),
											HSpacer{},
										},
									},
									Composite{
										AssignTo: &javaPathRow,
										Layout:   HBox{MarginsZero: true, Spacing: 8},
										Children: []Widget{
											LineEdit{AssignTo: &javaEdit, Text: app.config.JavaExecutable,
												ReadOnly: true, MinSize: Size{Width: 260}},
											javaPickButton.Declarative(),
											javaAutoButton.Declarative(),
											HSpacer{},
										},
									},
								},
							},
							Composite{
								AssignTo: &settingsPage,
								Visible:  false,
								Layout:   VBox{Margins: Margins{Left: 20, Top: 16, Right: 20, Bottom: 18}, Spacing: 8},
								Children: []Widget{
									sheenWidget(2, &sheens[3]),
									settingsTitle.Declarative(),
									dirLabel.Declarative(),
									Composite{
										AssignTo: &dirRow,
										Layout:   HBox{MarginsZero: true, Spacing: 8},
										Children: []Widget{
											LineEdit{AssignTo: &dirEdit, Text: app.config.GameDirectory,
												ReadOnly: true, MinSize: Size{Width: 320}},
											dirPickButton.Declarative(),
											HSpacer{},
										},
									},
									memoryLabel.Declarative(),
									Composite{
										AssignTo: &memoryRow,
										Layout:   HBox{MarginsZero: true, Spacing: 10},
										Children: []Widget{
											minLabel.Declarative(),
											NumberEdit{AssignTo: &minMemory,
												Value:    float64(app.config.MinMemoryMb),
												MinValue: 256, MaxValue: 65536, Decimals: 0,
												MinSize: Size{Width: 100},
												OnValueChanged: func() {
													app.config.MinMemoryMb = int(minMemory.Value())
													saveConfig(app.config)
												}},
											maxLabel.Declarative(),
											NumberEdit{AssignTo: &maxMemory,
												Value:    float64(app.config.MaxMemoryMb),
												MinValue: 256, MaxValue: 65536, Decimals: 0,
												MinSize: Size{Width: 100},
												OnValueChanged: func() {
													app.config.MaxMemoryMb = int(maxMemory.Value())
													saveConfig(app.config)
												}},
											HSpacer{},
										},
									},
									windowLabel.Declarative(),
									Composite{
										AssignTo: &windowRow,
										Layout:   HBox{MarginsZero: true, Spacing: 10},
										Children: []Widget{
											widthLabel.Declarative(),
											NumberEdit{AssignTo: &widthEdit,
												Value:    float64(app.config.WindowWidth),
												MinValue: 0, MaxValue: 7680, Decimals: 0,
												MinSize: Size{Width: 100},
												OnValueChanged: func() {
													app.config.WindowWidth = int(widthEdit.Value())
													saveConfig(app.config)
												}},
											heightLabel.Declarative(),
											NumberEdit{AssignTo: &heightEdit,
												Value:    float64(app.config.WindowHeight),
												MinValue: 0, MaxValue: 4320, Decimals: 0,
												MinSize: Size{Width: 100},
												OnValueChanged: func() {
													app.config.WindowHeight = int(heightEdit.Value())
													saveConfig(app.config)
												}},
											HSpacer{},
										},
									},
									VSpacer{},
									modsSwitch.Declarative(),
								},
							},
							Composite{
								AssignTo: &modsPage,
								Visible:  false,
								Layout:   VBox{Margins: Margins{Left: 20, Top: 16, Right: 20, Bottom: 18}, Spacing: 8},
								Children: []Widget{
									sheenWidget(2, &sheens[4]),
									modsTitle.Declarative(),
									modsHint.Declarative(),
									modsList.Declarative(),
									VSpacer{},
									modsRefreshButton.Declarative(),
								},
							},
							Composite{
								AssignTo: &logPage,
								Visible:  false,
								Layout:   VBox{Margins: Margins{Left: 20, Top: 16, Right: 20, Bottom: 18}, Spacing: 8},
								Children: []Widget{
									sheenWidget(2, &sheens[5]),
									logTitle.Declarative(),
									TextEdit{AssignTo: &logView, ReadOnly: true, VScroll: true, HScroll: true},
									Composite{
										AssignTo: &logButtons,
										Layout:   HBox{MarginsZero: true, Spacing: 10},
										Children: []Widget{
											logClearButton.Declarative(),
											logOpenButton.Declarative(),
											HSpacer{},
										},
									},
								},
							},
						},
					},
				},
			},
			progress.Declarative(),
			footerText.Declarative(),
		},
	}).Create(); err != nil {
		return err
	}

	// ── настройка после создания: прозрачность, карточки, поля ──
	for _, item := range []creatable{
		titleText, subtitleText, footerText, accountTitle, playerText, accountHint,
		gameTitle, gameInfo, javaInfo, javaTitle, javaState, javaHint,
		settingsTitle, dirLabel, memoryLabel, windowLabel, minLabel, maxLabel,
		widthLabel, heightLabel, modsTitle, modsHint, logTitle,
		progress, pill, modsList, modsSwitch,
		loginButton, logoutButton, installButton, playButton, cancelButton,
		javaDownloadButton, javaCheckButton, javaPickButton, javaAutoButton,
		dirPickButton, modsRefreshButton, logClearButton, logOpenButton,
		tabAccount, tabGame, tabJava, tabSettings, tabMods, tabLog,
	} {
		item.afterCreate()
	}

	// Логотип и светлые кромки карточек тоже прозрачные: иначе их углы
	// закрасились бы системным цветом поверх живого фона.
	for _, widget := range append(sheens, logoMarkWidget.widget) {
		if widget != nil {
			widget.SetBackground(walk.NullBrush())
		}
	}
	clearBackground(headerRow, bodyRow, railBox, pagesBox, titleBox,
		gameButtons, javaButtons, javaPathRow, dirRow, memoryRow, windowRow, logButtons)

	// Стеклянные подложки: карточки, боковое меню, логотип, кнопки.
	// Каждая показывает сквозь себя размытый фон — это и есть Liquid Glass.
	panes := make([]*glassPane, 0, 24)
	for _, card := range []*walk.Composite{accountPage, gamePage, javaPage,
		settingsPage, modsPage, logPage} {
		if pane := decorateCard(card, 18); pane != nil {
			panes = append(panes, pane)
		}
	}
	if pane := decorateCard(railBox, 18); pane != nil {
		pane.options = railGlassOptions(rgbOf(cGlassTint))
		pane.options.Radius = 18
		panes = append(panes, pane)
	}
	logoMarkWidget.pane = newGlassPaneFor(logoMarkWidget.widget, logoMarkWidget.glassOptions())
	panes = append(panes, logoMarkWidget.pane)
	styleLineEdit(dirEdit)
	styleLineEdit(javaEdit)
	styleNumberEdit(minMemory)
	styleNumberEdit(maxMemory)
	styleNumberEdit(widthEdit)
	styleNumberEdit(heightEdit)
	styleTextEdit(logView)

	// Поля ввода тоже из стекла: та же подложка, но плотнее и темнее, чтобы
	// поле читалось вдавленным. Лог оставляем однотонным — он много
	// прокручивается, и там важнее стабильность, чем эффект.
	for _, control := range []backgroundTarget{dirEdit, javaEdit,
		minMemory, maxMemory, widthEdit, heightEdit} {
		if pane := newGlassControl(control, fieldGlassOptions(rgbOf(cField))); pane != nil {
			panes = append(panes, pane)
		}
	}

	// Стеклянное окно: скруглённые углы и акрил на Windows 11.
	// Пока живой фон не отрисован, окно живёт градиентом — без белой вспышки.
	mainWindow.SetBackground(verticalBrush(cBackTop, cBackBot))
	glassWindow(mainWindow.Handle())
	clipChildren(mainWindow.Handle())
	_ = mainWindow.SetSize(walk.Size{Width: 980, Height: 640})

	// Живой фон: медленно плывущие пятна за стеклом.
	animator := newAnimator(mainWindow)
	animator.addWidgets(append(sheens, logoMarkWidget.widget)...)
	buttons := []*glassButton{
		loginButton, logoutButton, installButton, playButton, cancelButton,
		javaDownloadButton, javaCheckButton, javaPickButton, javaAutoButton,
		dirPickButton, modsRefreshButton, logClearButton, logOpenButton,
		tabAccount, tabGame, tabJava, tabSettings, tabMods, tabLog,
	}
	for _, button := range buttons {
		if button == nil || button.widget == nil {
			continue
		}
		button.pane = newGlassPaneFor(button.widget, button.glassOptions())
		panes = append(panes, button.pane)
	}
	pill.pane = newGlassPaneFor(pill.widget, pill.glassOptions())
	progress.pane = newGlassPaneFor(progress.widget, progress.glassOptions())
	modsSwitch.pane = newGlassPaneFor(modsSwitch.widget, modsSwitch.glassOptions())
	panes = append(panes, pill.pane, progress.pane, modsSwitch.pane)
	animator.addPanes(panes...)
	animator.add(progress, pill, modsSwitch,
		loginButton, logoutButton, installButton, playButton, cancelButton,
		javaDownloadButton, javaCheckButton, javaPickButton, javaAutoButton,
		dirPickButton, modsRefreshButton, logClearButton, logOpenButton,
		tabAccount, tabGame, tabJava, tabSettings, tabMods, tabLog)
	glassRefresh = animator.refreshPanes
	animator.start()

	refreshAccount()
	refreshJava()
	refreshMods()
	showPage(1)
	cancelButton.SetEnabled(false)
	logoutButton.SetEnabled(false)
	appendLog("AetherGlass Launcher запущен")
	appendLog("Каталог игры: " + app.config.GameDirectory)
	appendLog("Java: для самого лаунчера не нужна; для игры ставится официальный рантайм Mojang")

	mainWindow.Run()

	animator.stopLoop()
	animator.dispose()
	disposeTheme()
	app.launcher.Stop()
	return nil
}

// clearBackground — контейнер без своей заливки: сквозь него видно фон окна.
func clearBackground(widgets ...*walk.Composite) {
	for _, widget := range widgets {
		if widget != nil {
			widget.SetBackground(walk.NullBrush())
			clipChildren(widget.Handle())
		}
	}
}

func styleLineEdit(edit *walk.LineEdit) {
	if edit == nil {
		return
	}
	edit.SetBackground(solidBrush(cField))
	edit.SetTextColor(cText)
}

func styleNumberEdit(edit *walk.NumberEdit) {
	if edit == nil {
		return
	}
	edit.SetBackground(solidBrush(cField))
	edit.SetTextColor(cText)
}

func styleTextEdit(edit *walk.TextEdit) {
	if edit == nil {
		return
	}
	edit.SetBackground(solidBrush(cField))
	edit.SetTextColor(cText)
}

// play объявлена как переменная, потому что «Играть» вызывает установку,
// а установка по завершении — «Играть».
var play func()

func saveConfig(config *core.Config) {
	_ = config.Save(ConfigFile())
}

func startGame(app *controller, appendLog func(string)) error {
	app.mutex.Lock()
	session := app.session
	installation := app.installation
	app.mutex.Unlock()

	if session == nil || !session.Valid() {
		if session != nil && session.RefreshToken != "" {
			refreshed, err := core.Refresh(session)
			if err == nil {
				_ = refreshed.Save(AuthFile())
				app.mutex.Lock()
				app.session = refreshed
				session = refreshed
				app.mutex.Unlock()
				appendLog("Сессия обновлена")
			} else {
				appendLog("Сессию обновить не удалось: " + err.Error())
			}
		}
	}
	if session == nil || !session.Valid() {
		return &core.Errorf{Message: "сначала войдите в аккаунт Microsoft (вкладка «Аккаунт»)"}
	}
	plan, err := app.launcher.Plan(installation, app.config.Paths(), app.config, session)
	if err != nil {
		return err
	}
	appendLog("Запуск: " + plan.Executable)
	if err := app.launcher.Launch(plan, func(line string) { appendLog(line) }, func() {
		appendLog("Игра завершена")
	}); err != nil {
		return err
	}
	return nil
}

func login(app *controller, appendLog func(string), setStatus func(string, walk.Color)) (*core.Session, error) {
	appendLog("Открываем страницу входа Microsoft…")
	session, err := core.Login(app.config.ClientID, func(rawURL string) {
		appendLog("Если браузер не открылся, перейдите по ссылке:")
		appendLog(rawURL)
		openBrowser(rawURL)
		setStatus("Ждём подтверждения входа в браузере…", cAccent2)
	}, nil)
	if err != nil {
		return nil, err
	}
	if err := session.Save(AuthFile()); err != nil {
		appendLog("Сессию сохранить не удалось: " + err.Error())
	}
	return session, nil
}

func openBrowser(rawURL string) {
	command := exec.Command("rundll32", "url.dll,FileProtocolHandler", rawURL)
	command.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	_ = command.Start()
}

// taskProgress — ход работы: этап — в «таблетку» статуса, файлы — в полосу,
// и всё это потокобезопасно: Synchronize вызывается на стороне окна.
type taskProgress struct {
	stage func(string, walk.Color)
	file  func(float64)
	log   func(string)
}

func (p *taskProgress) Stage(text string) {
	if p.log != nil {
		p.log("• " + text)
	}
	if p.stage != nil {
		p.stage(text, cAccent2)
	}
}

func (p *taskProgress) File(name string, done int64, total int64) {
	if p.file == nil || total <= 0 {
		return
	}
	percent := float64(done) * 100 / float64(total)
	if percent < 0 {
		percent = 0
	}
	if percent > 100 {
		percent = 100
	}
	p.file(percent)
}
