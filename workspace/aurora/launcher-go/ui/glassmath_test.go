package ui

// Проверки математики стекла. Они работают на любой машине: весь код,
// который здесь проверяется, не зависит от Windows (см. glassmath.go),
// поэтому эффекты проверяются числами и картинкой, а не «на глаз в игре».

import (
	"image"
	"image/png"
	"math"
	"os"
	"testing"
)

// testField — фон, как у настоящего окна: те же пятна, тот же градиент.
func testField(t *testing.T, width int, height int) *glassField {
	t.Helper()
	top, bottom := glassBackgroundColors()
	field := &glassField{
		Top:    top,
		Bottom: bottom,
		Spots: defaultGlassSpots(
			rgbTriple(120, 82, 157), // фиолетовое
			rgbTriple(47, 191, 216), // бирюзовое
			rgbTriple(65, 99, 188),  // синее
			rgbTriple(127, 137, 229)),
	}
	for i := 0; i < framesPerGlassUpdate+1; i++ {
		field.step(width, height)
	}
	if len(field.Blurred) == 0 {
		t.Fatal("фон не отрисовался")
	}
	return field
}

func luminance(r uint8, g uint8, b uint8) float64 {
	return 0.2126*float64(r) + 0.7152*float64(g) + 0.0722*float64(b)
}

// meanLuma — средняя яркость картинки.
func meanLuma(picture *image.RGBA) float64 {
	sum := 0.0
	count := 0
	for y := 0; y < picture.Bounds().Dy(); y++ {
		for x := 0; x < picture.Bounds().Dx(); x++ {
			index := y*picture.Stride + x*4
			sum += luminance(picture.Pix[index], picture.Pix[index+1], picture.Pix[index+2])
			count++
		}
	}
	if count == 0 {
		return 0
	}
	return sum / float64(count)
}

// detail — резкость: средняя кривизна (вторая разность) яркости.
// Наклон мерить нельзя: размытие наклон сохраняет, а кривизну — сглаживает.
func detail(data []float32, width int, height int) float64 {
	luma := func(index int) float64 {
		return 0.2126*float64(data[index]) + 0.7152*float64(data[index+1]) +
			0.0722*float64(data[index+2])
	}
	sum, count := 0.0, 0
	for y := 1; y < height-1; y++ {
		for x := 1; x < width-1; x++ {
			center := luma(3 * (y*width + x))
			left := luma(3 * (y*width + x - 1))
			right := luma(3 * (y*width + x + 1))
			up := luma(3 * ((y-1)*width + x))
			down := luma(3 * ((y+1)*width + x))
			sum += math.Abs(left+right-2*center) + math.Abs(up+down-2*center)
			count += 2
		}
	}
	if count == 0 {
		return 0
	}
	return sum / float64(count)
}

// TestGlassBlurOperator — оператор размытия действительно сглаживает:
// на искусственной ряби (высокие частоты) кривизна должна исчезнуть почти
// полностью. Это честная проверка именно кода размытия.
func TestGlassBlurOperator(t *testing.T) {
	const width, height = 160, 120
	field := &glassField{GridW: width, GridH: height}
	field.Sharp = make([]float32, 3*width*height)
	field.Blurred = make([]float32, 3*width*height)
	for i := 0; i < len(field.Sharp); i++ {
		// Детерминированная рябь: чёт/нечёт дают максимальную кривизну.
		if (i/3)%2 == 0 {
			field.Sharp[i] = 220
		} else {
			field.Sharp[i] = 20
		}
	}
	before := detail(field.Sharp, width, height)
	field.blur()
	after := detail(field.Blurred, width, height)
	if after > before*0.1 {
		t.Fatalf("размытие не сглаживает: было %.3f, стало %.3f", before, after)
	}
	t.Logf("рябь: кривизна %.3f → %.3f", before, after)
}

