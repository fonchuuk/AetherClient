//go:build windows

package ui

import (
	"math"
	"strings"

	"github.com/lxn/walk"
	. "github.com/lxn/walk/declarative"
	"github.com/lxn/win"

	"aetherglass/launcher/core"
)

// Виджеты нарисованы вручную: стандартные кнопки Windows в тёмном стекле
// выглядят чужеродно. Фон виджету подкладывает сам walk — он берёт кисть
// родителя и выравнивает её по положению окна, поэтому «живой» фон просвечивает
// сквозь кнопки и подписи ровно там, где нужно (см. walk.NullBrush).

// animated — то, что умеет двигаться: тик анимации, нужна ли перерисовка.
// handle нужен затем, чтобы перерисовать виджет, когда обновился живой фон:
// виджет сам заливает себя родительской кистью, но делает это только когда
// его попросят.
type animated interface {
	tick(dt float64) bool
	invalidate()
	handle() *walk.CustomWidget
}

// cursorInside — курсор над виджетом: наведение определяем сами, потому что
// в walk нет события «ушли мышью».
func cursorInside(handle win.HWND) bool {
	var point win.POINT
	if !win.GetCursorPos(&point) {
		return false
	}
	var rect win.RECT
	if !win.GetWindowRect(handle, &rect) {
		return false
	}
	return point.X >= rect.Left && point.X < rect.Right &&
		point.Y >= rect.Top && point.Y < rect.Bottom
}

// ─────────────────────────────── кнопка ───────────────────────────────

type buttonKind int

const (
	buttonGlass buttonKind = iota
	buttonAccent
	buttonDanger
	buttonTab
)

type glassButton struct {
	widget   *walk.CustomWidget
	pane     *glassPane
	text     string
	kind     buttonKind
	width    int
	height   int
	onClick  func()
	hovered  bool
	pressed  bool
	active   bool
	enabled  bool
	scale    float64
	velocity float64
	target   float64
	glow     float64
}

func newGlassButton(text string, kind buttonKind, width int, height int,
	onClick func()) *glassButton {
	return &glassButton{text: text, kind: kind, width: width, height: height,
		onClick: onClick, enabled: true, scale: 1, target: 1}
}

// Declarative — описание виджета для дерева интерфейса.
func (b *glassButton) Declarative() CustomWidget {
	return CustomWidget{
		AssignTo:            &b.widget,
		Paint:               b.paint,
		OnMouseDown:         b.onMouseDown,
		OnMouseUp:           b.onMouseUp,
		MinSize:             Size{Width: b.width, Height: b.height},
		MaxSize:             Size{Width: 0, Height: b.height},
		InvalidatesOnResize: true,
	}
}

// afterCreate — включаем «прозрачность»: фон берётся у родителя.
func (b *glassButton) afterCreate() {
	if b.widget != nil {
		b.widget.SetBackground(walk.NullBrush())
	}
}

func (b *glassButton) onMouseDown(x int, y int, button walk.MouseButton) {
	if button != walk.LeftButton || !b.enabled || b.widget == nil {
		return
	}
	b.pressed = true
	b.target = 0.955
	b.widget.Invalidate()
}

func (b *glassButton) onMouseUp(x int, y int, button walk.MouseButton) {
	if !b.pressed || b.widget == nil {
		return
	}
	b.pressed = false
	b.target = 1
	b.widget.Invalidate()
	if b.onClick != nil && b.enabled {
		b.onClick()
	}
}

func (b *glassButton) isEnabled() bool { return b.enabled }

// SetEnabled — включает и выключает кнопку (во время работы блокируем лишнее).
func (b *glassButton) SetEnabled(enabled bool) {
	if b.widget == nil {
		b.enabled = enabled
		return
	}
	b.widget.Synchronize(func() {
		if b.enabled == enabled {
			return
		}
		b.enabled = enabled
		b.widget.SetEnabled(enabled)
		_ = b.widget.Invalidate()
	})
}

func (b *glassButton) SetText(text string) {
	if b.widget == nil {
		b.text = text
		return
	}
	b.widget.Synchronize(func() {
		b.text = text
		_ = b.widget.Invalidate()
	})
}

