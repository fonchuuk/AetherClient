//go:build windows

package ui

import (
	"time"

	"github.com/lxn/walk"
	"github.com/lxn/win"
)

// animator — один таймер на всё окно: фон, стеклянные подложки, кнопки,
// полоса и статус двигаются в одном ритме, а не каждый сам по себе.
type animator struct {
	window  *walk.MainWindow
	items   []animated
	widgets []*walk.CustomWidget
	panes   []*glassPane
	engine  *glassEngine
	quit    chan struct{}
}

func newAnimator(window *walk.MainWindow) *animator {
	return &animator{window: window, engine: newGlassEngine(window),
		quit: make(chan struct{})}
}

func (a *animator) add(items ...animated) {
	a.items = append(a.items, items...)
}

// addWidgets — виджеты без своей анимации, но их всё равно нужно перерисовывать
// вместе с фоном: у них прозрачные уголки.
func (a *animator) addWidgets(widgets ...*walk.CustomWidget) {
	a.widgets = append(a.widgets, widgets...)
}

// addPanes — стеклянные детали: карточки и кнопки, которым нужен кусок
// размытого фона под собой.
func (a *animator) addPanes(panes ...*glassPane) {
	a.panes = append(a.panes, panes...)
}

func (a *animator) start() {
	go func() {
		ticker := time.NewTicker(55 * time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-a.quit:
				return
			case <-ticker.C:
				a.step(0.055)
			}
		}
	}()
}

func (a *animator) stopLoop() {
	select {
	case <-a.quit:
		// уже остановлен
	default:
		close(a.quit)
	}
}

// refreshPanes — пересобрать стеклянные подложки. Можно звать в любой момент:
// после смены страницы, после изменения размера, при первом показе.
func (a *animator) refreshPanes() {
	if a == nil || a.engine == nil || a.window == nil {
		return
	}
	if a.window.Handle() == 0 || !windowAlive(a.window.Handle()) {
		return
	}
	defer func() { _ = recover() }()
	a.window.Synchronize(func() {
		// Отрисовка стекла не имеет права ронять лаунчер: если что-то
		// пошло не так, окно просто продолжает жить на прежней подложке.
		defer func() { _ = recover() }()
		for _, pane := range a.panes {
			if pane != nil && pane.visible() {
				pane.refresh(a.window, a.engine)
			}
		}
	})
}

// step — один шаг анимации. Выполняется в потоке окна.
func (a *animator) step(dt float64) {
	if a.window == nil {
		return
	}
	handle := a.window.Handle()
	if handle == 0 || !windowAlive(handle) {
		return
	}
	defer func() {
		// Окно могли закрыть между проверкой и отрисовкой — тогда просто
		// выходим, это не ошибка.
		_ = recover()
	}()
	a.window.Synchronize(func() {
		// То же самое: паника внутри кадра не должна закрывать окно.
		defer func() { _ = recover() }()
		changed := a.engine.tick()
		if changed {
			// Фон перерисован — стеклянные детали берут новую подложку.
			for _, pane := range a.panes {
				if pane != nil && pane.visible() {
					pane.refresh(a.window, a.engine)
				}
			}
		}
		for _, item := range a.items {
			if item.tick(dt) {
				item.invalidate()
			}
		}
		if !changed {
			return
		}
		// Подложки обновились: перерисовываем всё, что на них стоит.
		for _, item := range a.items {
			if widget := item.handle(); widget != nil {
				_ = widget.Invalidate()
			}
		}
		for _, widget := range a.widgets {
			if widget != nil {
				_ = widget.Invalidate()
			}
		}
	})
}

func (a *animator) dispose() {
	for _, pane := range a.panes {
		if pane != nil {
			pane.dispose()
		}
	}
	a.panes = nil
	if a.engine != nil {
		a.engine.dispose()
		a.engine = nil
	}
}

// visible — видна ли деталь на самом деле. IsWindowVisible смотрит и на
// родителей: скрытая страница делает невидимыми все свои поля и кнопки.
func (p *glassPane) visible() bool {
	handle := p.handle()
	if handle == 0 {
		return false
	}
	return windowChainVisible(handle)
}

func windowChainVisible(handle win.HWND) bool {
	for handle != 0 {
		if !win.IsWindowVisible(handle) {
			return false
		}
		handle = win.GetParent(handle)
	}
	return true
}