// TestGlassBlurRadiusInPixels — сила размытия в настоящих пикселях: берём
// резкую ступеньку и мерим, на скольких пикселях яркость переходит от 10 %
// до 90 %. Это и есть то, что глаз читает как «размытие под стеклом».
func TestGlassBlurRadiusInPixels(t *testing.T) {
	const width, height = 200, 60
	field := &glassField{GridW: width, GridH: height}
	field.Sharp = make([]float32, 3*width*height)
	field.Blurred = make([]float32, 3*width*height)
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			value := float32(0)
			if x > width/2 {
				value = 255
			}
			index := 3 * (y*width + x)
			field.Sharp[index], field.Sharp[index+1], field.Sharp[index+2] = value, value, value
		}
	}
	field.blur()
	row := height / 2
	level := func(x int) float64 {
		index := 3 * (row*width + x)
		return float64(field.Blurred[index])
	}
	low, high := level(4), level(width-5)
	edgeLow, edgeHigh := -1.0, -1.0
	for x := 4; x < width-4; x++ {
		value := level(x)
		part := (value - low) / (high - low)
		if edgeLow < 0 && part >= 0.1 {
			edgeLow = float64(x)
		}
		if edgeHigh < 0 && part >= 0.9 {
			edgeHigh = float64(x)
			break
		}
	}
	pixels := (edgeHigh - edgeLow) * gridScale
	if pixels < 18 || pixels > 90 {
		t.Fatalf("размытие не той силы: переход %.0f пикселей", pixels)
	}
	t.Logf("переход размытия: %.0f пикселей", pixels)
}

// TestGlassPanelIsBrighterThanBackdrop — стеклянная панель обязана быть
// светлее того, что за ней: именно так читается «стекло, а не дыра в фоне».
func TestGlassPanelIsBrighterThanBackdrop(t *testing.T) {
	field := testField(t, 960, 640)
	rect := glassRect{X: 200, Y: 80, Width: 700, Height: 480}
	card := field.compose(rect, cardGlassOptions(rgbTriple(44, 58, 124)))
	if card == nil {
		t.Fatal("панель не собралась")
	}

	// Фон ровно под панелью — тот же участок, но без стекла.
	background := image.NewRGBA(image.Rect(0, 0, rect.Width, rect.Height))
	for y := 0; y < rect.Height; y++ {
		for x := 0; x < rect.Width; x++ {
			r, g, b := field.sample(float32(rect.X+x)/gridScale, float32(rect.Y+y)/gridScale)
			index := y*background.Stride + x*4
			background.Pix[index] = byte(clampColor(r))
			background.Pix[index+1] = byte(clampColor(g))
			background.Pix[index+2] = byte(clampColor(b))
			background.Pix[index+3] = 255
		}
	}
	glassLuma, backLuma := meanLuma(card), meanLuma(background)
	if glassLuma <= backLuma+2 {
		t.Fatalf("стекло не светлее фона: стекло %.1f, фон %.1f", glassLuma, backLuma)
	}
	t.Logf("яркость: фон %.1f → стекло %.1f", backLuma, glassLuma)
}

// TestGlassRimIsBright — светлая кромка: верхний ряд панели должен быть
// заметно ярче середины. Без этого стекло выглядит плоской плашкой.
func TestGlassRimIsBright(t *testing.T) {
	field := testField(t, 960, 640)
	card := field.compose(glassRect{X: 200, Y: 80, Width: 700, Height: 480},
		cardGlassOptions(rgbTriple(44, 58, 124)))
	if card == nil {
		t.Fatal("панель не собралась")
	}
	rowLuma := func(y int) float64 {
		sum := 0.0
		for x := 0; x < card.Bounds().Dx(); x++ {
			index := y*card.Stride + x*4
			sum += luminance(card.Pix[index], card.Pix[index+1], card.Pix[index+2])
		}
		return sum / float64(card.Bounds().Dx())
	}
	top, middle := rowLuma(1), rowLuma(card.Bounds().Dy()/2)
	if top <= middle+8 {
		t.Fatalf("кромка не светится: верх %.1f, середина %.1f", top, middle)
	}
	t.Logf("кромка: верх %.1f против середины %.1f", top, middle)
}

