package core

import (
	"crypto/sha1"
	"crypto/sha512"
	"encoding/hex"
	"encoding/json"
	"errors"
	"hash"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// ProgressListener — ход загрузки: этап и счётчик файлов.
// Методы могут вызываться из нескольких потоков одновременно.
type ProgressListener interface {
	Stage(text string)
	File(name string, done, total int64)
}

// Cancel — флаг отмены: его проверяют все долгие операции.
type Cancel struct {
	flag int32
}

// Cancel выставляет флаг отмены.
func (c *Cancel) Cancel() { atomic.StoreInt32(&c.flag, 1) }

// IsCancelled — просили ли прервать работу.
func (c *Cancel) IsCancelled() bool { return c != nil && atomic.LoadInt32(&c.flag) != 0 }

// ErrCancelled — операция прервана пользователем.
var ErrCancelled = errors.New("операция отменена")

// ErrNoProgress — загрузка не продвигалась слишком долго.
var ErrNoProgress = errors.New("загрузка прервана: нет данных более двух минут")

const (
	// copyBufferSize — размер блока при копировании и подсчёте контрольных сумм.
	// Стандартных 32 КБ мало: на файлах в десятки мегабайт это тысячи лишних
	// системных вызовов, а контрольная сумма считается на каждом запуске.
	copyBufferSize = 1024 * 1024
	// downloadWorkers — сколько файлов качается одновременно. Ресурсы игры —
	// это тысячи мелких файлов, поэтому параллельность важнее размера потока.
	downloadWorkers = 16
	// progressThrottle — как часто можно дёргать интерфейс: чаще незачем,
	// окно Windows всё равно не успевает перерисовываться.
	progressThrottle = 100 * time.Millisecond
	// retryAttempts — сколько раз повторяем оборванную загрузку.
	retryAttempts = 4
)

// Транспорт с пулом соединений. По умолчанию Go держит всего два свободных
// соединения на хост, из-за чего параллельная загрузка ресурсов вырождалась
// в «два файла за раз» и постоянные повторные TLS-рукопожатия. Здесь пул
// рассчитан на параллельную работу, а таймауты не дают зависнуть навсегда.
var httpTransport = &http.Transport{
	Proxy: http.ProxyFromEnvironment,
	DialContext: (&net.Dialer{
		Timeout:   15 * time.Second,
		KeepAlive: 30 * time.Second,
	}).DialContext,
	MaxIdleConns:          512,
	MaxIdleConnsPerHost:   64,
	IdleConnTimeout:       90 * time.Second,
	TLSHandshakeTimeout:   15 * time.Second,
	ExpectContinueTimeout: time.Second,
	ResponseHeaderTimeout: 30 * time.Second,
	ForceAttemptHTTP2:     true,
}

var httpClient = &http.Client{Transport: httpTransport}

// isRetryableStatus — код ответа, после которого имеет смысл повторить запрос.
func isRetryableStatus(code int) bool {
	return code == http.StatusTooManyRequests || code == http.StatusRequestTimeout ||
		code >= http.StatusInternalServerError
}

// do выполняет запрос и повторяет его при обрыве сети или временной ошибке
// сервера. Повторяются только запросы без тела: повтор отправки формы входа
// мог бы создать лишнюю сессию.
func do(request *http.Request, cancel *Cancel) (*http.Response, error) {
	repeatable := request.Method == http.MethodGet || request.Method == http.MethodHead
	var lastError error
	for attempt := 1; attempt <= retryAttempts; attempt++ {
		if cancel.IsCancelled() {
			return nil, ErrCancelled
		}
		response, err := httpClient.Do(request)
		if err == nil {
			if !repeatable || !isRetryableStatus(response.StatusCode) {
				return response, nil
			}
			// Тело нужно закрыть и выбросить, иначе соединение не вернётся в пул.
			_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 64*1024))
			response.Body.Close()
			lastError = &Errorf{Message: "сервер ответил " + strconv.Itoa(response.StatusCode)}
		} else {
			lastError = err
			if !repeatable {
				return nil, err
			}
		}
		if attempt == retryAttempts {
			break
		}
		if !sleepWithCancel(time.Duration(attempt)*500*time.Millisecond, cancel) {
			return nil, ErrCancelled
		}
	}
	return nil, lastError
}

// sleepWithCancel — пауза между повторами, которая прерывается по отмене.
func sleepWithCancel(duration time.Duration, cancel *Cancel) bool {
	if cancel == nil {
		time.Sleep(duration)
		return true
	}
	deadline := time.Now().Add(duration)
	for time.Now().Before(deadline) {
		if cancel.IsCancelled() {
			return false
		}
		time.Sleep(50 * time.Millisecond)
	}
	return true
}

// GetJSON запрашивает документ и разбирает его как JSON.
func GetJSON(rawURL string) (any, error) {
	request, err := http.NewRequest(http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("User-Agent", "AetherGlassLauncher/1.0")
	response, err := do(request, nil)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, &Errorf{Message: "сервер ответил " + strconv.Itoa(response.StatusCode) +
			" (" + response.Status + ") на " + rawURL}
	}
	var result any
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return nil, err
	}
	return result, nil
}