func (b *glassButton) handle() *walk.CustomWidget { return b.widget }

// glassOptions — как выглядит стекло этой кнопки. Вид стекла задаётся
// общими наборами (см. glassmath.go), цвет — палитрой лаунчера.
func (b *glassButton) glassOptions() glassOptions {
	tint := rgbOf(cGlassTint)
	switch b.kind {
	case buttonAccent:
		tint = rgbOf(cAccent)
	case buttonDanger:
		tint = rgbOf(cBad)
	}
	return buttonGlassOptions(tint, int(b.kind))
}

// SetActive — для вкладок: активная становится фиолетовым стеклом.
func (b *glassButton) SetActive(active bool) {
	if b.widget == nil {
		b.active = active
		return
	}
	b.widget.Synchronize(func() {
		b.active = active
		if b.pane != nil {
			if active {
				b.pane.options = activeTabOptions(rgbOf(cAccent))
			} else {
				b.pane.options = b.glassOptions()
			}
		}
		_ = b.widget.Invalidate()
	})
}

// textColor — цвет подписи с учётом состояния.
func (b *glassButton) textColor() walk.Color {
	var color walk.Color
	switch b.kind {
	case buttonAccent:
		color = walk.RGB(255, 255, 255)
	case buttonDanger:
		color = walk.RGB(255, 243, 246)
	case buttonTab:
		if b.active {
			color = walk.RGB(255, 255, 255)
		} else {
			hover := 0.0
			if b.hovered {
				hover = 1
			}
			color = mix(cMuted, cText, 0.25+0.65*hover)
		}
	default:
		color = cText
	}
	if !b.enabled {
		color = mix(color, cMuted, 0.62)
	}
	return color
}

// rimColor — цвет светлой кромки: по ней глаз читает толщину стекла.
func (b *glassButton) rimColor() walk.Color {
	switch b.kind {
	case buttonAccent:
		return mix(cAccent, walk.RGB(255, 255, 255), 0.42)
	case buttonDanger:
		return mix(cBad, walk.RGB(255, 255, 255), 0.38)
	case buttonTab:
		if b.active {
			return mix(cAccent2, cAccent, 0.45)
		}
		return cEdgeSoft
	default:
		return cEdge
	}
}

func (b *glassButton) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if b.widget == nil {
		return nil
	}
	bounds := b.widget.ClientBounds()
	if bounds.Width <= 0 || bounds.Height <= 0 {
		return nil
	}
	// Упругое сжатие: стекло как будто проминается под пальцем.
	shrinkX := int(float64(bounds.Width) * (1 - b.scale) / 2)
	shrinkY := int(float64(bounds.Height) * (1 - b.scale) / 2)
	rect := walk.Rectangle{X: bounds.X + shrinkX, Y: bounds.Y + shrinkY,
		Width: bounds.Width - 2*shrinkX, Height: bounds.Height - 2*shrinkY}
	if rect.Width <= 0 || rect.Height <= 0 {
		return nil
	}
	radius := rect.Height / 2
	if radius > 18 {
		radius = 18
	}

	// Основа — настоящая стеклянная подложка: размытый фон за кнопкой,
	// преломление у кромок, светлая кромка, блик и внутренняя тень.
	if b.pane != nil {
		b.pane.draw(canvas, rect)
	} else {
		fillRound(canvas, verticalBrush(cGlassTop, cGlassBot), rect, radius)
	}
	// Подсветка при наведении: мягкий свет рассеивается в толще стекла.
	if b.glow > 0.01 && blobWhite != nil {
		halo := walk.Rectangle{X: rect.X - rect.Width/3, Y: rect.Y - rect.Height,
			Width: rect.Width * 5 / 3, Height: rect.Height * 3}
		_ = canvas.DrawBitmapWithOpacity(blobWhite, halo, byte(5+20*b.glow))
	}
	// Две кромки: внешняя яркая, внутренняя потише — между ними читается
	// толщина стекла, а не просто обводка.
	strokeRound(canvas, mix(b.rimColor(), walk.RGB(255, 255, 255), 0.12+0.55*b.glow),
		rect, radius)
	if rect.Height > 10 {
		inner := walk.Rectangle{X: rect.X + 1, Y: rect.Y + 1,
			Width: rect.Width - 2, Height: rect.Height - 2}
		strokeRound(canvas, mix(cEdgeSoft, walk.RGB(255, 255, 255), 0.10+0.30*b.glow),
			inner, radius-1)
	}
	drawText(canvas, b.text, fontButton, b.textColor(), rect,
		walk.TextSingleLine|walk.TextCenter|walk.TextVCenter|walk.TextEndEllipsis)
	return nil
}

