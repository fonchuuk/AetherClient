package core

import (
	"crypto/md5"
	"encoding/hex"
	"strings"
)

// Офлайн-профиль: игра без входа в Microsoft.
//
// Это не обход защиты и не подмена чужой учётной записи. Офлайн-профиль
// просто не получает токена Minecraft, поэтому:
//
//   - одиночная игра и игра по локальной сети работают полностью;
//   - серверы с включённой проверкой лицензии (online-mode=true — в том числе
//     Realms и все обычные публичные серверы) такого игрока не пустят:
//     подтвердить сессию нечем, и игра честно показывает отказ;
//   - скинов и плащей нет: их раздаёт Mojang по токену.
//
// Лаунчер не рассылает и не подделывает токены — он их не передаёт вовсе.
// Игра при этом скачивается с официальных серверов Mojang обычным порядком.
const (
	// Длина имени — правило Minecraft: от 3 до 16 символов.
	minOfflineNameLength = 3
	maxOfflineNameLength = 16
	// Префикс, с которым игра и ванильный сервер считают офлайн-UUID.
	offlinePrefix = "OfflinePlayer:"
	// Значения для командной строки игры. Ноль, а не пустая строка: пустой
	// элемент в командной строке был бы разобран как лишний аргумент.
	offlineAccessToken = "0"
	// Тип учётной записи «legacy»: с ним игра не пытается обновить токен.
	offlineUserType = "legacy"
)

// OfflineUUID — UUID офлайн-игрока.
//
// Считается ровно так же, как в самой игре и на ванильном сервере:
// UUID.nameUUIDFromBytes(("OfflinePlayer:" + имя).getBytes(UTF-8)), то есть
// MD5 от строки с выставленными битами версии 3 и варианта RFC 4122.
// Благодаря этому мир, созданный офлайн, не «меняет владельца», если позже
// войти в этот же аккаунт по-настоящему.
func OfflineUUID(name string) string {
	sum := md5.Sum([]byte(offlinePrefix + name))
	sum[6] = (sum[6] & 0x0f) | 0x30 // версия 3
	sum[8] = (sum[8] & 0x3f) | 0x80 // вариант RFC 4122
	text := hex.EncodeToString(sum[:])
	return text[0:8] + "-" + text[8:12] + "-" + text[12:16] + "-" +
		text[16:20] + "-" + text[20:32]
}

// ValidOfflineName — годится ли имя для офлайн-профиля: от 3 до 16 символов,
// только латиница, цифры и подчёркивание (ограничение Minecraft).
func ValidOfflineName(name string) bool {
	name = strings.TrimSpace(name)
	if len(name) < minOfflineNameLength || len(name) > maxOfflineNameLength {
		return false
	}
	for _, letter := range name {
		allowed := letter >= 'a' && letter <= 'z' ||
			letter >= 'A' && letter <= 'Z' ||
			letter >= '0' && letter <= '9' ||
			letter == '_'
		if !allowed {
			return false
		}
	}
	return true
}

// OfflineNameError — понятная причина, по которой имя не подошло.
func OfflineNameError(name string) string {
	name = strings.TrimSpace(name)
	switch {
	case name == "":
		return "введите имя игрока"
	case len(name) < minOfflineNameLength:
		return "имя слишком короткое: нужно хотя бы 3 символа"
	case len(name) > maxOfflineNameLength:
		return "имя слишком длинное: можно не больше 16 символов"
	default:
		return "в имени можно использовать только латиницу, цифры и подчёркивание"
	}
}

// NewOfflineSession собирает офлайн-сессию: имя, UUID и всё. Ни токена,
// ни срока действия у неё нет — именно поэтому она и офлайн.
func NewOfflineSession(name string) (*Session, error) {
	name = strings.TrimSpace(name)
	if !ValidOfflineName(name) {
		return nil, &Errorf{Message: OfflineNameError(name)}
	}
	return &Session{
		PlayerName: name,
		UUID:       OfflineUUID(name),
		Offline:    true,
	}, nil
}
