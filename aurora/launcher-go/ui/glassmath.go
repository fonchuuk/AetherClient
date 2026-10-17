package ui

// Чистая математика стекла: здесь нет ни одного вызова Windows, только
// числа и картинки в памяти. Поэтому её можно прогнать тестом на любой
// машине и проверить, что размытие, преломление и кромка действительно
// получаются такими, как задумано (см. glassmath_test.go).

import (
	"image"
	"math"
)

// gridScale — во сколько раз сетка фона меньше реальных пикселей.
const gridScale = 3

// blurRadius — радиус размытия в клетках сетки (×3 = реальные пиксели).
const blurRadius = 7

// glassShadowStrength — насколько темнеет самый низ стекла (доля яркости).
const glassShadowStrength = 0.5

// glassShadowIndent — на сколько пикселей тень сдвинута внутрь от нижней
// кромки: сам край остаётся светлым, а темнеет стекло уже за ним.
const glassShadowIndent = 3

// blurPasses — число проходов box blur: три прохода дают форму, близкую
// к гауссовой, и стоят недорого.
const blurPasses = 3

// glassRect — прямоугольник в координатах окна (реальные пиксели).
type glassRect struct {
	X      int
	Y      int
	Width  int
	Height int
}

// glassSpot — световое пятно на фоне: цвет, центр, размер и своя орбита.
type glassSpot struct {
	Color [3]float32
	BaseX float64 // доля ширины
	BaseY float64 // доля высоты
	Size  float64 // доля от большей стороны
	AmpX  float64 // амплитуда дрейфа, доля ширины
	AmpY  float64 // амплитуда дрейфа, доля высоты
	Speed float64
	Shift float64
	Alpha float32
}

// glassField — фон за стеклом: нарисованный, а затем размытый.
type glassField struct {
	GridW   int
	GridH   int
	Sharp   []float32 // фон до размытия: rgb по строкам
	Blurred []float32 // размытый и усиленный фон — то, что видно сквозь стекло
	Phase   float64
	Frame   int
	Top     [3]float32 // цвет сверху
	Bottom  [3]float32 // цвет снизу
	Ready   bool       // фон уже хоть раз отрисован
	Spots   []glassSpot
}

// framesPerGlassUpdate — стекло обновляется примерно шесть раз в секунду:
// пятна плывут медленно, а процессор и память это экономит.
const framesPerGlassUpdate = 3

// step — следующий кадр. Возвращает true, если фон перерисован.
func (f *glassField) step(width int, height int) bool {
	if width < 16 || height < 16 {
		return false
	}
	f.Frame++
	// Первый кадр рисуем сразу: иначе окно полсекунды стоит пустым.
	if f.Frame < framesPerGlassUpdate && f.Ready {
		return false
	}
	f.Frame = 0
	f.Phase += 0.03

	gridW := width/gridScale + 2
	gridH := height/gridScale + 2
	if gridW != f.GridW || gridH != f.GridH {
		f.GridW, f.GridH = gridW, gridH
		f.Sharp = make([]float32, 3*gridW*gridH)
		f.Blurred = make([]float32, 3*gridW*gridH)
	}
	f.render()
	f.blur()
	f.Ready = true
	return true
}

