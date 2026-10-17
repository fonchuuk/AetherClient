//go:build windows

package ui

import (
	"image"
	"reflect"

	"github.com/lxn/walk"
	"github.com/lxn/win"
)

// Стеклянный слой окна. Вся математика живёт в glassmath.go — здесь только
// то, что связано с Windows: как размытый фон становится фоном окна и как
// каждая деталь получает свой кусок стекла.
//
// Идея Liquid Glass: панель показывает сквозь себя размытое и преломлённое
// содержимое, которое у неё за спиной. Фон поэтому рисуется в памяти
// (см. glassField), размывается и раздаётся деталям — никакой «имитации
// одной обводкой».

// glassEngine — фон окна: рисует, размывает и отдаёт кисть окну.
type glassEngine struct {
	window *walk.MainWindow
	field  glassField
	small  *walk.Bitmap
	full   *walk.Bitmap
	brush  *walk.BitmapBrush
}

func newGlassEngine(window *walk.MainWindow) *glassEngine {
	engine := &glassEngine{window: window}
	// Фон: глубокий синий градиент и медленно плывущие пятна — синее,
	// фиолетовое и бирюзовое свечение.
	engine.field.Top, engine.field.Bottom = glassBackgroundColors()
	engine.field.Spots = defaultGlassSpots(rgbOf(cBlobViolet), rgbOf(cBlobTeal),
		rgbOf(cBlobBlue), rgbOf(cBlobLight))
	return engine
}

// tick — следующий кадр фона. Возвращает true, если фон перерисован.
func (g *glassEngine) tick() bool {
	if g.window == nil {
		return false
	}
	client := g.window.ClientBoundsPixels()
	if !g.field.step(client.Width, client.Height) {
		return false
	}
	small, err := walk.NewBitmapFromImageForDPI(g.field.gridImage(), 96)
	if err != nil {
		return false
	}
	full, err := walk.NewBitmapForDPI(walk.Size{Width: client.Width, Height: client.Height},
		g.window.DPI())
	if err != nil {
		small.Dispose()
		return false
	}
	canvas, err := walk.NewCanvasFromImage(full)
	if err != nil {
		small.Dispose()
		full.Dispose()
		return false
	}
	// Маленькую картинку растягиваем на всё окно: содержимое мягкое,
	// поэтому интерполяция не портит вид, а считается это быстро.
	_ = canvas.DrawImageStretchedPixels(small,
		walk.Rectangle{X: 0, Y: 0, Width: client.Width, Height: client.Height})
	canvas.Dispose()

	brush, err := walk.NewBitmapBrush(full)
	if err != nil {
		small.Dispose()
		full.Dispose()
		return false
	}
	g.window.SetBackground(brush)

	// Старые кисть и картинки удаляем только после подмены: окно уже
	// отвязалось от них внутри SetBackground.
	if g.brush != nil {
		g.brush.Dispose()
	}
	if g.full != nil {
		g.full.Dispose()
	}
	if g.small != nil {
		g.small.Dispose()
	}
	g.brush, g.full, g.small = brush, full, small
	return true
}

// compose — кусок стекла для детали, которая стоит в rect (координаты окна,
// реальные пиксели).
func (g *glassEngine) compose(rect glassRect, width int, height int,
	options glassOptions) *image.RGBA {
	rect.Width, rect.Height = width, height
	return g.field.compose(rect, options)
}

func (g *glassEngine) dispose() {
	if g.brush != nil {
		g.brush.Dispose()
	}
	if g.full != nil {
		g.full.Dispose()
	}
	if g.small != nil {
		g.small.Dispose()
	}
	g.brush, g.full, g.small = nil, nil, nil
}

// ─────────────────────────────── стеклянная деталь ───────────────────────────────

// backgroundTarget — контейнер или контрол, которому стекло становится
// фоном (карточка, поле ввода, лог).
type backgroundTarget interface {
	SetBackground(background walk.Brush)
	Handle() win.HWND
}

// glassPane — стеклянная подложка конкретной детали: карточки, кнопки
// или поля ввода.
type glassPane struct {
	widget  *walk.CustomWidget // деталь рисует стекло сама
	target  backgroundTarget   // контейнер или контрол: стекло — его фон
	options glassOptions
	bitmap  *walk.Bitmap
	brush   *walk.BitmapBrush
}

// newGlassPaneFor — подложка для виджета, который рисует себя сам.
func newGlassPaneFor(widget *walk.CustomWidget, options glassOptions) *glassPane {
	if widget == nil {
		return nil
	}
	return &glassPane{widget: widget, options: options}
}