func (b *glassButton) tick(dt float64) bool {
	changed := false
	// Наведение: опрашиваем курсор, события «мышь ушла» в walk нет.
	hover := b.enabled && b.widget != nil && cursorInside(b.widget.Handle())
	if hover != b.hovered {
		b.hovered = hover
		changed = true
	}
	// Пружина: цель — 1 (отпущена) или 0.955 (нажата). Недодемпфированная,
	// поэтому кнопка на отпускании чуть перелетает и возвращается.
	force := (b.target - b.scale) * 260
	b.velocity += (force - b.velocity*16) * dt
	b.scale += b.velocity * dt
	if math.Abs(b.target-b.scale) < 0.0008 && math.Abs(b.velocity) < 0.01 {
		b.scale, b.velocity = b.target, 0
	} else {
		changed = true
	}
	// Свечение нарастает и гаснет плавно.
	glowTarget := 0.0
	if b.hovered {
		glowTarget = 0.7
	}
	if b.pressed {
		glowTarget = 1
	}
	if math.Abs(glowTarget-b.glow) > 0.01 {
		b.glow += (glowTarget - b.glow) * clamp(dt*9, 0, 1)
		changed = true
	} else {
		b.glow = glowTarget
	}
	return changed
}

func (b *glassButton) invalidate() {
	if b.widget != nil {
		_ = b.widget.Invalidate()
	}
}

// ─────────────────────────────── полоса хода ───────────────────────────────

type glassProgress struct {
	widget *walk.CustomWidget
	pane   *glassPane
	value  float64 // текущее, анимированное
	target float64 // куда тянем
	sheen  float64 // бегущий блик
}

func newGlassProgress() *glassProgress {
	return &glassProgress{}
}

func (p *glassProgress) Declarative() CustomWidget {
	return CustomWidget{
		AssignTo:            &p.widget,
		Paint:               p.paint,
		MinSize:             Size{Width: 0, Height: 12},
		MaxSize:             Size{Width: 0, Height: 12},
		InvalidatesOnResize: true,
	}
}

func (p *glassProgress) afterCreate() {
	if p.widget != nil {
		p.widget.SetBackground(walk.NullBrush())
	}
}

// Set — новое значение в процентах (0…100). Можно из любого потока.
func (p *glassProgress) Set(percent float64) {
	value := clamp(percent, 0, 100)
	if p.widget == nil {
		p.target = value
		return
	}
	p.widget.Synchronize(func() { p.target = value })
}

func (p *glassProgress) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if p.widget == nil {
		return nil
	}
	bounds := p.widget.ClientBounds()
	if bounds.Width <= 4 || bounds.Height <= 0 {
		return nil
	}
	track := walk.Rectangle{X: bounds.X, Y: bounds.Y + (bounds.Height-10)/2,
		Width: bounds.Width, Height: 10}
	radius := 5
	// Жёлоб — то же стекло, но вогнутое: подложка берётся из размытого фона.
	if p.pane != nil {
		p.pane.draw(canvas, track)
	} else {
		fillRound(canvas, verticalBrush(cGlassBot, cDeep), track, radius)
	}
	filled := int(float64(track.Width) * p.value / 100)
	if filled > 0 {
		bar := walk.Rectangle{X: track.X, Y: track.Y, Width: filled, Height: track.Height}
		fillRound(canvas, verticalBrush(
			mix(cAccent, walk.RGB(255, 255, 255), 0.32),
			mix(cAccent2, cAccent, 0.45)), bar, radius)
		// Гребень: тонкая светлая линия по верху — жидкость блестит.
		if filled > 2*radius {
			crest := walk.Rectangle{X: bar.X + radius/2, Y: bar.Y + 1,
				Width: filled - radius, Height: 3}
			fillRound(canvas, solidBrush(mix(walk.RGB(255, 255, 255), cAccent2, 0.30)),
				crest, 1)
		}
		// Блик бежит по готовой части: полоса «живая».
		if blobWhite != nil && p.value > 2 && p.value < 99 {
			size := track.Height * 3
			x := track.X + int(float64(filled-size)*p.sheen)
			if x < track.X {
				x = track.X
			}
			_ = canvas.DrawBitmapWithOpacity(blobWhite,
				walk.Rectangle{X: x, Y: track.Y - track.Height, Width: size, Height: size * 2}, 54)
		}
	}
	strokeRound(canvas, mix(cEdgeSoft, cAccent2, 0.35), track, radius)
	return nil
}