// render — фон: вертикальный градиент и медленно плывущие пятна.
// Считаются только клетки внутри каждого пятна: на глаз то же самое,
// но в разы быстрее.
func (f *glassField) render() {
	w, h := f.GridW, f.GridH
	if w <= 0 || h <= 0 || len(f.Sharp) < 3*w*h {
		return
	}
	fw, fh := float64(w), float64(h)
	base := math.Max(fw, fh)

	// Пятна переводим из долей в клетки и считаем центры на этом кадре.
	type prepared struct {
		color   [3]float32
		alpha   float32
		cx      float64
		cy      float64
		radius  float64
		squared float64
	}
	spots := make([]prepared, 0, len(f.Spots))
	for _, spot := range f.Spots {
		if spot.Alpha <= 0 || spot.Size <= 0 {
			continue
		}
		radius := spot.Size * base / 2
		if radius < 1 {
			continue
		}
		spots = append(spots, prepared{
			color:   spot.Color,
			alpha:   spot.Alpha,
			cx:      spot.BaseX*fw + math.Sin(f.Phase*spot.Speed+spot.Shift)*spot.AmpX*fw,
			cy:      spot.BaseY*fh + math.Cos(f.Phase*spot.Speed*0.8+spot.Shift)*spot.AmpY*fh,
			radius:  radius,
			squared: radius * radius,
		})
	}

	data := f.Sharp
	// Градиент: сверху чуть светлее, снизу — почти чёрный.
	for y := 0; y < h; y++ {
		v := float32(y) / float32(h-1)
		rowR := f.Top[0] + (f.Bottom[0]-f.Top[0])*v
		rowG := f.Top[1] + (f.Bottom[1]-f.Top[1])*v
		rowB := f.Top[2] + (f.Bottom[2]-f.Top[2])*v
		row := y * w
		for x := 0; x < w; x++ {
			index := 3 * (row + x)
			data[index], data[index+1], data[index+2] = rowR, rowG, rowB
		}
	}

	// Пятна: проходим только по их кругам.
	for _, spot := range spots {
		fromY := int(math.Ceil(spot.cy - spot.radius))
		toY := int(math.Floor(spot.cy + spot.radius))
		if fromY < 0 {
			fromY = 0
		}
		if toY > h-1 {
			toY = h - 1
		}
		for y := fromY; y <= toY; y++ {
			dy := float64(y) - spot.cy
			span := math.Sqrt(math.Max(spot.squared-dy*dy, 0))
			fromX := int(math.Ceil(spot.cx - span))
			toX := int(math.Floor(spot.cx + span))
			if fromX < 0 {
				fromX = 0
			}
			if toX > w-1 {
				toX = w - 1
			}
			dy2 := dy * dy
			row := y * w
			for x := fromX; x <= toX; x++ {
				dx := float64(x) - spot.cx
				share := (dx*dx + dy2) / spot.squared
				if share >= 1 {
					continue
				}
				a := spot.alpha * spotFalloff(share)
				index := 3 * (row + x)
				data[index] = data[index]*(1-a) + spot.color[0]*a
				data[index+1] = data[index+1]*(1-a) + spot.color[1]*a
				data[index+2] = data[index+2]*(1-a) + spot.color[2]*a
			}
		}
	}
}

// spotFalloff — спад яркости пятна от центра к краю: cos²(d·π/2), но по
// таблице, чтобы не считать косинус миллион раз за кадр.
var spotFalloffTable = func() []float32 {
	const size = 257
	table := make([]float32, size)
	for i := 0; i < size; i++ {
		d := float64(i) / float64(size-1)
		wave := math.Cos(d * math.Pi / 2)
		table[i] = float32(wave * wave)
	}
	return table
}()

func spotFalloff(share float64) float32 {
	if share <= 0 {
		return 1
	}
	if share >= 1 {
		return 0
	}
	index := int(share * float64(len(spotFalloffTable)-1))
	return spotFalloffTable[index]
}

// blur — размытие фона. Три прохода box blur дают форму, близкую к гауссовой;
// затем лёгкое усиление яркости и насыщенности: стекло всегда чуть сочнее
// того, что за ним.
func (f *glassField) blur() {
	w, h := f.GridW, f.GridH
	if len(f.Blurred) < len(f.Sharp) {
		return
	}
	copy(f.Blurred, f.Sharp)
	temporary := make([]float32, len(f.Blurred))
	for pass := 0; pass < blurPasses; pass++ {
		boxBlur(f.Blurred, temporary, w, h, blurRadius, true)
		boxBlur(temporary, f.Blurred, w, h, blurRadius, false)
	}
	for i := 0; i < len(f.Blurred); i += 3 {
		r, gr, b := f.Blurred[i], f.Blurred[i+1], f.Blurred[i+2]
		grey := 0.2126*r + 0.7152*gr + 0.0722*b
		r = grey + (r-grey)*1.35
		gr = grey + (gr-grey)*1.35
		b = grey + (b-grey)*1.35
		f.Blurred[i] = clampColor(r*1.55 + 16)
		f.Blurred[i+1] = clampColor(gr*1.55 + 16)
		f.Blurred[i+2] = clampColor(b*1.55 + 24)
	}
}

