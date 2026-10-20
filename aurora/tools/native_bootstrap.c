/*
 * AetherGlass Native Windows Bootstrapper (x86_64-w64-mingw32-gcc)
 *
 * 1. Не требует предустановленной в реестре Java:
 *    - Ищет любую подходящую 64-битную Java (17..25+, с приоритетом 21+) во всех
 *      возможных местах: рядом с .exe, в %LOCALAPPDATA%\AetherGlass\runtime,
 *      %USERPROFILE%\.aetherglass\runtime, %JAVA_HOME%, %PATH%, Program Files,
 *      .jdks, а также во встроенных рантаймах Minecraft Launcher, TLauncher,
 *      Legacy Launcher, Prism, Modrinth, CurseForge, LunarClient и в реестре Windows.
 *    - Если на чистом ПК вообще нет Java 17+, НЕ выдаёт ошибку «JDK 21 not found»,
 *      а показывает нативное окно подготовки и автоматически скачивает портативную
 *      OpenJDK 21 (Temurin JRE 21 x64) в %LOCALAPPDATA%\AetherGlass\runtime\jdk-21.
 * 2. Запускает встроенный в конец этого .exe (или лежащий рядом) JAR-архив.
 */

#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

#ifndef HEAP_MAX_MB
#define HEAP_MAX_MB 512
#endif

#ifndef PRODUCT_TITLE_W
#define PRODUCT_TITLE_W L"AetherGlass"
#endif

static wchar_t g_bestJavaw[MAX_PATH * 2] = {0};
static int g_bestMajor = 0;

/* Читает мажорную версию из файла <home>\release (JAVA_VERSION="21.0.5") без запуска процесса */
static int read_release_version(const wchar_t *javaHome) {
    wchar_t relPath[MAX_PATH * 2];
    _snwprintf(relPath, MAX_PATH * 2 - 1, L"%s\\release", javaHome);
    HANDLE hFile = CreateFileW(relPath, GENERIC_READ, FILE_SHARE_READ, NULL,
                               OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
        return -1;
    }
    char buf[2048];
    DWORD readBytes = 0;
    BOOL ok = ReadFile(hFile, buf, sizeof(buf) - 1, &readBytes, NULL);
    CloseHandle(hFile);
    if (!ok || readBytes == 0) {
        return -1;
    }
    buf[readBytes] = '\0';
    const char *p = strstr(buf, "JAVA_VERSION=\"");
    if (!p) {
        return -1;
    }
    p += strlen("JAVA_VERSION=\"");
    int major = atoi(p);
    if (major == 1 && (p[1] == '.' || p[1] == '_')) {
        major = atoi(p + 2);
    }
    return major > 0 ? major : -1;
}

