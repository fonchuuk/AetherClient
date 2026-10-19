package core

import (
	"bufio"
	"io"
	"os/exec"
	"strings"
	"sync"
)

// Имя и версия лаунчера — их игра показывает в служебных аргументах.
const (
	LauncherName    = "AetherGlassLauncher"
	LauncherVersion = "1.0.0"
)

// LaunchPlan — готовая команда запуска игры.
type LaunchPlan struct {
	Executable       string
	Command          []string
	WorkingDirectory string
}

// Launcher запускает и следит за процессом игры. Одновременно может быть
// запущен только один экземпляр: повторный запуск отклоняется, пока живой
// процесс не завершится.
type Launcher struct {
	mutex   sync.Mutex
	process *exec.Cmd
	output  io.ReadCloser
}

// IsRunning — запущена ли игра в данный момент.
func (l *Launcher) IsRunning() bool {
	l.mutex.Lock()
	defer l.mutex.Unlock()
	return l.process != nil && l.process.Process != nil && l.process.ProcessState == nil
}

// Stop завершает процесс игры, если он ещё жив.
func (l *Launcher) Stop() {
	l.mutex.Lock()
	defer l.mutex.Unlock()
	if l.process != nil && l.process.Process != nil && l.process.ProcessState == nil {
		_ = l.process.Process.Kill()
	}
}

// Plan собирает командную строку: аргументы берутся из официального JSON версии
// Mojang и из профиля Fabric, подставляются только значения токенов — ровно как
// это делает официальный лаунчер.
func (l *Launcher) Plan(installation *Installation, paths Paths, config *Config,
	session *Session) (*LaunchPlan, error) {
	if session == nil || !session.Valid() {
		return nil, &Errorf{Message: "нет действительной учётной записи: войдите через Microsoft"}
	}
	if installation == nil {
		return nil, &Errorf{Message: "игра ещё не установлена: нажмите «Установить»"}
	}
	javaExecutable := installation.JavaExecutable
	if config.JavaExecutable != "" {
		if info := Inspect(config.JavaExecutable); info.Valid() {
			javaExecutable = config.JavaExecutable
		} else {
			return nil, &Errorf{Message: "указанная в настройках Java не работает: " + info.Error}
		}
	}
	if javaExecutable == "" {
		return nil, &Errorf{Message: "не выбрана Java для запуска"}
	}

	customResolution := config.WindowWidth > 0 && config.WindowHeight > 0
	features := Features(customResolution)

	// Офлайн-профиль приходит без токена и без идентификатора Xbox, поэтому
	// вместо них подставляются значения, которые игра понимает как «входа не
	// было». Пустоту подставлять нельзя: пустой элемент в командной строке
	// угодил бы в разбор как лишний аргумент.
	userType := "msa"
	accessToken := session.AccessToken
	clientID := session.ClientID
	xuid := session.XUID
	if session.Offline {
		userType = offlineUserType
		accessToken = offlineAccessToken
		clientID = session.UUID
		xuid = offlineAccessToken
	}

	tokens := map[string]string{
		"launcher_name":       LauncherName,
		"launcher_version":    LauncherVersion,
		"classpath":           strings.Join(installation.Classpath, string(classpathSeparator())),
		"classpath_separator": string(classpathSeparator()),
		"library_directory":   paths.Libraries(),
		"natives_directory":   installation.NativesDir,
		"version_name":        installation.FabricID,
		"game_directory":      paths.Base,
		"assets_root":         paths.Assets(),
		"assets_index_name":   installation.AssetIndexID,
		"auth_player_name":    session.PlayerName,
		"auth_uuid":           session.UUID,
		"auth_access_token":   accessToken,
		"user_type":           userType,
		"auth_xuid":           xuid,
		"clientid":            clientID,
		"version_type":        LauncherName,
		"resolution_width":    itoa(int64(config.WindowWidth)),
		"resolution_height":   itoa(int64(config.WindowHeight)),
	}

	command := []string{javaExecutable,
		"-Xms" + itoa(int64(config.MinMemoryMb)) + "M",
		"-Xmx" + itoa(int64(config.MaxMemoryMb)) + "M",
		"-Dfile.encoding=UTF-8"}
	if installation.LogConfigFile != "" && installation.LogConfigArgument != "" {
		command = append(command,
			Substitute(installation.LogConfigArgument, map[string]string{"path": installation.LogConfigFile}))
	}
	command = appendArguments(command, installation.JVMArguments, tokens, features)
	command = append(command, installation.MainClass)
	command = appendArguments(command, installation.GameArguments, tokens, features)
	return &LaunchPlan{Executable: javaExecutable, Command: command, WorkingDirectory: paths.Base}, nil
}

// Launch запускает игру и по строкам отдаёт её вывод.
func (l *Launcher) Launch(plan *LaunchPlan, output func(string), onExit func()) error {
	if l.IsRunning() {
		return &Errorf{Message: "игра уже запущена — дождитесь её завершения"}
	}
	command := exec.Command(plan.Command[0], plan.Command[1:]...)
	command.Dir = plan.WorkingDirectory
	command.SysProcAttr = hiddenWindowAttr()
	stream, err := command.StdoutPipe()
	if err != nil {
		return err
	}
	command.Stderr = command.Stdout
	if err := command.Start(); err != nil {
		return err
	}
	l.mutex.Lock()
	l.process = command
	l.output = stream
	l.mutex.Unlock()

	go func() {
		reader := bufio.NewReader(stream)
		for {
			line, err := reader.ReadString('\n')
			if line != "" && output != nil {
				output(strings.TrimRight(line, "\r\n"))
			}
			if err != nil {
				break
			}
		}
		_ = command.Wait()
		l.mutex.Lock()
		if l.process == command {
			l.process = nil
			l.output = nil
		}
		l.mutex.Unlock()
		if onExit != nil {
			onExit()
		}
	}()
	return nil
}

func appendArguments(target []string, arguments []any, tokens map[string]string,
	features map[string]bool) []string {
	for _, entry := range arguments {
		switch value := entry.(type) {
		case string:
			target = append(target, Substitute(value, tokens))
		case map[string]any:
			if !Allows(value, features) {
				continue
			}
			switch inner := value["value"].(type) {
			case string:
				target = append(target, Substitute(inner, tokens))
			case []any:
				for _, item := range inner {
					target = append(target, Substitute(unstring(item), tokens))
				}
			}
		}
	}
	return target
}

// Substitute подставляет значения токенов ${…} в аргумент.
func Substitute(text string, tokens map[string]string) string {
	result := text
	for key, value := range tokens {
		placeholder := "${" + key + "}"
		if strings.Contains(result, placeholder) {
			result = strings.ReplaceAll(result, placeholder, value)
		}
	}
	return result
}

func unstring(value any) string {
	text, ok := value.(string)
	if !ok {
		return ""
	}
	return text
}
