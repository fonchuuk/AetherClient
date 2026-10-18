package core

import (
	"regexp"
	"runtime"
)

// OSName — имя текущей ОС в форме, которую использует Mojang: windows, linux, osx.
func OSName() string {
	switch runtime.GOOS {
	case "windows":
		return "windows"
	case "darwin":
		return "osx"
	default:
		return "linux"
	}
}

// OSArch — архитектура в форме Mojang: x86_64, x86, arm64, arm32.
func OSArch() string {
	switch runtime.GOARCH {
	case "amd64":
		return "x86_64"
	case "386":
		return "x86"
	case "arm64":
		return "arm64"
	case "arm":
		return "arm32"
	default:
		return runtime.GOARCH
	}
}

// OSVersion — версия ОС, как её видит Java/Mojang.
func OSVersion() string {
	version, err := osVersion()
	if err != nil {
		return ""
	}
	return version
}

// Allows проверяет правило Mojang: список rules из библиотеки или аргумента.
// Если правил нет — разрешено. Действует последнее подходящее правило.
func Allows(entry map[string]any, features map[string]bool) bool {
	rules, ok := entry["rules"].([]any)
	if !ok {
		return true
	}
	allowed := false
	for _, item := range rules {
		rule, ok := item.(map[string]any)
		if !ok {
			continue
		}
		if !matches(rule, features) {
			continue
		}
		action, _ := rule["action"].(string)
		allowed = action == "allow"
	}
	return allowed
}

func matches(rule map[string]any, features map[string]bool) bool {
	if os, ok := rule["os"].(map[string]any); ok {
		if name, ok := os["name"].(string); ok && name != OSName() {
			return false
		}
		if arch, ok := os["arch"].(string); ok && arch != OSArch() {
			return false
		}
		if version, ok := os["version"].(string); ok {
			matched, err := regexp.MatchString(version, OSVersion())
			if err != nil || !matched {
				return false
			}
		}
	}
	if ruleFeatures, ok := rule["features"].(map[string]any); ok {
		for key, value := range ruleFeatures {
			expected, ok := value.(bool)
			if !ok {
				continue
			}
			actual, known := features[key]
			if !known || actual != expected {
				return false
			}
		}
	}
	return true
}

// Features — значения признаков, которые подставляет лаунчер: как в официальном
// запуске без демо-режима и без принудительного разрешения.
func Features(customResolution bool) map[string]bool {
	return map[string]bool{
		"is_demo_user":          false,
		"has_custom_resolution": customResolution,
	}
}