// TestGlassRefractionBendsEdges — преломление: вдоль кромки стекло загибает
// то, что за ним, а в середине картинка остаётся прямой.
func TestGlassRefractionBendsEdges(t *testing.T) {
	field := testField(t, 960, 640)
	rect := glassRect{X: 200, Y: 80, Width: 700, Height: 480}
	options := cardGlassOptions(rgbTriple(44, 58, 124))
	bent := field.compose(rect, options)
	options.Refraction = 0
	straight := field.compose(rect, options)
	if bent == nil || straight == nil {
		t.Fatal("панель не собралась")
	}
	// Средняя разница по полосе: у левой кромки и в середине панели.
	band := func(fromX int, toX int) float64 {
		sum, count := 0.0, 0
		for y := 40; y < 440; y += 3 {
			for x := fromX; x < toX; x++ {
				index := y*bent.Stride + x*4
				a := luminance(bent.Pix[index], bent.Pix[index+1], bent.Pix[index+2])
				b := luminance(straight.Pix[index], straight.Pix[index+1],
					straight.Pix[index+2])
				sum += math.Abs(a - b)
				count++
			}
		}
		return sum / float64(count)
	}
	edge, middle := band(0, 10), band(300, 310)
	if edge < 0.4 || edge < middle*3 {
		t.Fatalf("преломление не работает: у кромки %.2f, в середине %.2f", edge, middle)
	}
	t.Logf("преломление: у кромки %.2f, в середине %.2f", edge, middle)
}

// TestGlassBottomIsNotCrushed — внутренняя тень не должна превращать низ
// панели в чёрную полосу: она лишь слегка затемняет край.
func TestGlassBottomIsNotCrushed(t *testing.T) {
	field := testField(t, 960, 640)
	card := field.compose(glassRect{X: 200, Y: 80, Width: 700, Height: 480},
		cardGlassOptions(rgbTriple(44, 58, 124)))
	if card == nil {
		t.Fatal("панель не собралась")
	}
	rowLuma := func(y int) float64 {
		sum := 0.0
		for x := 20; x < card.Bounds().Dx()-20; x++ {
			index := y*card.Stride + x*4
			sum += luminance(card.Pix[index], card.Pix[index+1], card.Pix[index+2])
		}
		return sum / float64(card.Bounds().Dx()-40)
	}
	bottom := rowLuma(card.Bounds().Dy() - 2)  // сама кромка: светлая
	inside := rowLuma(card.Bounds().Dy() - 10) // стекло за кромкой: тень
	middle := rowLuma(card.Bounds().Dy() / 2)
	if bottom < middle*0.25 {
		t.Fatalf("кромка низа провалена в чёрный: %.1f против %.1f", bottom, middle)
	}
	if inside > middle*0.9 {
		t.Fatalf("тени нет: %.1f против середины %.1f", inside, middle)
	}
	t.Logf("кромка низа %.1f, тень за ней %.1f, середина %.1f", bottom, inside, middle)
}

// TestGlassRefractionOnContrast — преломление на контрастном фоне: у кромки
// стекло загибает содержимое так сильно, что разница видна невооружённым
// глазом. На ровном фоне её почти нет — это честно, так ведёт себя и
// настоящее стекло.
func TestGlassRefractionOnContrast(t *testing.T) {
	const width, height = 200, 120
	field := &glassField{GridW: width, GridH: height}
	field.Sharp = make([]float32, 3*width*height)
	field.Blurred = make([]float32, 3*width*height)
	// Диагональный градиент: яркость растёт слева направо.
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			value := float32(x) / float32(width) * 255
			index := 3 * (y*width + x)
			field.Sharp[index], field.Sharp[index+1], field.Sharp[index+2] = value, value, value
		}
	}
	copy(field.Blurred, field.Sharp)
	rect := glassRect{X: 40, Y: 20, Width: 100, Height: 60}
	options := cardGlassOptions(rgbTriple(54, 70, 142))
	bent := field.compose(rect, options)
	options.Refraction = 0
	straight := field.compose(rect, options)
	sum, count := 0.0, 0
	for y := 20; y < 40; y++ {
		for x := 1; x < 8; x++ {
			index := y*bent.Stride + x*4
			a := luminance(bent.Pix[index], bent.Pix[index+1], bent.Pix[index+2])
			b := luminance(straight.Pix[index], straight.Pix[index+1], straight.Pix[index+2])
			sum += math.Abs(a - b)
			count++
		}
	}
	delta := sum / float64(count)
	if delta < 4 {
		t.Fatalf("преломление слабое даже на контрастном фоне: %.2f", delta)
	}
	t.Logf("преломление на контрастном фоне: %.2f", delta)
}