// glassOptions — стекло жёлоба.
func (p *glassProgress) glassOptions() glassOptions {
	return trackGlassOptions(rgbOf(cGlassBot), 5)
}

func (p *glassProgress) tick(dt float64) bool {
	changed := false
	if math.Abs(p.target-p.value) > 0.2 {
		p.value += (p.target - p.value) * clamp(dt*7, 0, 1)
		changed = true
	} else if p.value != p.target {
		p.value = p.target
		changed = true
	}
	if p.value > 1 && p.value < 99.5 {
		p.sheen += dt * 0.55
		for p.sheen > 1 {
			p.sheen -= 2
		}
		if p.sheen < 0 {
			p.sheen = -p.sheen
		}
		changed = true
	}
	return changed
}

func (p *glassProgress) invalidate() {
	if p.widget != nil {
		_ = p.widget.Invalidate()
	}
}

func (p *glassProgress) handle() *walk.CustomWidget { return p.widget }

// ─────────────────────────────── переключатель ───────────────────────────────

type glassSwitch struct {
	widget   *walk.CustomWidget
	pane     *glassPane
	label    string
	on       bool
	knob     float64
	hovered  bool
	onChange func(bool)
}

func newGlassSwitch(label string, on bool, onChange func(bool)) *glassSwitch {
	return &glassSwitch{label: label, on: on, onChange: onChange, knob: 0}
}

func (s *glassSwitch) Declarative() CustomWidget {
	return CustomWidget{
		AssignTo:            &s.widget,
		Paint:               s.paint,
		OnMouseDown:         s.onMouseDown,
		MinSize:             Size{Width: 0, Height: 26},
		MaxSize:             Size{Width: 0, Height: 26},
		InvalidatesOnResize: true,
	}
}

func (s *glassSwitch) afterCreate() {
	if s.widget != nil {
		s.widget.SetBackground(walk.NullBrush())
		s.knob = 0
		if s.on {
			s.knob = 1
		}
	}
}

func (s *glassSwitch) onMouseDown(x int, y int, button walk.MouseButton) {
	if button != walk.LeftButton {
		return
	}
	s.Set(!s.on)
	if s.onChange != nil {
		s.onChange(s.on)
	}
}

// Set — включить или выключить (без вызова обработчика).
func (s *glassSwitch) Set(on bool) {
	if s.widget == nil {
		s.on = on
		return
	}
	s.widget.Synchronize(func() {
		s.on = on
		_ = s.widget.Invalidate()
	})
}

