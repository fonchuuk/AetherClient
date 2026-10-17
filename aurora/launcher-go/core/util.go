package core

import (
	"encoding/json"
	"strconv"
)

// Мелкие помощники для работы с JSON, который приходит от Mojang и Fabric:
// структура документа заранее неизвестна, поэтому каждый доступ проверяется.

func asObject(value any) map[string]any {
	object, ok := value.(map[string]any)
	if !ok {
		return map[string]any{}
	}
	return object
}

func asArray(value any) []any {
	array, ok := value.([]any)
	if !ok {
		return nil
	}
	return array
}

func asString(value any, fallback string) string {
	text, ok := value.(string)
	if !ok || text == "" {
		return fallback
	}
	return text
}

func asInt64(value any, fallback int64) int64 {
	switch number := value.(type) {
	case float64:
		return int64(number)
	case float32:
		return int64(number)
	case int:
		return int64(number)
	case int64:
		return number
	case json.Number:
		parsed, err := number.Int64()
		if err != nil {
			return fallback
		}
		return parsed
	default:
		return fallback
	}
}

func itoa(value int64) string { return strconv.FormatInt(value, 10) }
