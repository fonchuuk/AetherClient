//go:build windows

package ui

import (
	"image"
	"image/color"
	"math"
	"sync"
	"unsafe"

	"github.com/lxn/walk"
	"github.com/lxn/win"
	"golang.org/x/sys/windows"
)

// Палитра «жидкое стекло»: глубокий тёмный фон, на нём — сияющие пятна,
// сверху — полупрозрачные (визуально) стеклянные панели с мягкими краями.
// Палитра подобрана по референсу: глубокий синий фон (#04081c…#111c46),
// стекло светлее фона (#2c3a7c), кромки почти белые, свечение — синее,
// фиолетовое и бирюзовое. Оттенки ярких пикселей референса: 210–270°,
// то есть синий с уходом в фиолет, тепла нет.
var (
	cBackTop = walk.RGB(19, 29, 76) // верх фона
	cBackBot = walk.RGB(5, 9, 30)   // низ фона

	cBlobBlue   = walk.RGB(65, 99, 188)  // #4163bc — основное синее пятно
	cBlobViolet = walk.RGB(120, 82, 157) // #78529d — фиолетовое свечение
	cBlobTeal   = walk.RGB(47, 191, 216) // бирюзовое свечение
	cBlobLight  = walk.RGB(127, 137, 229)

	cDeep      = walk.RGB(6, 10, 32)   // самый низ фона
	cGlassTop  = walk.RGB(58, 74, 146) // стеклянная карточка: сверху светлее
	cGlassTint = walk.RGB(54, 70, 142) // оттенок самого стекла
	cGlassMid  = walk.RGB(44, 58, 124)
	cGlassBot  = walk.RGB(30, 40, 92)
	cField     = walk.RGB(17, 26, 64)    // поля ввода и лог: чуть темнее карточки
	cEdge      = walk.RGB(150, 172, 255) // светлая кромка стекла
	cEdgeSoft  = walk.RGB(96, 118, 200)
	cText      = walk.RGB(238, 243, 255)
	cMuted     = walk.RGB(163, 178, 224)
	cAccent    = walk.RGB(124, 96, 255)
	cAccent2   = walk.RGB(54, 224, 255)
	cAccent3   = walk.RGB(255, 112, 216)
	cGood      = walk.RGB(74, 232, 164)
	cWarn      = walk.RGB(255, 199, 102)
	cBad       = walk.RGB(255, 108, 124)
)

var (
	fontTitle  *walk.Font
	fontHead   *walk.Font
	fontBody   *walk.Font
	fontSmall  *walk.Font
	fontButton *walk.Font

	// Пятна для подсветки при наведении: рисуется поверх стекла. Сам фон
	// теперь считается в памяти (см. glassmath.go), картинки для него не нужны.
	blobWhite *walk.Bitmap
)

// initTheme готовит шрифты и «пятна» для фона — один раз на запуск.
func initTheme() {
	fontTitle = uiFont("Segoe UI", 22, walk.FontBold)
	fontHead = uiFont("Segoe UI", 11, walk.FontBold)
	fontBody = uiFont("Segoe UI", 9, 0)
	fontSmall = uiFont("Segoe UI", 8, 0)
	fontButton = uiFont("Segoe UI", 10, walk.FontBold)

	blobWhite = blob(360, 255, 255, 255)
}

// uiFont берёт шрифт, а если такого семейства нет — откатывается на системный.
func uiFont(family string, size int, style walk.FontStyle) *walk.Font {
	for _, candidate := range []string{family, "Segoe UI", "Tahoma"} {
		if font, err := walk.NewFont(candidate, size, style); err == nil {
			return font
		}
	}
	return nil
}

// blob — размытое цветное пятно: им рисуется живой фон. Цвета записаны
// уже «примноженными» на прозрачность, как того требует GDI (AlphaBlend).
func blob(size int, r, g, b uint8) *walk.Bitmap {
	img := image.NewRGBA(image.Rect(0, 0, size, size))
	center := float64(size) / 2
	for y := 0; y < size; y++ {
		for x := 0; x < size; x++ {
			dx := (float64(x) - center + 0.5) / center
			dy := (float64(y) - center + 0.5) / center
			distance := math.Sqrt(dx*dx + dy*dy)
			if distance >= 1 {
				continue
			}
			falloff := math.Pow(math.Cos(distance*math.Pi/2), 2.4)
			alpha := uint16(falloff * 255)
			img.SetRGBA(x, y, color.RGBA{
				R: byte(uint16(r) * alpha / 255),
				G: byte(uint16(g) * alpha / 255),
				B: byte(uint16(b) * alpha / 255),
				A: byte(alpha),
			})
		}
	}
	bitmap, err := walk.NewBitmapFromImageForDPI(img, 96)
	if err != nil {
		return nil
	}
	return bitmap
}