func (s *glassSwitch) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if s.widget == nil {
		return nil
	}
	bounds := s.widget.ClientBounds()
	trackWidth, trackHeight := 46, 22
	track := walk.Rectangle{X: bounds.X + bounds.Width - trackWidth,
		Y: bounds.Y + (bounds.Height-trackHeight)/2, Width: trackWidth, Height: trackHeight}
	textBounds := walk.Rectangle{X: bounds.X, Y: bounds.Y,
		Width: bounds.Width - trackWidth - 10, Height: bounds.Height}
	drawText(canvas, s.label, fontBody, cText, textBounds,
		walk.TextSingleLine|walk.TextVCenter|walk.TextEndEllipsis)

	on := s.knob
	if on < 0 {
		on = 0
	}
	if on > 1 {
		on = 1
	}
	// Жёлоб — стекло: берём из подложки ровно тот кусок, что под ним.
	if s.pane != nil && s.pane.bitmap != nil {
		_ = canvas.DrawBitmapPartWithOpacity(s.pane.bitmap, track, track, 255)
	} else {
		fillRound(canvas, verticalBrush(mix(cGlassBot, cDeep, 0.5),
			mix(cGlassTop, cAccent, 0.35*on)), track, trackHeight/2)
	}
	// Включённый переключатель залит мягким светом изнутри.
	if on > 0.01 && blobWhite != nil {
		glow := walk.Rectangle{X: track.X - 6, Y: track.Y - 8,
			Width: track.Width + 12, Height: track.Height + 16}
		_ = canvas.DrawBitmapWithOpacity(blobWhite, glow, byte(60*on))
	}
	strokeRound(canvas, mix(cEdgeSoft, mix(cAccent2, walk.RGB(255, 255, 255), 0.4), on),
		track, trackHeight/2)

	// Ручка: стеклянный шарик, который перекатывается.
	knobSize := trackHeight - 6
	offset := 3 + on*float64(trackWidth-knobSize-6)
	knob := walk.Rectangle{X: track.X + int(offset), Y: track.Y + 3,
		Width: knobSize, Height: knobSize}
	fillRound(canvas, verticalBrush(walk.RGB(255, 255, 255), mix(cAccent2, cAccent, 0.55)),
		knob, knobSize/2)
	strokeRound(canvas, mix(cEdgeSoft, walk.RGB(255, 255, 255), 0.7), knob, knobSize/2)
	return nil
}

// glassOptions — стекло переключателя.
func (s *glassSwitch) glassOptions() glassOptions {
	return trackGlassOptions(rgbOf(cGlassTint), 11)
}

func (s *glassSwitch) tick(dt float64) bool {
	changed := false
	target := 0.0
	if s.on {
		target = 1
	}
	if math.Abs(target-s.knob) > 0.002 {
		s.knob += (target - s.knob) * clamp(dt*11, 0, 1)
		changed = true
	} else if s.knob != target {
		s.knob = target
		changed = true
	}
	if s.widget != nil {
		hover := cursorInside(s.widget.Handle())
		if hover != s.hovered {
			s.hovered = hover
			changed = true
		}
	}
	return changed
}

func (s *glassSwitch) invalidate() {
	if s.widget != nil {
		_ = s.widget.Invalidate()
	}
}

func (s *glassSwitch) handle() *walk.CustomWidget { return s.widget }

// ─────────────────────────────── подписи ───────────────────────────────

// glassText — нарисованная вручную подпись: обычная метка Windows заливала бы
// свой прямоугольник和系统ным цветом, а тут текст лежит прямо на стекле.
type glassText struct {
	widget *walk.CustomWidget
	text   string
	font   *walk.Font
	color  walk.Color
	height int
	wrap   bool
	center bool
}

func newGlassText(text string, font *walk.Font, color walk.Color, height int) *glassText {
	return &glassText{text: text, font: font, color: color, height: height}
}

func (t *glassText) Declarative() CustomWidget {
	return CustomWidget{
		AssignTo:            &t.widget,
		Paint:               t.paint,
		MinSize:             Size{Width: 0, Height: t.height},
		MaxSize:             Size{Width: 0, Height: t.height},
		InvalidatesOnResize: true,
	}
}

func (t *glassText) afterCreate() {
	if t.widget != nil {
		t.widget.SetBackground(walk.NullBrush())
	}
}

func (t *glassText) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if t.widget == nil {
		return nil
	}
	bounds := t.widget.ClientBounds()
	format := walk.DrawTextFormat(walk.TextSingleLine | walk.TextVCenter | walk.TextEndEllipsis)
	if t.wrap {
		format = walk.TextWordbreak
	}
	if t.center {
		format |= walk.TextCenter
	}
	drawText(canvas, t.text, t.font, t.color, bounds, format)
	return nil
}

// SetText — новый текст. Можно вызывать из любого потока: обновление
// выполнится в потоке окна.
func (t *glassText) SetText(text string) {
	if t.widget == nil {
		t.text = text
		return
	}
	t.widget.Synchronize(func() {
		if t.text == text {
			return
		}
		t.text = text
		_ = t.widget.Invalidate()
	})
}

