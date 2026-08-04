#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <GL/gl.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <wchar.h>

static FILE *log_file;
static unsigned int swap_successes;
static unsigned int swap_failures;

static void log_line(const char *format, ...)
{
    va_list args;

    if (!log_file) return;
    va_start(args, format);
    vfprintf(log_file, format, args);
    va_end(args);
    fputc('\n', log_file);
    fflush(log_file);
}

static LRESULT CALLBACK window_proc(HWND hwnd, UINT message, WPARAM wparam, LPARAM lparam)
{
    if (message == WM_CLOSE)
    {
        DestroyWindow(hwnd);
        return 0;
    }
    if (message == WM_DESTROY)
    {
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, message, wparam, lparam);
}

static int fail(const wchar_t *message)
{
    log_line("FAIL: Win32 error %lu", GetLastError());
    MessageBoxW(NULL, message, L"WGL Swap Test - FAIL", MB_OK | MB_ICONERROR);
    return 1;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command_line, int show_command)
{
    static const wchar_t class_name[] = L"WinlatorWglSwapTest";
    wchar_t log_path[MAX_PATH];
    wchar_t *last_separator;
    PIXELFORMATDESCRIPTOR pfd = {0};
    WNDCLASSW window_class = {0};
    LARGE_INTEGER frequency, start, now;
    HGLRC gl_context = NULL;
    MSG message;
    HWND hwnd;
    HDC dc;
    int pixel_format;
    BOOL running = TRUE;
    unsigned int frame = 0;

    (void)previous;
    (void)command_line;
    (void)show_command;

    if (GetModuleFileNameW(NULL, log_path, MAX_PATH))
    {
        last_separator = wcsrchr(log_path, L'\\');
        if (last_separator) wcscpy(last_separator + 1, L"wgl_swap_test.log");
        else wcscpy(log_path, L"wgl_swap_test.log");
    }
    else wcscpy(log_path, L"wgl_swap_test.log");
    log_file = _wfopen(log_path, L"w");
    log_line("WGL swap test starting");

    window_class.style = CS_OWNDC;
    window_class.lpfnWndProc = window_proc;
    window_class.hInstance = instance;
    window_class.hCursor = LoadCursorW(NULL, IDC_ARROW);
    window_class.lpszClassName = class_name;
    if (!RegisterClassW(&window_class)) return fail(L"RegisterClass failed. See wgl_swap_test.log.");

    hwnd = CreateWindowExW(0, class_name, L"WGL Swap Test - starting", WS_OVERLAPPEDWINDOW | WS_VISIBLE,
                           CW_USEDEFAULT, CW_USEDEFAULT, 960, 540, NULL, NULL, instance, NULL);
    if (!hwnd) return fail(L"CreateWindow failed. See wgl_swap_test.log.");

    dc = GetDC(hwnd);
    if (!dc) return fail(L"GetDC failed. See wgl_swap_test.log.");

    pfd.nSize = sizeof(pfd);
    pfd.nVersion = 1;
    pfd.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
    pfd.iPixelType = PFD_TYPE_RGBA;
    pfd.cColorBits = 24;
    pfd.cDepthBits = 16;
    pfd.iLayerType = PFD_MAIN_PLANE;

    pixel_format = ChoosePixelFormat(dc, &pfd);
    log_line("ChoosePixelFormat=%d", pixel_format);
    if (!pixel_format || !SetPixelFormat(dc, pixel_format, &pfd))
        return fail(L"SetPixelFormat failed. See wgl_swap_test.log.");

    gl_context = wglCreateContext(dc);
    log_line("wglCreateContext=%p", gl_context);
    if (!gl_context || !wglMakeCurrent(dc, gl_context))
        return fail(L"OpenGL context creation failed. See wgl_swap_test.log.");

    log_line("GL_VENDOR=%s", glGetString(GL_VENDOR));
    log_line("GL_RENDERER=%s", glGetString(GL_RENDERER));
    log_line("GL_VERSION=%s", glGetString(GL_VERSION));

    QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&start);
    while (running)
    {
        RECT rect;
        wchar_t title[160];
        float elapsed;

        while (PeekMessageW(&message, NULL, 0, 0, PM_REMOVE))
        {
            if (message.message == WM_QUIT) running = FALSE;
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
        if (!running) break;

        QueryPerformanceCounter(&now);
        elapsed = (float)(now.QuadPart - start.QuadPart) / (float)frequency.QuadPart;
        GetClientRect(hwnd, &rect);
        glViewport(0, 0, rect.right, rect.bottom);
        glClearColor(0.5f + 0.5f * sinf(elapsed),
                     0.5f + 0.5f * sinf(elapsed + 2.094f),
                     0.5f + 0.5f * sinf(elapsed + 4.189f), 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glBegin(GL_TRIANGLES);
        glColor3f(1.0f, 0.2f, 0.2f); glVertex2f(0.0f, 0.75f);
        glColor3f(0.2f, 1.0f, 0.2f); glVertex2f(-0.75f, -0.65f);
        glColor3f(0.2f, 0.2f, 1.0f); glVertex2f(0.75f, -0.65f);
        glEnd();

        if (SwapBuffers(dc)) ++swap_successes;
        else
        {
            ++swap_failures;
            if (swap_failures <= 10) log_line("SwapBuffers failed at frame %u: error %lu", frame, GetLastError());
        }
        ++frame;

        if (!(frame % 60))
        {
            _snwprintf(title, 160, L"WGL Swap Test - PASS: animated colors | frames %u | swaps %u", frame, swap_successes);
            SetWindowTextW(hwnd, title);
            log_line("progress frame=%u swap_successes=%u swap_failures=%u", frame, swap_successes, swap_failures);
        }
        Sleep(16);
    }

    log_line("RESULT frames=%u swap_successes=%u swap_failures=%u", frame, swap_successes, swap_failures);
    wglMakeCurrent(NULL, NULL);
    wglDeleteContext(gl_context);
    ReleaseDC(hwnd, dc);
    if (log_file) fclose(log_file);
    return swap_successes ? 0 : 2;
}