// ─────────────────────────────── кисти и перья ───────────────────────────────

var (
	brushMutex sync.Mutex
	brushCache = map[[2]walk.Color]walk.Brush{}
	penCache   = map[walk.Color]walk.Pen{}
)

// verticalBrush — вертикальный градиент. Кисти кэшируются: создавать их на
// каждую перерисовку значит засорять таблицу дескрипторов GDI.
func verticalBrush(top walk.Color, bottom walk.Color) walk.Brush {
	brushMutex.Lock()
	defer brushMutex.Unlock()
	key := [2]walk.Color{top, bottom}
	if brush, ok := brushCache[key]; ok {
		return brush
	}
	brush, err := walk.NewVerticalGradientBrush([]walk.GradientStop{
		{Offset: 0, Color: top},
		{Offset: 1, Color: bottom},
	})
	if err != nil {
		return nil
	}
	brushCache[key] = brush
	return brush
}

// solidBrush — однотонная кисть (тот же кэш).
func solidBrush(color walk.Color) walk.Brush {
	return verticalBrush(color, color)
}

// colorPen — тонкое перо нужного цвета, тоже из кэша.
func colorPen(color walk.Color) walk.Pen {
	brushMutex.Lock()
	defer brushMutex.Unlock()
	if pen, ok := penCache[color]; ok {
		return pen
	}
	pen, err := walk.NewCosmeticPen(walk.PenSolid, color)
	if err != nil {
		return nil
	}
	penCache[color] = pen
	return pen
}

// disposeTheme освобождает кисти, перья и пятна.
func disposeTheme() {
	brushMutex.Lock()
	for _, brush := range brushCache {
		brush.Dispose()
	}
	brushCache = map[[2]walk.Color]walk.Brush{}
	for _, pen := range penCache {
		pen.Dispose()
	}
	penCache = map[walk.Color]walk.Pen{}
	brushMutex.Unlock()
	for _, bitmap := range []*walk.Bitmap{blobWhite} {
		if bitmap != nil {
			bitmap.Dispose()
		}
	}
}

// ─────────────────────────────── рисование ───────────────────────────────

// fillRound — заливка скруглённого прямоугольника.
func fillRound(canvas *walk.Canvas, brush walk.Brush, bounds walk.Rectangle, radius int) {
	if brush == nil || bounds.Width <= 0 || bounds.Height <= 0 {
		return
	}
	if radius > bounds.Height/2 {
		radius = bounds.Height / 2
	}
	if radius > bounds.Width/2 {
		radius = bounds.Width / 2
	}
	_ = canvas.FillRoundedRectangle(brush, bounds, walk.Size{Width: radius * 2, Height: radius * 2})
}

// strokeRound — обводка скруглённого прямоугольника.
func strokeRound(canvas *walk.Canvas, color walk.Color, bounds walk.Rectangle, radius int) {
	pen := colorPen(color)
	if pen == nil || bounds.Width <= 0 || bounds.Height <= 0 {
		return
	}
	if radius > bounds.Height/2 {
		radius = bounds.Height / 2
	}
	if radius > bounds.Width/2 {
		radius = bounds.Width / 2
	}
	_ = canvas.DrawRoundedRectangle(pen, bounds, walk.Size{Width: radius * 2, Height: radius * 2})
}

// drawText печатает текст; без шрифта или текста — просто ничего не делает.
func drawText(canvas *walk.Canvas, text string, font *walk.Font, color walk.Color,
	bounds walk.Rectangle, format walk.DrawTextFormat) {
	if font == nil || text == "" || bounds.Width <= 0 || bounds.Height <= 0 {
		return
	}
	_ = canvas.DrawText(text, font, color, bounds, format|walk.TextNoPrefix)
}