func (t *glassText) SetColor(color walk.Color) {
	if t.widget == nil {
		t.color = color
		return
	}
	t.widget.Synchronize(func() {
		t.color = color
		_ = t.widget.Invalidate()
	})
}

func (t *glassText) invalidate() {
	if t.widget != nil {
		_ = t.widget.Invalidate()
	}
}

func (t *glassText) handle() *walk.CustomWidget { return t.widget }

// ─────────────────────────────── статус ───────────────────────────────

// statusPill — стеклянная «таблетка» с дышащей точкой: показывает состояние.
type statusPill struct {
	widget *walk.CustomWidget
	pane   *glassPane
	text   string
	dot    walk.Color
	pulse  float64
}

func newStatusPill(text string) *statusPill {
	return &statusPill{text: text, dot: cMuted, pulse: 0}
}

func (s *statusPill) Declarative() CustomWidget {
	return CustomWidget{
		AssignTo:            &s.widget,
		Paint:               s.paint,
		MinSize:             Size{Width: 240, Height: 30},
		MaxSize:             Size{Width: 320, Height: 30},
		InvalidatesOnResize: true,
	}
}

func (s *statusPill) afterCreate() {
	if s.widget != nil {
		s.widget.SetBackground(walk.NullBrush())
	}
}

func (s *statusPill) Set(text string, dot walk.Color) {
	if s.widget == nil {
		s.text, s.dot = text, dot
		return
	}
	s.widget.Synchronize(func() {
		s.text, s.dot = text, dot
		_ = s.widget.Invalidate()
	})
}

func (s *statusPill) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if s.widget == nil {
		return nil
	}
	bounds := s.widget.ClientBounds()
	if bounds.Width <= 0 || bounds.Height <= 0 {
		return nil
	}
	// Стеклянная таблетка: размытый фон, преломление, кромка — как у карточек.
	if s.pane != nil {
		s.pane.draw(canvas, bounds)
	} else {
		fillRound(canvas, verticalBrush(mix(cGlassTop, cText, 0.05), cGlassBot),
			bounds, bounds.Height/2)
	}
	strokeRound(canvas, mix(cEdgeSoft, walk.RGB(255, 255, 255), 0.25), bounds, bounds.Height/2)

	// Точка: дышит, поэтому видно, что лаунчер живой, даже когда он ждёт.
	dotSize := 8
	dot := walk.Rectangle{X: bounds.X + 12, Y: bounds.Y + (bounds.Height-dotSize)/2,
		Width: dotSize, Height: dotSize}
	brightness := 0.65 + 0.35*math.Sin(s.pulse)
	fillRound(canvas, solidBrush(mix(cGlassBot, s.dot, brightness)), dot, dotSize/2)
	strokeRound(canvas, mix(s.dot, walk.RGB(255, 255, 255), 0.4), dot, dotSize/2)

	text := walk.Rectangle{X: bounds.X + 28, Y: bounds.Y,
		Width: bounds.Width - 38, Height: bounds.Height}
	drawText(canvas, s.text, fontBody, cText, text,
		walk.TextSingleLine|walk.TextVCenter|walk.TextEndEllipsis)
	return nil
}

// glassOptions — стекло таблетки статуса.
func (s *statusPill) glassOptions() glassOptions {
	return pillGlassOptions(rgbOf(cGlassTint))
}

func (s *statusPill) tick(dt float64) bool {
	s.pulse += dt * 2.4
	for s.pulse > 2*math.Pi {
		s.pulse -= 2 * math.Pi
	}
	return true
}

func (s *statusPill) invalidate() {
	if s.widget != nil {
		_ = s.widget.Invalidate()
	}
}

func (s *statusPill) handle() *walk.CustomWidget { return s.widget }

// ─────────────────────────────── список модов ───────────────────────────────

const modRowHeight = 26

type modsPanel struct {
	widget *walk.CustomWidget
	rows   []core.ModStatus
	count  int // сколько строк заложено по высоте
	hover  int
}