// boxBlur — размытие по одной оси бегущей суммой: быстро и без артефактов.
func boxBlur(source []float32, target []float32, w int, h int, radius int,
	horizontal bool) {
	if len(source) == 0 || len(target) < len(source) {
		return
	}
	if radius < 1 {
		copy(target, source)
		return
	}
	if horizontal {
		for y := 0; y < h; y++ {
			row := y * w * 3
			for channel := 0; channel < 3; channel++ {
				sum := float32(0)
				for x := -radius; x <= radius; x++ {
					sum += source[row+clampInt(x, 0, w-1)*3+channel]
				}
				count := float32(radius*2 + 1)
				for x := 0; x < w; x++ {
					target[row+x*3+channel] = sum / count
					out := source[row+clampInt(x-radius, 0, w-1)*3+channel]
					in := source[row+clampInt(x+radius+1, 0, w-1)*3+channel]
					sum += in - out
				}
			}
		}
		return
	}
	for x := 0; x < w; x++ {
		for channel := 0; channel < 3; channel++ {
			sum := float32(0)
			for y := -radius; y <= radius; y++ {
				sum += source[clampInt(y, 0, h-1)*w*3+x*3+channel]
			}
			count := float32(radius*2 + 1)
			for y := 0; y < h; y++ {
				target[y*w*3+x*3+channel] = sum / count
				out := source[clampInt(y-radius, 0, h-1)*w*3+x*3+channel]
				in := source[clampInt(y+radius+1, 0, h-1)*w*3+x*3+channel]
				sum += in - out
			}
		}
	}
}

// sample — билинейная выборка размытого фона в клетках сетки.
func (f *glassField) sample(x float32, y float32) (float32, float32, float32) {
	return f.sampleFrom(f.Blurred, x, y)
}

// sampleFrom — билинейная выборка из любого слоя фона (размытый или чёткий).
func (f *glassField) sampleFrom(data []float32, x float32,
	y float32) (float32, float32, float32) {
	if len(data) == 0 {
		return 0, 0, 0
	}
	w, h := float32(f.GridW), float32(f.GridH)
	if x < 0 {
		x = 0
	}
	if y < 0 {
		y = 0
	}
	if x > w-1.001 {
		x = w - 1.001
	}
	if y > h-1.001 {
		y = h - 1.001
	}
	if x < 0 {
		x = 0
	}
	if y < 0 {
		y = 0
	}
	x0, y0 := int(x), int(y)
	fx, fy := x-float32(x0), y-float32(y0)
	i00 := 3 * (y0*f.GridW + x0)
	i10 := i00 + 3
	i01 := i00 + 3*f.GridW
	i11 := i01 + 3
	var result [3]float32
	for c := 0; c < 3; c++ {
		top := data[i00+c]*(1-fx) + data[i10+c]*fx
		low := data[i01+c]*(1-fx) + data[i11+c]*fx
		result[c] = top*(1-fy) + low*fy
	}
	return result[0], result[1], result[2]
}

// gridImage — текущий фон как картинка: она растягивается на всё окно
// и становится его фоном.
func (f *glassField) gridImage() *image.RGBA {
	out := image.NewRGBA(image.Rect(0, 0, f.GridW, f.GridH))
	for y := 0; y < f.GridH; y++ {
		for x := 0; x < f.GridW; x++ {
			offset := 3 * (y*f.GridW + x)
			index := y*out.Stride + x*4
			out.Pix[index] = byte(clampColor(f.Sharp[offset]))
			out.Pix[index+1] = byte(clampColor(f.Sharp[offset+1]))
			out.Pix[index+2] = byte(clampColor(f.Sharp[offset+2]))
			out.Pix[index+3] = 255
		}
	}
	return out
}

// ─────────────────────────────── как выглядит стекло ───────────────────────────────

// glassOptions — как именно это стекло выглядит.
type glassOptions struct {
	Tint       [3]float32 // собственный цвет стекла
	TintAlpha  float32    // его прозрачность: 0 — чистое стекло
	Rim        float32    // светлая кромка по контуру
	Edge       float32    // ширина зоны преломления, пиксели
	Refraction float32    // насколько край «загибает» содержимое, пиксели
	Magnify    float32    // увеличение подложки: толстое стекло приближает
	Sheen      float32    // диагональный блик
	SheenPos   float32
	Shadow     float32 // внутренняя тень у нижней кромки
	Grain      float32 // зерно материала
	Radius     int     // скругление углов
	RoundAlpha bool    // обрезать углы прозрачностью (для виджетов)
}