// mix — смешивает два цвета: t=0 даёт первый, t=1 — второй.
func mix(from walk.Color, to walk.Color, t float64) walk.Color {
	if t < 0 {
		t = 0
	}
	if t > 1 {
		t = 1
	}
	r := float64(from.R()) + (float64(to.R())-float64(from.R()))*t
	g := float64(from.G()) + (float64(to.G())-float64(from.G()))*t
	b := float64(from.B()) + (float64(to.B())-float64(from.B()))*t
	return walk.RGB(byte(r), byte(g), byte(b))
}

// clamp — число в заданных пределах.
func clamp(value float64, low float64, high float64) float64 {
	if value < low {
		return low
	}
	if value > high {
		return high
	}
	return value
}

// ─────────────────────── системные вызовы Windows ───────────────────────

var (
	libDwm     = windows.NewLazySystemDLL("dwmapi.dll")
	procDwmSet = libDwm.NewProc("DwmSetWindowAttribute")
	libGdi     = windows.NewLazySystemDLL("gdi32.dll")
	procRound  = libGdi.NewProc("CreateRoundRectRgn")
	procDelete = libGdi.NewProc("DeleteObject")
	libUser    = windows.NewLazySystemDLL("user32.dll")
	procRgn    = libUser.NewProc("SetWindowRgn")
	procIsWin  = libUser.NewProc("IsWindow")
)

const (
	dwmCornerPreference = 33 // DWMWA_WINDOW_CORNER_PREFERENCE
	dwmCornerRound      = 2  // DWMWCP_ROUND
	dwmBackdropType     = 38 // DWMWA_SYSTEMBACKDROP_TYPE
	dwmBackdropAcrylic  = 3  // DWMSBT_TRANSIENTWINDOW
)

// glassWindow — закруглённые углы и акриловая подложка окна (Windows 11).
// На Windows 10 эти атрибуты просто неизвестны системе: вызов вернёт ошибку,
// и окно останется обычным — ничего не ломается.
func glassWindow(handle win.HWND) {
	window := windows.HWND(handle)
	corner := uint32(dwmCornerRound)
	_, _, _ = procDwmSet.Call(uintptr(window), uintptr(dwmCornerPreference),
		uintptr(unsafe.Pointer(&corner)), unsafe.Sizeof(corner))
	backdrop := uint32(dwmBackdropAcrylic)
	_, _, _ = procDwmSet.Call(uintptr(window), uintptr(dwmBackdropType),
		uintptr(unsafe.Pointer(&backdrop)), unsafe.Sizeof(backdrop))
}

// windowAlive — окно ещё существует: перед каждой перерисовкой это проверяется,
// иначе после закрытия лаунчер продолжал бы стучаться в мёртвый дескриптор.
func windowAlive(handle win.HWND) bool {
	alive, _, _ := procIsWin.Call(uintptr(windows.HWND(handle)))
	return alive != 0
}

// clipChildren — окно не закрашивает свои дочерние элементы. Без этого флага
// живой фон при каждой перерисовке затирал бы кнопки и подписи: они отдельные
// окна и сами о своей перерисовке не узнают.
func clipChildren(handle win.HWND) {
	style := win.GetWindowLongPtr(handle, win.GWL_STYLE)
	if style == 0 {
		return
	}
	win.SetWindowLongPtr(handle, win.GWL_STYLE, style|win.WS_CLIPCHILDREN)
}

// roundCorners — скруглённая область окну-виджету: именно так у карточек
// появляются мягкие, «стеклянные» углы, а не прямоугольные системные.
func roundCorners(handle win.HWND, width int, height int, radius int) {
	if width <= 0 || height <= 0 || radius <= 0 {
		return
	}
	if radius > width/2 {
		radius = width / 2
	}
	if radius > height/2 {
		radius = height / 2
	}
	region, _, _ := procRound.Call(0, 0, uintptr(width), uintptr(height),
		uintptr(radius*2), uintptr(radius*2))
	if region == 0 {
		return
	}
	applied, _, _ := procRgn.Call(uintptr(windows.HWND(handle)), region, 1)
	if applied == 0 {
		// Область не применилась — удаляем, чтобы не течь дескрипторами.
		_, _, _ = procDelete.Call(region)
	}
}