func newModsPanel(count int) *modsPanel {
	return &modsPanel{rows: []core.ModStatus{}, count: count, hover: -1}
}

func (m *modsPanel) Declarative() CustomWidget {
	height := modRowHeight * m.count
	if height < modRowHeight {
		height = modRowHeight
	}
	return CustomWidget{
		AssignTo:            &m.widget,
		Paint:               m.paint,
		OnMouseMove:         m.onMouseMove,
		MinSize:             Size{Width: 0, Height: height},
		InvalidatesOnResize: true,
		StretchFactor:       1,
	}
}

func (m *modsPanel) afterCreate() {
	if m.widget != nil {
		m.widget.SetBackground(walk.NullBrush())
	}
}

func (m *modsPanel) Set(rows []core.ModStatus) {
	if m.widget == nil {
		m.rows = rows
		return
	}
	m.widget.Synchronize(func() {
		m.rows = rows
		_ = m.widget.Invalidate()
	})
}

func (m *modsPanel) onMouseMove(x int, y int, button walk.MouseButton) {
	row := y / modRowHeight
	if row < 0 || row >= len(m.rows) {
		row = -1
	}
	if row != m.hover {
		m.hover = row
		m.invalidate()
	}
}

func (m *modsPanel) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if m.widget == nil {
		return nil
	}
	bounds := m.widget.ClientBounds()
	for index, row := range m.rows {
		top := bounds.Y + index*modRowHeight
		if top > bounds.Y+bounds.Height {
			break
		}
		rect := walk.Rectangle{X: bounds.X + 2, Y: top + 1,
			Width: bounds.Width - 4, Height: modRowHeight - 2}
		// Подсветка строки под курсором: еле заметная, как у стекла.
		if index == m.hover {
			fillRound(canvas, solidBrush(mix(cGlassTop, cText, 0.06)), rect, 8)
		}
		color := cMuted
		switch {
		case strings.Contains(row.State, "установлен"):
			color = cGood
		case strings.Contains(row.State, "повреждён"):
			color = cBad
		case strings.Contains(row.State, "нужно"):
			color = cWarn
		}
		dot := walk.Rectangle{X: rect.X + 8, Y: rect.Y + (rect.Height-8)/2, Width: 8, Height: 8}
		fillRound(canvas, solidBrush(color), dot, 4)

		name := walk.Rectangle{X: rect.X + 24, Y: rect.Y,
			Width: rect.Width - 150, Height: rect.Height}
		drawText(canvas, row.Name, fontBody, cText, name,
			walk.TextSingleLine|walk.TextVCenter|walk.TextEndEllipsis)
		version := walk.Rectangle{X: rect.X + rect.Width - 122, Y: rect.Y,
			Width: 74, Height: rect.Height}
		drawText(canvas, row.Version, fontSmall, cMuted, version,
			walk.TextSingleLine|walk.TextVCenter|walk.TextEndEllipsis)
		state := walk.Rectangle{X: rect.X + rect.Width - 44, Y: rect.Y,
			Width: 40, Height: rect.Height}
		drawText(canvas, shortState(row.State), fontSmall, color, state,
			walk.TextSingleLine|walk.TextVCenter)
	}
	return nil
}

// shortState — короткая метка состояния для правой колонки.
func shortState(state string) string {
	switch {
	case strings.Contains(state, "установлен"):
		return "ок"
	case strings.Contains(state, "повреждён"):
		return "битый"
	default:
		return "нет"
	}
}

func (m *modsPanel) tick(dt float64) bool { return false }

func (m *modsPanel) invalidate() {
	if m.widget != nil {
		_ = m.widget.Invalidate()
	}
}

func (m *modsPanel) handle() *walk.CustomWidget { return m.widget }

// ─────────────────────────── логотип и блики ───────────────────────────

// logoMark — стеклянный знак лаунчера: толстая линза, которая увеличивает
// и преломляет то, что за ней, с яркой кромкой и бликом внутри.
type logoMark struct {
	widget *walk.CustomWidget
	pane   *glassPane
	size   int
}

func newLogoMark(size int) *logoMark {
	return &logoMark{size: size}
}