// compose — собрать кусок стекла: размытый фон за деталью + преломление
// у краёв + полупрозрачная подмесь цвета + светлая кромка + блик + тень
// + зерно. rect — положение детали в координатах окна.
func (f *glassField) compose(rect glassRect, options glassOptions) *image.RGBA {
	width, height := rect.Width, rect.Height
	if width <= 0 || height <= 0 || len(f.Blurred) == 0 {
		return nil
	}
	out := image.NewRGBA(image.Rect(0, 0, width, height))

	edge := options.Edge
	if edge < 1 {
		edge = 1
	}
	magnify := options.Magnify
	if magnify <= 0 {
		magnify = 1
	}
	inv := 1 / magnify
	scaleF := float32(gridScale)
	centerX := float32(width) / 2
	centerY := float32(height) / 2
	wF, hF := float32(width), float32(height)

	// Веса кромок по строкам и столбцам: сверху ярче всего (свет падает
	// сверху), снизу слабее, по бокам — средне.
	rowTop := make([]float32, height)
	rowBottom := make([]float32, height)
	rowSheen := make([]float32, height)
	colLeft := make([]float32, width)
	colRight := make([]float32, width)
	for y := 0; y < height; y++ {
		rowTop[y] = edgeFalloff(float32(y), edge)
		rowBottom[y] = edgeFalloff(float32(height-1-y), edge) * 0.40
		rowSheen[y] = sheenValue(float32(y)/hF, options)
	}
	for x := 0; x < width; x++ {
		colLeft[x] = edgeFalloff(float32(x), edge) * 0.65
		colRight[x] = edgeFalloff(float32(width-1-x), edge) * 0.65
	}
	grainTable := make([]float32, 4096)
	for i := range grainTable {
		grainTable[i] = (float32(i%97)/97 - 0.5) * 2
	}

	tint := options.Tint
	stride := out.Stride
	for y := 0; y < height; y++ {
		row := out.Pix[y*stride : (y+1)*stride]
		v := float32(y) / hF
		// Подмесь цвета гуще сверху: так светится реальное стекло.
		tintA := options.TintAlpha * (1.30 - 0.55*v)
		if tintA > 1 {
			tintA = 1
		}
		// Внутренняя тень у нижней кромки: её ширина задаётся полем Shadow,
		// а сила — константой. Раньше сила умножалась на ширину, из-за чего
		// низ каждой панели уходил в чёрный.
		shadow := float32(0)
		depth := hF - 1 - float32(y) - glassShadowIndent
		if depth < 0 {
			depth = 0
		}
		if options.Shadow > 0 && depth < options.Shadow {
			shadow = (1 - depth/options.Shadow) * glassShadowStrength
		}
		sheenRow := rowSheen[y]
		for x := 0; x < width; x++ {
			left := float32(x)
			right := wF - 1 - float32(x)
			top := float32(y)
			bottom := hF - 1 - float32(y)
			// Преломление: у самой кромки стекло загибает то, что снаружи.
			nearest := left
			nx, ny := float32(-1), float32(0)
			if right < nearest {
				nearest, nx, ny = right, 1, 0
			}
			if top < nearest {
				nearest, nx, ny = top, 0, -1
			}
			if bottom < nearest {
				nearest, nx, ny = bottom, 0, 1
			}
			bend := edgeFalloff(nearest, edge) * options.Refraction
			// У кромки материала меньше, поэтому стекло там прозрачнее:
			// преломлённое содержимое видно сильнее. Так ведёт себя и
			// настоящее стекло — в торец оно просвечивает почти целиком.
			edgeSee := 1 - 0.45*edgeFalloff(nearest, edge)
			// У кромки стекло толще и работает линзой: оно «фокусирует» фон,
			// поэтому узкая полоска по краю показывает содержимое почти без
			// размытия. Именно она отличает стекло от размытой плашки.
			focus := edgeFalloff(nearest, edge*1.4) * 0.85
			// Свечение кромки: на сгибе стекло собирает свет, поэтому узкая
			// полоса вдоль края всегда ярче середины — это видно даже на
			// ровном фоне, как у настоящего толстого стекла.
			caustic := 1 + 0.42*edgeFalloff(nearest, edge*1.2)

			sx := float32(rect.X) + (float32(x)-centerX)*inv + centerX + nx*bend
			sy := float32(rect.Y) + (float32(y)-centerY)*inv + centerY + ny*bend
			r, gr, b := f.sample(sx/scaleF, sy/scaleF)
			if focus > 0.01 {
				sr, sg, sb := f.sampleFrom(f.Sharp, sx/scaleF, sy/scaleF)
				r = r*(1-focus) + sr*focus
				gr = gr*(1-focus) + sg*focus
				b = b*(1-focus) + sb*focus
			}

			// Полупрозрачная подмесь собственного цвета стекла: в середине
			// плотнее, у кромки — прозрачнее.
			layer := tintA * edgeSee
			r = (r*(1-layer) + tint[0]*layer) * caustic
			gr = (gr*(1-layer) + tint[1]*layer) * caustic
			b = (b*(1-layer) + tint[2]*layer) * caustic

			// Светлая кромка по контуру.
			rim := rowTop[y]
			if value := rowBottom[y]; value > rim {
				rim = value
			}
			if value := colLeft[x]; value > rim {
				rim = value
			}
			if value := colRight[x]; value > rim {
				rim = value
			}
			if rim > 0 {
				rim *= options.Rim
				r += (255 - r) * rim
				gr += (255 - gr) * rim
				b += (255 - b) * rim
			}

			// Диагональный блик: стекло ловит свет полосой.
			if sheenRow > 0 {
				u := float32(x) / wF
				band := u*0.75 + v*0.85
				value := sheenRow * bandValue(band, options)
				r += (255 - r) * value
				gr += (255 - gr) * value
				b += (255 - b) * value
			}

			// Внутренняя тень у нижней кромки — объём.
			if shadow > 0 {
				dark := 1 - shadow
				r *= dark
				gr *= dark
				b *= dark
			}

			// Зерно: чтобы стекло не выглядело пластмассой.
			if options.Grain > 0 {
				noise := grainTable[(x*31+y*17)&4095] * options.Grain
				r += noise
				gr += noise
				b += noise
			}

			alpha := byte(255)
			if options.RoundAlpha {
				alpha = byte(255 * roundCoverage(float32(x)+0.5, float32(y)+0.5,
					wF, hF, float32(options.Radius)))
			}
			offset := x * 4
			row[offset] = byte(clampColor(r))
			row[offset+1] = byte(clampColor(gr))
			row[offset+2] = byte(clampColor(b))
			row[offset+3] = alpha
		}
	}
	return out
}