// TestGlassCornersAreTransparent — скруглённые уголки виджетов должны быть
// прозрачными, иначе по углам кнопок будут тёмные квадраты.
func TestGlassCornersAreTransparent(t *testing.T) {
	field := testField(t, 960, 640)
	button := field.compose(glassRect{X: 300, Y: 300, Width: 160, Height: 40},
		buttonGlassOptions(rgbTriple(44, 58, 124), 0))
	if button == nil {
		t.Fatal("кнопка не собралась")
	}
	alphaAt := func(x int, y int) uint8 {
		return button.Pix[y*button.Stride+x*4+3]
	}
	if alphaAt(0, 0) != 0 || alphaAt(159, 39) != 0 {
		t.Fatalf("углы непрозрачные: левый верх %d, правый низ %d", alphaAt(0, 0), alphaAt(159, 39))
	}
	if alphaAt(80, 20) != 255 {
		t.Fatalf("середина прозрачная: %d", alphaAt(80, 20))
	}
}

// TestGlassGrainIsStable — зерно не должно мерцать: два одинаковых вызова
// дают одну и ту же картинку.
func TestGlassGrainIsStable(t *testing.T) {
	field := testField(t, 960, 640)
	options := cardGlassOptions(rgbTriple(44, 58, 124))
	first := field.compose(glassRect{X: 200, Y: 80, Width: 300, Height: 200}, options)
	field.Phase += 0 // фон не двигали
	second := field.compose(glassRect{X: 200, Y: 80, Width: 300, Height: 200}, options)
	for i := 0; i < len(first.Pix); i++ {
		if first.Pix[i] != second.Pix[i] {
			t.Fatalf("зерно плавает: байт %d (%d против %d)", i, first.Pix[i], second.Pix[i])
		}
	}
}

// TestGlassBackgroundNotFlat — фон не должен быть ровной заливкой: пятна
// и градиент обязаны давать перепад яркости по окну.
func TestGlassBackgroundNotFlat(t *testing.T) {
	field := testField(t, 960, 640)
	low, high := 255.0, 0.0
	for y := 0; y < field.GridH; y++ {
		for x := 0; x < field.GridW; x++ {
			index := 3 * (y*field.GridW + x)
			value := luminance(byte(field.Sharp[index]), byte(field.Sharp[index+1]),
				byte(field.Sharp[index+2]))
			if value < low {
				low = value
			}
			if value > high {
				high = value
			}
		}
	}
	if high-low < 12 {
		t.Fatalf("фон почти однотонный: перепад %.1f", high-low)
	}
	t.Logf("перепад яркости фона: %.1f … %.1f", low, high)
}