func (l *logoMark) Declarative() CustomWidget {
	return CustomWidget{
		AssignTo: &l.widget,
		MinSize:  Size{Width: l.size, Height: l.size},
		MaxSize:  Size{Width: l.size, Height: l.size},
		Paint:    l.paint,
	}
}

func (l *logoMark) paint(canvas *walk.Canvas, update walk.Rectangle) error {
	if l.widget == nil {
		return nil
	}
	bounds := l.widget.ClientBounds()
	if bounds.Width <= 0 || bounds.Height <= 0 {
		return nil
	}
	if l.pane != nil {
		l.pane.draw(canvas, bounds)
	} else {
		fillRound(canvas, verticalBrush(mix(cAccent, walk.RGB(255, 255, 255), 0.12),
			mix(cAccent2, cAccent, 0.55)), bounds, 14)
	}
	// Кромка и внутренняя кромка: линза читается как объёмная.
	strokeRound(canvas, mix(cAccent2, walk.RGB(255, 255, 255), 0.55), bounds, 14)
	inner := walk.Rectangle{X: bounds.X + 2, Y: bounds.Y + 2,
		Width: bounds.Width - 4, Height: bounds.Height - 4}
	strokeRound(canvas, mix(cAccent2, walk.RGB(255, 255, 255), 0.15), inner, 12)
	// Блик: светлое пятно сверху, как отражение лампы на стекле.
	if blobWhite != nil {
		glare := walk.Rectangle{X: bounds.X + bounds.Width/8, Y: bounds.Y + bounds.Height/9,
			Width: bounds.Width * 2 / 3, Height: bounds.Height * 2 / 3}
		_ = canvas.DrawBitmapWithOpacity(blobWhite, glare, 70)
	}
	return nil
}

// glassOptions — линза: сильное увеличение и преломление.
func (l *logoMark) glassOptions() glassOptions {
	return lensGlassOptions(rgbOf(cAccent))
}

// sheenWidget — тонкая светлая линия сверху карточки: та самая кромка,
// по которой глаз читает «стекло». Рисуется поверх стеклянной подложки.
func sheenWidget(height int, target **walk.CustomWidget) CustomWidget {
	var widget *walk.CustomWidget
	return CustomWidget{
		AssignTo: &widget,
		MinSize:  Size{Width: 0, Height: height},
		MaxSize:  Size{Width: 0, Height: height},
		Paint: func(canvas *walk.Canvas, update walk.Rectangle) error {
			bounds := widget.ClientBounds()
			if bounds.Width <= 2 {
				return nil
			}
			half := bounds.Width / 2
			left := walk.Rectangle{X: bounds.X, Y: bounds.Y, Width: half, Height: bounds.Height}
			right := walk.Rectangle{X: bounds.X + half, Y: bounds.Y,
				Width: bounds.Width - half, Height: bounds.Height}
			bright := mix(cAccent2, walk.RGB(255, 255, 255), 0.35)
			_ = canvas.GradientFillRectangle(mix(cGlassTop, cAccent2, 0.35), bright,
				walk.Horizontal, left)
			_ = canvas.GradientFillRectangle(bright, mix(cGlassTop, cAccent2, 0.15),
				walk.Horizontal, right)
			return nil
		},
	}
}

// ─────────────────────────── стеклянная карточка ───────────────────────────

// decorateCard делает из обычного контейнера стеклянную панель: подложка из
// размытого фона, преломление у краёв, светлая кромка, скруглённые углы.
// Возвращает подложку, которую надо отдать аниматору.
func decorateCard(card *walk.Composite, radius int) *glassPane {
	if card == nil {
		return nil
	}
	// Пока подложка не собрана, карточка живёт градиентом — без пустоты.
	card.SetBackground(verticalBrush(cGlassTop, cGlassBot))
	applyCardRegion(card, radius)
	card.BoundsChanged().Attach(func() {
		applyCardRegion(card, radius)
	})
	return newGlassCard(card, radius, cardGlassOptions(rgbOf(cGlassTint)))
}

func applyCardRegion(card *walk.Composite, radius int) {
	bounds := card.BoundsPixels()
	roundCorners(card.Handle(), bounds.Width, bounds.Height, radius)
}