// newGlassCard — подложка для карточки-контейнера.
func newGlassCard(card *walk.Composite, radius int, options glassOptions) *glassPane {
	if card == nil {
		return nil
	}
	options.Radius = radius
	options.RoundAlpha = false
	return &glassPane{target: card, options: options}
}

// newGlassControl — подложка для обычного контрола: поля ввода, лога.
func newGlassControl(control backgroundTarget, options glassOptions) *glassPane {
	if isNilControl(control) {
		return nil
	}
	options.RoundAlpha = false
	return &glassPane{target: control, options: options}
}

// refresh — пересобрать подложку под текущим положением детали.
func (p *glassPane) refresh(window *walk.MainWindow, engine *glassEngine) {
	if engine == nil || window == nil {
		return
	}
	handle := p.handle()
	if handle == 0 {
		return
	}
	bounds := p.bounds()
	if bounds.Width <= 0 || bounds.Height <= 0 {
		return
	}
	position, ok := windowRectPixels(window, handle)
	if !ok {
		return
	}
	picture := engine.compose(position, bounds.Width, bounds.Height, p.options)
	if picture == nil {
		return
	}
	bitmap, err := walk.NewBitmapFromImageForDPI(picture, window.DPI())
	if err != nil {
		return
	}
	if p.widget != nil {
		// Виджет рисует стекло сам в своей отрисовке.
		if p.bitmap != nil {
			p.bitmap.Dispose()
		}
		p.bitmap = bitmap
		_ = p.widget.Invalidate()
		return
	}
	// У карточки и у контрола стекло — это их фон: дочерние элементы
	// получают его кистью и продолжают стекло под собой (так делает walk).
	brush, err := walk.NewBitmapBrush(bitmap)
	if err != nil {
		bitmap.Dispose()
		return
	}
	p.target.SetBackground(brush)
	if p.brush != nil {
		p.brush.Dispose()
	}
	if p.bitmap != nil {
		p.bitmap.Dispose()
	}
	p.brush, p.bitmap = brush, bitmap
}

// handle — окно детали.
func (p *glassPane) handle() win.HWND {
	if p.widget != nil {
		return p.widget.Handle()
	}
	if p.target != nil && !isNilControl(p.target) {
		return p.target.Handle()
	}
	return 0
}

// isNilControl — пустая ли ссылка. Интерфейс с nil-указателем внутри сам по
// себе не nil, поэтому проверять надо и то, и другое: иначе вызов Handle()
// на несуществующем контроле уронил бы окно.
func isNilControl(control backgroundTarget) bool {
	if control == nil {
		return true
	}
	value := reflect.ValueOf(control)
	return value.Kind() == reflect.Ptr && value.IsNil()
}

// bounds — размер детали в реальных пикселях.
func (p *glassPane) bounds() walk.Rectangle {
	if p.widget != nil {
		return p.widget.ClientBoundsPixels()
	}
	if p.target != nil && !isNilControl(p.target) {
		if window, ok := p.target.(walk.Window); ok {
			return window.ClientBoundsPixels()
		}
	}
	return walk.Rectangle{}
}

// draw — нарисовать подложку: у виджетов углы прозрачные, поэтому картинка
// кладётся с учётом альфы каждого пикселя.
func (p *glassPane) draw(canvas *walk.Canvas, bounds walk.Rectangle) {
	if p.bitmap == nil || canvas == nil {
		return
	}
	_ = canvas.DrawBitmapWithOpacity(p.bitmap, bounds, 255)
}

func (p *glassPane) dispose() {
	if p.brush != nil {
		p.brush.Dispose()
	}
	if p.bitmap != nil {
		p.bitmap.Dispose()
	}
	p.brush, p.bitmap = nil, nil
}

// windowRectPixels — положение дочернего окна внутри клиентской области
// в реальных пикселях: отсюда берётся кусок фона, который видно сквозь стекло.
func windowRectPixels(window *walk.MainWindow, handle win.HWND) (glassRect, bool) {
	var rect win.RECT
	if !win.GetWindowRect(handle, &rect) {
		return glassRect{}, false
	}
	point := win.POINT{X: rect.Left, Y: rect.Top}
	if !win.ScreenToClient(window.Handle(), &point) {
		return glassRect{}, false
	}
	return glassRect{X: int(point.X), Y: int(point.Y),
		Width: int(rect.Right - rect.Left), Height: int(rect.Bottom - rect.Top)}, true
}

// rgbOf — цвет палитры в виде компонент для математики стекла.
func rgbOf(value walk.Color) [3]float32 {
	return [3]float32{float32(value.R()), float32(value.G()), float32(value.B())}
}
