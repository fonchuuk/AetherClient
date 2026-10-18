package core

import "testing"

// Сравнение версий — то место, где легко ошибиться: строковое сравнение
// считало бы v0.10.0 старше v0.9.0, потому что "1" < "9". Проверяем число.
func TestNewerThan(t *testing.T) {
	cases := []struct {
		candidate string
		current   string
		want      bool
	}{
		{"v0.1.2", "v0.1.1", true},   // обычное обновление
		{"v0.1.1", "v0.1.1", false},  // та же версия — обновления нет
		{"v0.1.0", "v0.1.1", false},  // старее — обновления нет
		{"v0.10.0", "v0.9.0", true},  // двузначная: строки бы ошиблись
		{"0.2.0", "v0.1.9", true},    // метка без «v»
		{"v1.0.0", "v0.99.99", true}, // старший разряд важнее
		{"v0.2", "v0.1.5", true},     // неполная метка
		{"", "v0.1.0", false},        // пустая метка обновлением не считается
	}
	for _, item := range cases {
		if got := newerThan(item.candidate, item.current); got != item.want {
			t.Errorf("newerThan(%q, %q) = %v, нужно %v",
				item.candidate, item.current, got, item.want)
		}
	}
}