// edgeFalloff — спад от кромки внутрь: 1 у края, 0 в глубине.
func edgeFalloff(distance float32, edge float32) float32 {
	if distance >= edge {
		return 0
	}
	value := 1 - distance/edge
	return value * value
}

// sheenValue — вертикальный профиль блика.
func sheenValue(v float32, options glassOptions) float32 {
	if options.Sheen <= 0 {
		return 0
	}
	value := 1 - float32(math.Abs(float64(v-options.SheenPos)))/0.42
	if value <= 0 {
		return 0
	}
	return value * value * options.Sheen
}

// bandValue — диагональная полоса блика.
func bandValue(band float32, options glassOptions) float32 {
	value := 1 - float32(math.Abs(float64(band-options.SheenPos)))/0.30
	if value <= 0 {
		return 0
	}
	return value * value
}

// roundCoverage — непрозрачность с мягким скруглением углов.
func roundCoverage(px float32, py float32, w float32, h float32, radius float32) float32 {
	if radius <= 0 {
		return 1
	}
	qx := float32(math.Abs(float64(px-w/2))) - (w/2 - radius)
	qy := float32(math.Abs(float64(py-h/2))) - (h/2 - radius)
	outside := float32(math.Hypot(math.Max(float64(qx), 0), math.Max(float64(qy), 0)))
	distance := outside + float32(math.Min(math.Max(float64(qx), float64(qy)), 0)) - radius
	coverage := 0.5 - distance
	if coverage < 0 {
		return 0
	}
	if coverage > 1 {
		return 1
	}
	return coverage
}