// PostForm отправляет форму и разбирает JSON-ответ.
func PostForm(rawURL string, form url.Values) (map[string]any, error) {
	response, err := httpClient.PostForm(rawURL, form)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	return decodeMap(response)
}

// PostJSON отправляет JSON и разбирает ответ. bearer может быть пустым.
func PostJSON(rawURL string, body any, bearer string) (map[string]any, error) {
	encoded, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	request, err := http.NewRequest(http.MethodPost, rawURL, strings.NewReader(string(encoded)))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json")
	if bearer != "" {
		request.Header.Set("Authorization", "Bearer "+bearer)
	}
	response, err := do(request, nil)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	return decodeMap(response)
}

func decodeMap(response *http.Response) (map[string]any, error) {
	var result any
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		if response.StatusCode != http.StatusOK {
			return nil, &Errorf{Message: "сервер ответил " + strconv.Itoa(response.StatusCode)}
		}
		return nil, err
	}
	object, ok := result.(map[string]any)
	if !ok {
		return nil, &Errorf{Message: "ожидался JSON-объект, пришёл " + strconv.Itoa(response.StatusCode)}
	}
	return object, nil
}

// DownloadJob — один файл, который нужно скачать. Контрольные суммы
// необязательны, но если указаны — проверяются на лету, без повторного чтения.
type DownloadJob struct {
	URL    string
	Target string
	Size   int64
	SHA1   string
	SHA512 string
	// Name — человеческое имя файла для строки прогресса; пусто — берётся имя пути.
	Name string
}

// Download скачивает файл, проверяет размер и SHA-1 и только потом
// переименовывает его в целевой путь: повреждённый файл не остаётся на диске.
func Download(rawURL string, target string, size int64, sha1sum string,
	progress ProgressListener, cancel *Cancel) error {
	return downloadFile(DownloadJob{URL: rawURL, Target: target, Size: size, SHA1: sha1sum},
		progress, cancel)
}

// countingProgress — считает готовые файлы и передаёт счётчик общему слушателю.
type countingProgress struct {
	listener ProgressListener
	total    int64
}

func (p *countingProgress) Stage(text string) {
	if p.listener != nil {
		p.listener.Stage(text)
	}
}

func (p *countingProgress) File(name string, done int64, total int64) {
	if p.listener != nil {
		p.listener.File(name, done, p.total)
	}
}

// DownloadAll качает набор файлов параллельно, сохраняя порядок: элемент
// результата соответствует элементу задания. Ошибка возвращается первая,
// но остальные потоки аккуратно останавливаются.
func DownloadAll(jobs []DownloadJob, workers int, progress ProgressListener,
	cancel *Cancel) error {
	if len(jobs) == 0 {
		return nil
	}
	jobs = dedupeJobs(jobs)
	if workers <= 0 {
		workers = downloadWorkers
	}
	if workers > len(jobs) {
		workers = len(jobs)
	}
	queue := make(chan int)
	var wait sync.WaitGroup
	var mutex sync.Mutex
	var failure error
	done := int64(0)
	report := func(name string) {
		if progress != nil {
			progress.File(name, atomic.LoadInt64(&done), int64(len(jobs)))
		}
	}
	for worker := 0; worker < workers; worker++ {
		wait.Add(1)
		go func() {
			defer wait.Done()
			for position := range queue {
				job := jobs[position]
				mutex.Lock()
				broken := failure != nil
				mutex.Unlock()
				if broken || cancel.IsCancelled() {
					continue
				}
				// Прогресс внутрь не передаём: на десятках параллельных
				// загрузок байтовые счётчики перемешались бы. Здесь важен
				// счётчик готовых файлов, а не байты одного из них.
				err := downloadFile(job, nil, cancel)
				atomic.AddInt64(&done, 1)
				report(progressName(job))
				if err != nil {
					mutex.Lock()
					if failure == nil {
						failure = err
					}
					mutex.Unlock()
				}
			}
		}()
	}
	for position := range jobs {
		if cancel.IsCancelled() {
			break
		}
		mutex.Lock()
		broken := failure != nil
		mutex.Unlock()
		if broken {
			break
		}
		queue <- position
	}
	close(queue)
	wait.Wait()
	if cancel.IsCancelled() {
		return ErrCancelled
	}
	mutex.Lock()
	defer mutex.Unlock()
	return failure
}

// progressName — имя файла для строки прогресса.
func progressName(job DownloadJob) string {
	if job.Name != "" {
		return job.Name
	}
	if job.Target == "" {
		return job.URL
	}
	return filepath.Base(job.Target)
}

// dedupeJobs убирает повторы: в ресурсах игры одна и та же контрольная сумма
// часто встречается под разными именами, а качать один файл дважды незачем.
func dedupeJobs(jobs []DownloadJob) []DownloadJob {
	seen := make(map[string]bool, len(jobs))
	result := make([]DownloadJob, 0, len(jobs))
	for _, job := range jobs {
		key := job.Target
		if len(key) > 0 {
			if seen[key] {
				continue
			}
			seen[key] = true
		}
		result = append(result, job)
	}
	return result
}