/* Проверяет версию через скрытый запуск java.exe -version (для симлинков из PATH) */
static int probe_java_exe_version(const wchar_t *javawPath) {
    wchar_t javaExe[MAX_PATH * 2];
    wcsncpy(javaExe, javawPath, MAX_PATH * 2 - 1);
    size_t len = wcslen(javaExe);
    if (len > 9 && _wcsicmp(javaExe + len - 9, L"javaw.exe") == 0) {
        wcscpy(javaExe + len - 9, L"java.exe");
    }
    if (GetFileAttributesW(javaExe) == INVALID_FILE_ATTRIBUTES) {
        wcsncpy(javaExe, javawPath, MAX_PATH * 2 - 1);
    }

    SECURITY_ATTRIBUTES sa;
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = TRUE;
    sa.lpSecurityDescriptor = NULL;

    HANDLE hRead = NULL, hWrite = NULL;
    if (!CreatePipe(&hRead, &hWrite, &sa, 0)) {
        return -1;
    }
    SetHandleInformation(hRead, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = hWrite;
    si.hStdError = hWrite;
    ZeroMemory(&pi, sizeof(pi));

    wchar_t cmd[MAX_PATH * 2 + 32];
    _snwprintf(cmd, MAX_PATH * 2 + 31, L"\"%s\" -version", javaExe);

    if (!CreateProcessW(NULL, cmd, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(hRead);
        CloseHandle(hWrite);
        return -1;
    }
    CloseHandle(hWrite);

    char out[2048];
    DWORD total = 0, n = 0;
    while (total < sizeof(out) - 1 && ReadFile(hRead, out + total, (DWORD)(sizeof(out) - 1 - total), &n, NULL) && n > 0) {
        total += n;
    }
    out[total] = '\0';
    WaitForSingleObject(pi.hProcess, 3000);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    CloseHandle(hRead);

    /* Ищем кавычки вида version "21.0.5" или "17.0.10" */
    const char *q = strchr(out, '"');
    if (!q) {
        return -1;
    }
    q++;
    int major = atoi(q);
    if (major == 1 && (q[1] == '.' || q[1] == '_')) {
        major = atoi(q + 2);
    }
    return major > 0 ? major : -1;
}

/* Проверяет конкретный каталог Java Home (ожидает <home>\bin\javaw.exe) */
static void consider_java_home(const wchar_t *homeDir) {
    if (!homeDir || !homeDir[0]) return;
    wchar_t javaw[MAX_PATH * 2];
    _snwprintf(javaw, MAX_PATH * 2 - 1, L"%s\\bin\\javaw.exe", homeDir);
    if (GetFileAttributesW(javaw) == INVALID_FILE_ATTRIBUTES) {
        _snwprintf(javaw, MAX_PATH * 2 - 1, L"%s\\bin\\java.exe", homeDir);
        if (GetFileAttributesW(javaw) == INVALID_FILE_ATTRIBUTES) {
            return;
        }
    }
    int major = read_release_version(homeDir);
    if (major < 0) {
        major = probe_java_exe_version(javaw);
    }
    if (major >= 17 && major > g_bestMajor) {
        g_bestMajor = major;
        wcsncpy(g_bestJavaw, javaw, MAX_PATH * 2 - 1);
    }
}

/* Проверяет конкретный путь к javaw.exe */
static void consider_javaw_file(const wchar_t *javawPath) {
    if (!javawPath || !javawPath[0]) return;
    if (GetFileAttributesW(javawPath) == INVALID_FILE_ATTRIBUTES) return;

    /* Пробуем подняться на 2 уровня вверх (<home>\bin\javaw.exe -> <home>) */
    wchar_t home[MAX_PATH * 2];
    wcsncpy(home, javawPath, MAX_PATH * 2 - 1);
    wchar_t *lastSlash = wcsrchr(home, L'\\');
    int major = -1;
    if (lastSlash) {
        *lastSlash = L'\0';
        wchar_t *prevSlash = wcsrchr(home, L'\\');
        if (prevSlash) {
            *prevSlash = L'\0';
            major = read_release_version(home);
        }
    }
    if (major < 0) {
        major = probe_java_exe_version(javawPath);
    }
    if (major >= 17 && major > g_bestMajor) {
        g_bestMajor = major;
        wcsncpy(g_bestJavaw, javawPath, MAX_PATH * 2 - 1);
    }
}

/* Рекурсивно обходит подкаталоги до глубины maxDepth в поиске <dir>\bin\javaw.exe */
static void scan_directory_tree(const wchar_t *rootDir, int maxDepth) {
    if (!rootDir || !rootDir[0] || maxDepth < 0 || g_bestMajor >= 21) return;
    if (GetFileAttributesW(rootDir) == INVALID_FILE_ATTRIBUTES) return;

    consider_java_home(rootDir);
    if (maxDepth == 0 || g_bestMajor >= 21) return;

    wchar_t pattern[MAX_PATH * 2];
    _snwprintf(pattern, MAX_PATH * 2 - 1, L"%s\\*", rootDir);
    WIN32_FIND_DATAW fd;
    HANDLE hFind = FindFirstFileW(pattern, &fd);
    if (hFind == INVALID_HANDLE_VALUE) return;

    do {
        if ((fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
            if (wcscmp(fd.cFileName, L".") != 0 && wcscmp(fd.cFileName, L"..") != 0) {
                wchar_t sub[MAX_PATH * 2];
                _snwprintf(sub, MAX_PATH * 2 - 1, L"%s\\%s", rootDir, fd.cFileName);
                scan_directory_tree(sub, maxDepth - 1);
            }
        }
    } while (FindNextFileW(hFind, &fd) && g_bestMajor < 21);

    FindClose(hFind);
}

/* Поиск в ветках реестра Windows */
static void scan_registry_key(HKEY rootKey, const wchar_t *subKey) {
    if (g_bestMajor >= 21) return;
    HKEY hKey;
    if (RegOpenKeyExW(rootKey, subKey, 0, KEY_READ | KEY_WOW64_64KEY, &hKey) != ERROR_SUCCESS) {
        return;
    }
    wchar_t verName[256];
    DWORD idx = 0, nameLen = 256;
    while (RegEnumKeyExW(hKey, idx++, verName, &nameLen, NULL, NULL, NULL, NULL) == ERROR_SUCCESS) {
        wchar_t childPath[512];
        _snwprintf(childPath, 511, L"%s\\%s", subKey, verName);
        HKEY hChild;
        if (RegOpenKeyExW(rootKey, childPath, 0, KEY_READ | KEY_WOW64_64KEY, &hChild) == ERROR_SUCCESS) {
            wchar_t javaHome[MAX_PATH * 2];
            DWORD cb = sizeof(javaHome), type = 0;
            if (RegQueryValueExW(hChild, L"JavaHome", NULL, &type, (LPBYTE)javaHome, &cb) == ERROR_SUCCESS) {
                consider_java_home(javaHome);
            }
            RegCloseKey(hChild);
        }
        /* Также проверяем вложенный Hotspot\MSI (Eclipse Adoptium / Microsoft JDK) */
        _snwprintf(childPath, 511, L"%s\\%s\\Hotspot\\MSI", subKey, verName);
        if (RegOpenKeyExW(rootKey, childPath, 0, KEY_READ | KEY_WOW64_64KEY, &hChild) == ERROR_SUCCESS) {
            wchar_t installDir[MAX_PATH * 2];
            DWORD cb = sizeof(installDir), type = 0;
            if (RegQueryValueExW(hChild, L"Path", NULL, &type, (LPBYTE)installDir, &cb) == ERROR_SUCCESS) {
                size_t l = wcslen(installDir);
                if (l > 0 && installDir[l - 1] == L'\\') installDir[l - 1] = L'\0';
                consider_java_home(installDir);
            }
            RegCloseKey(hChild);
        }
        nameLen = 256;
    }
    RegCloseKey(hKey);
}

/* Поиск по всем элементам переменной среды PATH */
static void scan_env_path(void) {
    if (g_bestMajor >= 21) return;
    DWORD needed = GetEnvironmentVariableW(L"PATH", NULL, 0);
    if (needed == 0) return;
    wchar_t *pathBuf = (wchar_t *)calloc(needed + 2, sizeof(wchar_t));
    if (!pathBuf) return;
    GetEnvironmentVariableW(L"PATH", pathBuf, needed + 1);

    wchar_t *cur = pathBuf;
    while (*cur && g_bestMajor < 21) {
        wchar_t *semi = wcschr(cur, L';');
        if (semi) {
            *semi = L'\0';
        }
        wchar_t *tok = cur;
        while (*tok == L' ' || *tok == L'"') tok++;
        size_t len = wcslen(tok);
        while (len > 0 && (tok[len - 1] == L'\\' || tok[len - 1] == L'"' || tok[len - 1] == L' ')) {
            tok[--len] = L'\0';
        }
        if (len > 0) {
            wchar_t candidate[MAX_PATH * 2];
            _snwprintf(candidate, MAX_PATH * 2 - 1, L"%s\\javaw.exe", tok);
            consider_javaw_file(candidate);
        }
        if (!semi) break;
        cur = semi + 1;
    }
    free(pathBuf);
}

static void find_all_java(const wchar_t *exeDir) {
    wchar_t path[MAX_PATH * 2];
    wchar_t localAppData[MAX_PATH] = {0};
    wchar_t appData[MAX_PATH] = {0};
    wchar_t userProfile[MAX_PATH] = {0};
    wchar_t progFiles[MAX_PATH] = {0};
    wchar_t progFilesX86[MAX_PATH] = {0};

    GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, MAX_PATH);
    GetEnvironmentVariableW(L"APPDATA", appData, MAX_PATH);
    GetEnvironmentVariableW(L"USERPROFILE", userProfile, MAX_PATH);
    GetEnvironmentVariableW(L"ProgramFiles", progFiles, MAX_PATH);
    GetEnvironmentVariableW(L"ProgramFiles(x86)", progFilesX86, MAX_PATH);

    /* 1. Рядом с самим .exe и в каталогах AetherGlass */
    _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\runtime", exeDir);
    scan_directory_tree(path, 3);

    if (localAppData[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\AetherGlass\\runtime", localAppData);
        scan_directory_tree(path, 3);
    }
    if (appData[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\AetherGlass\\runtime", appData);
        scan_directory_tree(path, 3);
    }
    if (userProfile[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\.aetherglass\\runtime", userProfile);
        scan_directory_tree(path, 4);
    }

    /* 2. JAVA_HOME и JDK_HOME */
    wchar_t javaHomeEnv[MAX_PATH] = {0};
    if (GetEnvironmentVariableW(L"JAVA_HOME", javaHomeEnv, MAX_PATH) > 0) {
        consider_java_home(javaHomeEnv);
    }
    if (GetEnvironmentVariableW(L"JDK_HOME", javaHomeEnv, MAX_PATH) > 0) {
        consider_java_home(javaHomeEnv);
    }

    /* 3. Стандартные каталоги установки Java в Program Files и профиле пользователя */
    const wchar_t *pfSubdirs[] = {
        L"Java", L"Eclipse Adoptium", L"AdoptOpenJDK", L"Microsoft",
        L"BellSoft", L"Zulu", L"Amazon Corretto", L"GraalVM", L"Semeru", L"Oracle\\Java"
    };
    if (progFiles[0]) {
        for (size_t i = 0; i < sizeof(pfSubdirs) / sizeof(pfSubdirs[0]); i++) {
            _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\%s", progFiles, pfSubdirs[i]);
            scan_directory_tree(path, 2);
        }
    }
    if (localAppData[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\Programs\\Eclipse Adoptium", localAppData);
        scan_directory_tree(path, 2);
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\Programs\\Java", localAppData);
        scan_directory_tree(path, 2);
    }
    if (userProfile[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\.jdks", userProfile);
        scan_directory_tree(path, 2);
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\.gradle\\jdks", userProfile);
        scan_directory_tree(path, 3);
    }

    /* 4. Встроенные рантаймы официального Minecraft Launcher, TLauncher, Prism, Modrinth, CurseForge, Lunar */
    if (localAppData[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1,
                   L"%s\\Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime",
                   localAppData);
        scan_directory_tree(path, 4);
    }
    if (progFilesX86[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\Minecraft Launcher\\runtime", progFilesX86);
        scan_directory_tree(path, 4);
    }
    if (appData[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\.minecraft\\runtime", appData);
        scan_directory_tree(path, 4);
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\.tlauncher\\jvms", appData);
        scan_directory_tree(path, 3);
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\PrismLauncher\\java", appData);
        scan_directory_tree(path, 3);
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\ModrinthApp\\meta\\java_versions", appData);
        scan_directory_tree(path, 3);
    }
    if (userProfile[0]) {
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\curseforge\\minecraft\\Install\\runtime", userProfile);
        scan_directory_tree(path, 4);
        _snwprintf(path, MAX_PATH * 2 - 1, L"%s\\.lunarclient\\jre", userProfile);
        scan_directory_tree(path, 4);
    }

    /* 5. Реестр Windows */
    const wchar_t *regKeys[] = {
        L"SOFTWARE\\JavaSoft\\JDK",
        L"SOFTWARE\\JavaSoft\\JRE",
        L"SOFTWARE\\JavaSoft\\Java Runtime Environment",
        L"SOFTWARE\\JavaSoft\\Java Development Kit",
        L"SOFTWARE\\Eclipse Adoptium\\JDK",
        L"SOFTWARE\\Eclipse Adoptium\\JRE",
        L"SOFTWARE\\Eclipse Foundation\\JDK",
        L"SOFTWARE\\AdoptOpenJDK\\JDK",
        L"SOFTWARE\\AdoptOpenJDK\\JRE",
        L"SOFTWARE\\Microsoft\\JDK",
        L"SOFTWARE\\BellSoft\\Liberica",
        L"SOFTWARE\\Azul Systems\\Zulu"
    };
    for (size_t i = 0; i < sizeof(regKeys) / sizeof(regKeys[0]); i++) {
        scan_registry_key(HKEY_LOCAL_MACHINE, regKeys[i]);
        scan_registry_key(HKEY_CURRENT_USER, regKeys[i]);
    }

    /* 6. Переменная среды PATH */
    scan_env_path();
}

/* Нативное мини-окно прогресса при автозагрузке портативной Java 21 (если на ПК вообще нет Java) */
static HWND create_status_window(HINSTANCE hInst) {
    HWND hwnd = CreateWindowExW(
        WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
        L"STATIC",
        L"\n   AetherGlass — Первичная подготовка среды запуска...\n   Подождите 15–30 сек., загружается портативный рантайм.",
        WS_POPUP | WS_VISIBLE | WS_BORDER | SS_LEFT,
        (GetSystemMetrics(SM_CXSCREEN) - 460) / 2,
        (GetSystemMetrics(SM_CYSCREEN) - 86) / 2,
        460, 86,
        NULL, NULL, hInst, NULL
    );
    if (hwnd) {
        HFONT hFont = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
        SendMessageW(hwnd, WM_SETFONT, (WPARAM)hFont, TRUE);
        UpdateWindow(hwnd);
    }
    return hwnd;
}

/* Автоматическое скачивание и распаковка портативной OpenJDK 21 в %LOCALAPPDATA%\AetherGlass\runtime\jdk-21 */
static int auto_provision_portable_jre21(HINSTANCE hInst) {
    wchar_t localAppData[MAX_PATH] = {0};
    if (GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, MAX_PATH) == 0) {
        GetTempPathW(MAX_PATH, localAppData);
    }

    wchar_t runtimeRoot[MAX_PATH * 2];
    _snwprintf(runtimeRoot, MAX_PATH * 2 - 1, L"%s\\AetherGlass\\runtime", localAppData);

    HWND statusWnd = create_status_window(hInst);

    /* Используем встроенные в Windows 10/11 curl.exe + tar.exe (или PowerShell с несколькими зеркалами) */
    wchar_t psCmd[2560];
    _snwprintf(
        psCmd, 2559,
        L"powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \""
        L"$ErrorActionPreference='Stop'; "
        L"$rt='%s'; $zip=Join-Path $env:TEMP 'aetherglass_jre21.zip'; "
        L"New-Item -ItemType Directory -Force -Path $rt | Out-Null; "
        L"$urls=@("
        L"'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk',"
        L"'https://download.bell-sw.com/java/21.0.5+11/bellsoft-jre21.0.5+11-windows-amd64.zip',"
        L"'https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.zip'"
        L"); "
        L"foreach ($u in $urls) { "
        L"  try { "
        L"    if (Get-Command curl.exe -ErrorAction SilentlyContinue) { & curl.exe -L -f -s -o $zip $u } "
        L"    else { Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $zip }; "
        L"    if ((Test-Path $zip) -and ((Get-Item $zip).Length -gt 10000000)) { break } "
        L"  } catch {} "
        L"}; "
        L"if (Get-Command tar.exe -ErrorAction SilentlyContinue) { & tar.exe -xf $zip -C $rt } "
        L"else { Expand-Archive -Path $zip -DestinationPath $rt -Force }; "
        L"Remove-Item $zip -Force -ErrorAction SilentlyContinue\"",
        runtimeRoot
    );

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    ZeroMemory(&pi, sizeof(pi));

    if (CreateProcessW(NULL, psCmd, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        /* Крутим цикл сообщений окна, пока идёт скачивание */
        while (WaitForSingleObject(pi.hProcess, 50) == WAIT_TIMEOUT) {
            MSG msg;
            while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) {
                TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }
        }
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
    }

    if (statusWnd) {
        DestroyWindow(statusWnd);
    }

    scan_directory_tree(runtimeRoot, 3);
    return g_bestMajor >= 17;
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    (void)hPrevInstance;
    (void)nCmdShow;

    wchar_t selfExe[MAX_PATH * 2] = {0};
    GetModuleFileNameW(NULL, selfExe, MAX_PATH * 2 - 1);

    wchar_t exeDir[MAX_PATH * 2] = {0};
    wcsncpy(exeDir, selfExe, MAX_PATH * 2 - 1);
    wchar_t *slash = wcsrchr(exeDir, L'\\');
    if (slash) {
        *slash = L'\0';
    }

    /* 1. Ищем любую установленную Java 17..25+ (включая рантаймы лаунчеров и систему) */
    find_all_java(exeDir);

    /* 2. Если Java 17+ вообще нет на ПК — автоматически скачиваем портативный JRE 21 */
    if (g_bestMajor < 17 || !g_bestJavaw[0]) {
        auto_provision_portable_jre21(hInstance);
    }

    if (!g_bestJavaw[0]) {
        MessageBoxW(NULL,
                    L"Не удалось подготовить среду запуска AetherGlass.\n"
                    L"Проверьте подключение к интернету и повторите попытку.",
                    PRODUCT_TITLE_W, MB_ICONERROR | MB_OK);
        return 1;
    }

    /* 3. Определяем целевой JAR: сам .exe уже содержит полный JAR в конце файла,
          а если рядом лежит AetherGlassLauncher.jar — используем его */
    wchar_t targetJar[MAX_PATH * 2];
    wcsncpy(targetJar, selfExe, MAX_PATH * 2 - 1);

    wchar_t sideJar[MAX_PATH * 2];
    _snwprintf(sideJar, MAX_PATH * 2 - 1, L"%s\\AetherGlassLauncher.jar", exeDir);
    if (wcsstr(selfExe, L"AetherGlassLauncher") != NULL &&
        GetFileAttributesW(sideJar) != INVALID_FILE_ATTRIBUTES) {
        wcsncpy(targetJar, sideJar, MAX_PATH * 2 - 1);
    }

    /* 4. Запускаем javaw.exe без консольного окна */
    wchar_t fullCmd[MAX_PATH * 8];
    if (lpCmdLine && lpCmdLine[0]) {
        _snwprintf(fullCmd, MAX_PATH * 8 - 1,
                   L"\"%s\" -Xmx%dm -Dfile.encoding=UTF-8 \"-Daetherglass.self.exe=%s\" -jar \"%s\" %s",
                   g_bestJavaw, HEAP_MAX_MB, selfExe, targetJar, lpCmdLine);
    } else {
        _snwprintf(fullCmd, MAX_PATH * 8 - 1,
                   L"\"%s\" -Xmx%dm -Dfile.encoding=UTF-8 \"-Daetherglass.self.exe=%s\" -jar \"%s\"",
                   g_bestJavaw, HEAP_MAX_MB, selfExe, targetJar);
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessW(NULL, fullCmd, NULL, NULL, FALSE, 0, NULL, exeDir, &si, &pi)) {
        MessageBoxW(NULL, L"Не удалось запустить процесс AetherGlass.", PRODUCT_TITLE_W, MB_ICONERROR | MB_OK);
        return 1;
    }

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 0;
}