// TestGlassPreview — рисует макет окна из настоящих стеклянных деталей.
// Включается переменной GLASS_PREVIEW=<путь к png>: картинку можно посмотреть
// и убедиться, что стекло выглядит как стекло.
func TestGlassPreview(t *testing.T) {
	path := os.Getenv("GLASS_PREVIEW")
	if path == "" {
		t.Skip("макет не запрошен: задайте GLASS_PREVIEW=<файл.png>")
	}
	const width, height = 980, 640
	field := testField(t, width, height)

	// Фон: сетка растягивается на окно (в программе это делает GDI).
	window := upscale(field.gridImage(), width, height)

	stamp(window, field.compose(glassRect{X: 16, Y: 74, Width: 176, Height: 500},
		railGlassOptions(rgbTriple(44, 58, 124))), 16, 74)
	stamp(window, field.compose(glassRect{X: 206, Y: 74, Width: 740, Height: 500},
		cardGlassOptions(rgbTriple(44, 58, 124))), 206, 74)
	stamp(window, field.compose(glassRect{X: 16, Y: 14, Width: 46, Height: 46},
		lensGlassOptions(rgbTriple(124, 96, 255))), 16, 14)
	stamp(window, field.compose(glassRect{X: 724, Y: 20, Width: 240, Height: 30},
		pillGlassOptions(rgbTriple(44, 58, 124))), 724, 20)

	// Вкладки: вторая активна — фиолетовое стекло.
	for i := 0; i < 6; i++ {
		options := buttonGlassOptions(rgbTriple(44, 58, 124), 3)
		if i == 1 {
			options = activeTabOptions(rgbTriple(124, 96, 255))
		}
		stamp(window, field.compose(glassRect{X: 24, Y: 86 + i*44, Width: 160, Height: 36},
			options), 24, 86+i*44)
	}
	// Кнопки внизу карточки: акцентная и обычная.
	stamp(window, field.compose(glassRect{X: 226, Y: 460, Width: 150, Height: 38},
		buttonGlassOptions(rgbTriple(124, 96, 255), 1)), 226, 460)
	stamp(window, field.compose(glassRect{X: 388, Y: 460, Width: 150, Height: 38},
		buttonGlassOptions(rgbTriple(44, 58, 124), 0)), 388, 460)
	// Полоса хода.
	stamp(window, field.compose(glassRect{X: 226, Y: 520, Width: 700, Height: 12},
		trackGlassOptions(rgbTriple(30, 40, 92), 5)), 226, 520)

	file, err := os.Create(path)
	if err != nil {
		t.Fatalf("не смог записать макет: %v", err)
	}
	defer file.Close()
	if err := png.Encode(file, window); err != nil {
		t.Fatalf("не смог закодировать макет: %v", err)
	}
	t.Logf("макет записан: %s", path)
}

// upscale — растянуть картинку билинейно (в программе это делает GDI).
func upscale(source *image.RGBA, width int, height int) *image.RGBA {
	out := image.NewRGBA(image.Rect(0, 0, width, height))
	srcW, srcH := source.Bounds().Dx(), source.Bounds().Dy()
	for y := 0; y < height; y++ {
		fy := float64(y) * float64(srcH) / float64(height)
		y0 := int(fy)
		y1 := y0 + 1
		if y1 >= srcH {
			y1 = srcH - 1
		}
		wy := fy - float64(y0)
		for x := 0; x < width; x++ {
			fx := float64(x) * float64(srcW) / float64(width)
			x0 := int(fx)
			x1 := x0 + 1
			if x1 >= srcW {
				x1 = srcW - 1
			}
			wx := fx - float64(x0)
			index := y*out.Stride + x*4
			for c := 0; c < 3; c++ {
				top := float64(source.Pix[y0*source.Stride+x0*4+c])*(1-wx) +
					float64(source.Pix[y0*source.Stride+x1*4+c])*wx
				low := float64(source.Pix[y1*source.Stride+x0*4+c])*(1-wx) +
					float64(source.Pix[y1*source.Stride+x1*4+c])*wx
				out.Pix[index+c] = byte(clampColor(float32(top*(1-wy) + low*wy)))
			}
			out.Pix[index+3] = 255
		}
	}
	return out
}

// stamp — положить деталь на фон с учётом прозрачных уголков.
func stamp(destination *image.RGBA, source *image.RGBA, x int, y int) {
	if source == nil {
		return
	}
	for sy := 0; sy < source.Bounds().Dy(); sy++ {
		for sx := 0; sx < source.Bounds().Dx(); sx++ {
			dx, dy := x+sx, y+sy
			if dx < 0 || dy < 0 || dx >= destination.Bounds().Dx() ||
				dy >= destination.Bounds().Dy() {
				continue
			}
			from := sy*source.Stride + sx*4
			alpha := source.Pix[from+3]
			if alpha == 0 {
				continue
			}
			to := dy*destination.Stride + dx*4
			if alpha == 255 {
				copy(destination.Pix[to:to+4], source.Pix[from:from+4])
				continue
			}
			weight := float32(alpha) / 255
			for c := 0; c < 3; c++ {
				destination.Pix[to+c] = byte(float32(destination.Pix[to+c])*(1-weight) +
					float32(source.Pix[from+c])*weight)
			}
			destination.Pix[to+3] = 255
		}
	}
}