// downloadFile — сама загрузка: во временный файл, с проверкой на лету.
func downloadFile(job DownloadJob, progress ProgressListener, cancel *Cancel) error {
	if job.Target == "" {
		return &Errorf{Message: "не задан путь для загрузки"}
	}
	if job.URL == "" {
		return &Errorf{Message: "не задан адрес для загрузки " + job.Target}
	}
	if FileMatches(job.Target, job.Size, job.SHA1) {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(job.Target), 0o755); err != nil {
		return err
	}
	request, err := http.NewRequest(http.MethodGet, job.URL, nil)
	if err != nil {
		return err
	}
	request.Header.Set("User-Agent", "AetherGlassLauncher/1.0")
	response, err := do(request, cancel)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return &Errorf{Message: "не удалось скачать (" + strconv.Itoa(response.StatusCode) +
			"): " + job.URL}
	}
	temp, err := os.CreateTemp(filepath.Dir(job.Target), ".aetherglass-*.part")
	if err != nil {
		return err
	}
	tempName := temp.Name()
	defer os.Remove(tempName)

	total := response.ContentLength
	if total <= 0 {
		total = job.Size
	}
	reader := &progressReader{reader: response.Body, name: filepath.Base(job.Target),
		total: total, progress: progress, cancel: cancel}

	// Контрольные суммы считаются прямо во время записи: второй проход по
	// файлу (а это повторное чтение десятков мегабайт) больше не нужен.
	writers := []io.Writer{temp}
	var sha1Hash, sha512Hash hash.Hash
	if job.SHA1 != "" {
		sha1Hash = sha1.New()
		writers = append(writers, sha1Hash)
	}
	if job.SHA512 != "" {
		sha512Hash = sha512.New()
		writers = append(writers, sha512Hash)
	}
	written, err := io.CopyBuffer(io.MultiWriter(writers...), reader,
		make([]byte, copyBufferSize))
	if err != nil {
		temp.Close()
		return err
	}
	if err := temp.Close(); err != nil {
		return err
	}
	if job.SHA1 != "" {
		if !strings.EqualFold(hex.EncodeToString(sha1Hash.Sum(nil)), job.SHA1) {
			return &Errorf{Message: "файл повреждён (не совпал SHA-1): " + job.URL}
		}
	}
	if job.SHA512 != "" {
		if !strings.EqualFold(hex.EncodeToString(sha512Hash.Sum(nil)), job.SHA512) {
			return &Errorf{Message: "файл повреждён (не совпал SHA-512): " + job.URL}
		}
	}
	if job.Size > 0 && written != job.Size {
		return &Errorf{Message: "размер файла не совпал с ожидаемым: " + job.URL}
	}
	return os.Rename(tempName, job.Target)
}

// FileMatches — файл уже скачан и не повреждён: совпали размер и SHA-1.
func FileMatches(path string, size int64, sha1sum string) bool {
	info, err := os.Stat(path)
	if err != nil || !info.Mode().IsRegular() {
		return false
	}
	if size > 0 && info.Size() != size {
		return false
	}
	if sha1sum == "" {
		return size <= 0 || info.Size() == size
	}
	actual, err := SHA1File(path)
	return err == nil && strings.EqualFold(actual, sha1sum)
}

// SHA1File считает SHA-1 файла.
func SHA1File(path string) (string, error) {
	return hashFile(path, sha1.New())
}

// SHA512File считает SHA-512 файла: по нему проверяются моды из манифеста.
func SHA512File(path string) (string, error) {
	return hashFile(path, sha512.New())
}

// hashFile считает контрольную сумму файла большими блоками.
func hashFile(path string, digest hash.Hash) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	if _, err := io.CopyBuffer(digest, file, make([]byte, copyBufferSize)); err != nil {
		return "", err
	}
	return hex.EncodeToString(digest.Sum(nil)), nil
}

type progressReader struct {
	reader   io.Reader
	name     string
	total    int64
	done     int64
	reported int64
	last     time.Time
	lastTick time.Time
	progress ProgressListener
	cancel   *Cancel
}

func (p *progressReader) Read(buffer []byte) (int, error) {
	if p.cancel.IsCancelled() {
		return 0, ErrCancelled
	}
	now := time.Now()
	if p.last.IsZero() {
		p.last = now
		p.lastTick = now
	} else if now.Sub(p.last) > 2*time.Minute {
		return 0, ErrNoProgress
	}
	count, err := p.reader.Read(buffer)
	now = time.Now()
	if count > 0 {
		p.done += int64(count)
		p.last = now
		// Интерфейс дёргаем не чаще раза в progressThrottle: иначе на десятках
		// параллельных загрузок окно только и делало бы, что перерисовывалось.
		if p.progress != nil && now.Sub(p.lastTick) >= progressThrottle &&
			(p.total <= 0 || p.done-p.reported > 256*1024 || p.done == p.total) {
			p.lastTick = now
			p.reported = p.done
			p.progress.File(p.name, p.done, p.total)
		}
	}
	return count, err
}