// ─────────────────────────────── готовые наборы ───────────────────────────────
//
// Здесь задаётся «характер» каждого типа деталей. Наборы общие для программы
// и для теста, поэтому проверяется именно то, что работает в лаунчере.

// glassBackgroundColors — цвета фона окна: верх и низ градиента. Один
// источник и для лаунчера, и для теста, чтобы они не расходились.
func glassBackgroundColors() (top [3]float32, bottom [3]float32) {
	return rgbTriple(10, 16, 48), rgbTriple(2, 5, 18)
}

// defaultGlassSpots — пятна света на фоне. Крупные дают общий свет, средние
// и мелкие — структуру: именно по ним видно, что стекло размывает и преломляет
// содержимое. На совсем гладком фоне эффект был бы неразличим.
func defaultGlassSpots(violet [3]float32, teal [3]float32, blue [3]float32,
	light [3]float32) []glassSpot {
	return []glassSpot{
		// Крупные: общий свет и глубина.
		{Color: violet, BaseX: 0.16, BaseY: 0.18, Size: 0.62,
			AmpX: 0.05, AmpY: 0.06, Speed: 0.70, Shift: 0.0, Alpha: 0.21},
		{Color: blue, BaseX: 0.68, BaseY: 0.94, Size: 0.56,
			AmpX: 0.07, AmpY: 0.05, Speed: 0.37, Shift: 2.2, Alpha: 0.25},
		{Color: violet, BaseX: 0.30, BaseY: 0.82, Size: 0.44,
			AmpX: 0.06, AmpY: 0.05, Speed: 0.31, Shift: 3.1, Alpha: 0.20},
		{Color: teal, BaseX: 0.88, BaseY: 0.26, Size: 0.42,
			AmpX: 0.05, AmpY: 0.05, Speed: 0.61, Shift: 1.1, Alpha: 0.21},
		// Средние: структура, которую стекло размывает.
		{Color: light, BaseX: 0.52, BaseY: 0.06, Size: 0.27,
			AmpX: 0.10, AmpY: 0.03, Speed: 0.23, Shift: 0.6, Alpha: 0.20},
		{Color: blue, BaseX: 0.42, BaseY: 0.42, Size: 0.24,
			AmpX: 0.06, AmpY: 0.07, Speed: 0.53, Shift: 1.7, Alpha: 0.21},
		{Color: teal, BaseX: 0.10, BaseY: 0.60, Size: 0.21,
			AmpX: 0.05, AmpY: 0.06, Speed: 0.47, Shift: 2.8, Alpha: 0.20},
		{Color: violet, BaseX: 0.80, BaseY: 0.70, Size: 0.20,
			AmpX: 0.06, AmpY: 0.05, Speed: 0.41, Shift: 4.0, Alpha: 0.21},
		// Мелкие и яркие: блики, их размытие заметнее всего.
		{Color: light, BaseX: 0.62, BaseY: 0.20, Size: 0.12,
			AmpX: 0.07, AmpY: 0.04, Speed: 0.83, Shift: 0.4, Alpha: 0.33},
		{Color: teal, BaseX: 0.24, BaseY: 0.32, Size: 0.10,
			AmpX: 0.06, AmpY: 0.05, Speed: 0.97, Shift: 2.1, Alpha: 0.20},
		{Color: light, BaseX: 0.88, BaseY: 0.52, Size: 0.09,
			AmpX: 0.05, AmpY: 0.06, Speed: 1.13, Shift: 3.4, Alpha: 0.20},
		{Color: violet, BaseX: 0.46, BaseY: 0.74, Size: 0.11,
			AmpX: 0.07, AmpY: 0.04, Speed: 0.71, Shift: 5.2, Alpha: 0.25},
	}
}

// cardGlassOptions — стекло больших панелей: толстое, с широкой зоной
// преломления по краям, поэтому видно и размытие, и «загиб» у кромки.
func cardGlassOptions(tint [3]float32) glassOptions {
	return glassOptions{
		Tint: tint, TintAlpha: 0.60, Rim: 0.74, Edge: 16, Refraction: 48,
		Magnify: 1.02, Sheen: 0.13, SheenPos: 0.18, Shadow: 20, Grain: 3.0,
	}
}

// railGlassOptions — стекло бокового меню: чуть плотнее, оно держит на себе
// кнопки вкладок и должно читаться отдельным слоем.
func railGlassOptions(tint [3]float32) glassOptions {
	options := cardGlassOptions(tint)
	options.TintAlpha = 0.62
	options.Rim = 0.72
	return options
}

// buttonGlassOptions — стекло кнопки. Виды: 0 — обычная, 1 — акцентная,
// 2 — опасная, 3 — вкладка меню.
func buttonGlassOptions(tint [3]float32, kind int) glassOptions {
	options := glassOptions{
		Tint: tint, TintAlpha: 0.58, Rim: 0.68, Edge: 9, Refraction: 22,
		Magnify: 1.035, Sheen: 0.11, SheenPos: 0.32, Shadow: 11, Grain: 3.0,
		Radius: 18, RoundAlpha: true,
	}
	switch kind {
	case 1: // акцентная: плотнее, ярче кромка и блик
		options.TintAlpha = 0.62
		options.Rim = 0.72
		options.Sheen = 0.18
		options.Refraction = 15
	case 2: // опасная
		options.TintAlpha = 0.40
		options.Rim = 0.62
	case 3: // вкладка
		options.TintAlpha = 0.40
		options.Rim = 0.55
		options.Edge = 8
		options.Magnify = 1.02
	}
	return options
}

// activeTabOptions — стекло активной вкладки: фиолетовое.
func activeTabOptions(tint [3]float32) glassOptions {
	options := buttonGlassOptions(tint, 3)
	options.TintAlpha = 0.52
	options.Rim = 0.70
	return options
}

// pillGlassOptions — стекло таблетки статуса.
func pillGlassOptions(tint [3]float32) glassOptions {
	return glassOptions{
		Tint: tint, TintAlpha: 0.60, Rim: 0.70, Edge: 9, Refraction: 16,
		Magnify: 1.02, Sheen: 0.12, SheenPos: 0.34, Shadow: 9, Grain: 2.6,
		Radius: 15, RoundAlpha: true,
	}
}

// trackGlassOptions — стекло жёлоба полосы хода и переключателя.
func trackGlassOptions(tint [3]float32, radius int) glassOptions {
	return glassOptions{
		Tint: tint, TintAlpha: 0.52, Rim: 0.48, Edge: 6, Refraction: 8,
		Magnify: 1, Sheen: 0.05, SheenPos: 0.5, Shadow: 5, Grain: 2.0,
		Radius: radius, RoundAlpha: true,
	}
}

// fieldGlassOptions — стекло полей ввода: плотнее и темнее, чтобы поле
// читалось как вдавленное стекло, а не как плашка.
func fieldGlassOptions(tint [3]float32) glassOptions {
	return glassOptions{
		Tint: tint, TintAlpha: 0.62, Rim: 0.50, Edge: 7, Refraction: 12,
		Magnify: 1, Sheen: 0.06, SheenPos: 0.4, Shadow: 8, Grain: 2.4,
	}
}

// lensGlassOptions — линза логотипа: сильное увеличение и преломление.
func lensGlassOptions(tint [3]float32) glassOptions {
	return glassOptions{
		Tint: tint, TintAlpha: 0.52, Rim: 0.78, Edge: 11, Refraction: 18,
		Magnify: 1.45, Sheen: 0.20, SheenPos: 0.30, Shadow: 10, Grain: 2.2,
		Radius: 14, RoundAlpha: true,
	}
}

// rgbTriple — цвет из компонент: для палитры и для подмеси стекла.
func rgbTriple(r uint8, g uint8, b uint8) [3]float32 {
	return [3]float32{float32(r), float32(g), float32(b)}
}

func clampColor(value float32) float32 {
	if value < 0 {
		return 0
	}
	if value > 255 {
		return 255
	}
	return value
}

func clampInt(value int, low int, high int) int {
	if value < low {
		return low
	}
	if value > high {
		return high
	}
	return value
}
