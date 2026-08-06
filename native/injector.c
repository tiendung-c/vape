#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include "loader_bootstrap.h"

#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <tlhelp32.h>

#include <conio.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#define MAX_CANDIDATES 256
#define WINDOW_TITLE_CAPACITY 256
#define REFRESH_INTERVAL_MS 750
#define COMMAND_LINE_CAPACITY 8192

typedef enum target_kind {
    TARGET_UNKNOWN = 0,
    TARGET_MINECRAFT = 1
} target_kind;

typedef LONG NTSTATUS;
typedef NTSTATUS (WINAPI *nt_query_information_process_fn)(
        HANDLE, ULONG, PVOID, ULONG, PULONG);

typedef struct process_basic_information_local {
    PVOID reserved1;
    PVOID peb_base_address;
    PVOID reserved2[2];
    ULONG_PTR unique_process_id;
    PVOID reserved3;
} process_basic_information_local;

typedef struct remote_unicode_string {
    USHORT length;
    USHORT maximum_length;
    PWSTR buffer;
} remote_unicode_string;

typedef struct process_candidate {
    DWORD process_id;
    wchar_t executable[MAX_PATH];
    wchar_t title[WINDOW_TITLE_CAPACITY];
    target_kind kind;
} process_candidate;

typedef struct bootstrap_controller {
    HANDLE mapping;
    HANDLE ack;
    HANDLE thread;
    SOCKET listener;
    volatile LONG connected;
    volatile LONG done;
    int winsock_started;
    uint16_t port;
} bootstrap_controller;

typedef struct window_search_context {
    process_candidate *candidates;
    size_t count;
} window_search_context;

static void print_last_error(const wchar_t *operation) {
    DWORD error = GetLastError();
    wchar_t *message = NULL;
    FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER
                    | FORMAT_MESSAGE_FROM_SYSTEM
                    | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL, error, 0, (wchar_t *)&message, 0, NULL);
    fwprintf(stderr, L"%ls failed (%lu): %ls\n", operation,
            (unsigned long)error, message == NULL ? L"unknown error" : message);
    if (message != NULL) {
        LocalFree(message);
    }
}

static int absolute_existing_file(
        const wchar_t *input, wchar_t *output, DWORD capacity) {
    DWORD length = GetFullPathNameW(input, capacity, output, NULL);
    DWORD attributes;
    if (length == 0 || length >= capacity) {
        return 0;
    }
    attributes = GetFileAttributesW(output);
    return attributes != INVALID_FILE_ATTRIBUTES
            && (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

static int default_dll_path(wchar_t *output, DWORD capacity) {
    DWORD length = GetModuleFileNameW(NULL, output, capacity);
    DWORD attributes;
    wchar_t *file_name;
    if (length == 0 || length >= capacity) {
        return 0;
    }
    file_name = wcsrchr(output, L'\\');
    file_name = file_name == NULL ? output : file_name + 1;
    if ((size_t)(file_name - output) + wcslen(L"Vape421Native.dll") + 1
            > capacity) {
        return 0;
    }
    wcscpy(file_name, L"Vape421Native.dll");
    attributes = GetFileAttributesW(output);
    return attributes != INVALID_FILE_ATTRIBUTES
            && (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

static int is_java_process(const wchar_t *executable) {
    return _wcsicmp(executable, L"java.exe") == 0
            || _wcsicmp(executable, L"javaw.exe") == 0;
}

static int contains_case_insensitive(const wchar_t *text, const wchar_t *needle) {
    size_t needle_length;
    const wchar_t *cursor;
    if (text == NULL || needle == NULL) return 0;
    needle_length = wcslen(needle);
    if (needle_length == 0) return 1;
    for (cursor = text; *cursor != L'\0'; ++cursor) {
        if (_wcsnicmp(cursor, needle, needle_length) == 0) return 1;
    }
    return 0;
}

static int read_remote_command_line(
        HANDLE process, wchar_t *output, DWORD capacity) {
    HMODULE ntdll;
    nt_query_information_process_fn query;
    process_basic_information_local basic;
    remote_unicode_string command_line;
    PVOID process_parameters = NULL;
    SIZE_T bytes_read = 0;
    ULONG return_length = 0;
    SIZE_T copy_bytes;

    if (output == NULL || capacity < 2) return 0;
    output[0] = L'\0';
    ntdll = GetModuleHandleW(L"ntdll.dll");
    query = ntdll == NULL ? NULL
            : (nt_query_information_process_fn)GetProcAddress(
                    ntdll, "NtQueryInformationProcess");
    if (query == NULL) return 0;
    memset(&basic, 0, sizeof(basic));
    if (query(process, 0, &basic, (ULONG)sizeof(basic), &return_length) != 0
            || basic.peb_base_address == NULL) {
        return 0;
    }
    /* PEB.ProcessParameters is at 0x20 for the x64 JVMs accepted by this tool. */
    if (!ReadProcessMemory(process,
            (const unsigned char *)basic.peb_base_address + 0x20,
            &process_parameters, sizeof(process_parameters), &bytes_read)
            || bytes_read != sizeof(process_parameters)
            || process_parameters == NULL) {
        return 0;
    }
    /* RTL_USER_PROCESS_PARAMETERS.CommandLine is at 0x70 on x64 Windows. */
    if (!ReadProcessMemory(process,
            (const unsigned char *)process_parameters + 0x70,
            &command_line, sizeof(command_line), &bytes_read)
            || bytes_read != sizeof(command_line)
            || command_line.buffer == NULL || command_line.length == 0) {
        return 0;
    }
    copy_bytes = command_line.length;
    if (copy_bytes > ((SIZE_T)capacity - 1) * sizeof(wchar_t)) {
        copy_bytes = ((SIZE_T)capacity - 1) * sizeof(wchar_t);
    }
    if (!ReadProcessMemory(process, command_line.buffer, output,
            copy_bytes, &bytes_read) || bytes_read != copy_bytes) {
        output[0] = L'\0';
        return 0;
    }
    output[copy_bytes / sizeof(wchar_t)] = L'\0';
    return 1;
}

static target_kind classify_process_command_line(const wchar_t *command_line) {
    if (contains_case_insensitive(command_line, L"minecraft")
            || contains_case_insensitive(command_line, L"net.minecraft")
            || contains_case_insensitive(command_line, L"com.mojang")
            || contains_case_insensitive(command_line, L"badlion")
            || contains_case_insensitive(command_line, L"lunar")
            || contains_case_insensitive(command_line, L"forge")
            || contains_case_insensitive(command_line, L"net.minecraftforge")
            || contains_case_insensitive(command_line, L"launchwrapper")
            || contains_case_insensitive(command_line, L"cpw.mods.fml")
            || contains_case_insensitive(command_line, L"fmlclient")
            || contains_case_insensitive(command_line, L"--gamedir")
            || contains_case_insensitive(command_line, L"--assetsdir")
            || contains_case_insensitive(command_line, L"--username")) {
        return TARGET_MINECRAFT;
    }
    /* An unreadable or generic Java command line is not auto-selected. It can
       still be shown in the manual selector when it owns a visible window. */
    return TARGET_UNKNOWN;
}

static target_kind detect_process_kind(
        DWORD process_id, wchar_t *command_line, DWORD capacity) {
    HANDLE process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ,
            FALSE, process_id);
    target_kind result = TARGET_UNKNOWN;
    if (process == NULL) return result;
    if (read_remote_command_line(process, command_line, capacity)) {
        result = classify_process_command_line(command_line);
    }
    CloseHandle(process);
    return result;
}

static const wchar_t *target_kind_name(target_kind kind) {
    switch (kind) {
        case TARGET_MINECRAFT: return L"Minecraft JVM (java.exe/javaw.exe)";
        default: return L"Unknown";
    }
}

static BOOL CALLBACK capture_window_title(HWND window, LPARAM parameter) {
    window_search_context *context = (window_search_context *)parameter;
    wchar_t title[WINDOW_TITLE_CAPACITY];
    DWORD process_id = 0;
    size_t index;
    if (!IsWindowVisible(window) || GetWindowTextLengthW(window) == 0) {
        return TRUE;
    }
    GetWindowThreadProcessId(window, &process_id);
    if (process_id == 0
            || GetWindowTextW(window, title, WINDOW_TITLE_CAPACITY) == 0) {
        return TRUE;
    }
    for (index = 0; index < context->count; ++index) {
        process_candidate *candidate = &context->candidates[index];
        if (candidate->process_id == process_id && candidate->title[0] == L'\0') {
            wcscpy(candidate->title, title);
            break;
        }
    }
    return TRUE;
}

static int compare_candidates(const void *left, const void *right) {
    const process_candidate *a = (const process_candidate *)left;
    const process_candidate *b = (const process_candidate *)right;
    if (a->process_id < b->process_id) return -1;
    if (a->process_id > b->process_id) return 1;
    return 0;
}

static size_t enumerate_candidates(
        process_candidate *candidates, size_t capacity) {
    HANDLE snapshot;
    PROCESSENTRY32W entry;
    size_t count = 0;
    size_t read_index;
    size_t write_index = 0;
    window_search_context context;
    wchar_t command_line[COMMAND_LINE_CAPACITY];

    snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (count < capacity && is_java_process(entry.szExeFile)) {
                process_candidate *candidate = &candidates[count++];
                memset(candidate, 0, sizeof(*candidate));
                candidate->process_id = entry.th32ProcessID;
                wcsncpy(candidate->executable, entry.szExeFile, MAX_PATH - 1);
                candidate->kind = detect_process_kind(candidate->process_id,
                        command_line,
                        (DWORD)(sizeof(command_line) / sizeof(command_line[0])));
            }
        } while (Process32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);

    context.candidates = candidates;
    context.count = count;
    EnumWindows(capture_window_title, (LPARAM)&context);

    /* The selector is for game windows, so omit background JVMs without one. */
    for (read_index = 0; read_index < count; ++read_index) {
        if (candidates[read_index].title[0] != L'\0'
                || candidates[read_index].kind != TARGET_UNKNOWN) {
            if (write_index != read_index) {
                candidates[write_index] = candidates[read_index];
            }
            ++write_index;
        }
    }
    qsort(candidates, write_index, sizeof(*candidates), compare_candidates);
    return write_index;
}

static void clear_console_rows(HANDLE output, SHORT rows) {
    CONSOLE_SCREEN_BUFFER_INFO info;
    COORD start = {0, 0};
    DWORD cells;
    DWORD written;
    if (!GetConsoleScreenBufferInfo(output, &info)) return;
    if (rows > info.dwSize.Y) rows = info.dwSize.Y;
    cells = (DWORD)info.dwSize.X * (DWORD)rows;
    FillConsoleOutputCharacterW(output, L' ', cells, start, &written);
    FillConsoleOutputAttribute(output, info.wAttributes, cells, start, &written);
}

static void render_selector(const process_candidate *candidates, size_t count,
        size_t selected, const wchar_t *dll_path) {
    HANDLE output = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_SCREEN_BUFFER_INFO info;
    COORD home = {0, 0};
    size_t index;
    static SHORT previous_rows = 0;
    if (GetConsoleScreenBufferInfo(output, &info)) {
        SHORT rows = (SHORT)(count + 5);
        if (count == 0) ++rows;
        clear_console_rows(output, rows > previous_rows ? rows : previous_rows);
        SetConsoleCursorPosition(output, home);
        previous_rows = rows;
    }
    wprintf(L"Vape421 Injector\n");
    wprintf(L"DLL: %ls\n\n", dll_path);
    wprintf(L"Select a Java game window (Up/Down, Enter to inject, Esc to quit)\n\n");
    if (count == 0) {
        wprintf(L"  No visible java.exe/javaw.exe windows. Waiting...\n");
    } else {
        for (index = 0; index < count; ++index) {
            const wchar_t *title = candidates[index].title[0] == L'\0'
                    ? L"(no visible window)" : candidates[index].title;
            wprintf(L"%lc [%5lu] %-9ls  %-15ls  %ls\n",
                    index == selected ? L'>' : L' ',
                    (unsigned long)candidates[index].process_id,
                    candidates[index].executable,
                    target_kind_name(candidates[index].kind), title);
        }
    }
    fflush(stdout);
}

static int bundle_root_path(wchar_t *output, DWORD capacity) {
    DWORD length = GetModuleFileNameW(NULL, output, capacity);
    wchar_t *separator;
    if (length == 0 || length >= capacity) return 0;
    separator = wcsrchr(output, L'\\');
    if (separator == NULL) return 0;
    *separator = L'\0';
    return 1;
}

static int payload_path_for_target(
        const wchar_t *bundle_root, target_kind kind,
        wchar_t *output, DWORD capacity) {
    if (bundle_root == NULL || output == NULL || kind == TARGET_UNKNOWN) return 0;
    if (_snwprintf_s(output, capacity, _TRUNCATE, L"%ls\\Vape421Native.dll",
            bundle_root) < 0) return 0;
    return GetFileAttributesW(output) != INVALID_FILE_ATTRIBUTES;
}

static void object_name(wchar_t *output, size_t capacity,
        const wchar_t *kind, DWORD process_id) {
    _snwprintf_s(output, capacity, _TRUNCATE,
            L"Local\\Vape421.%ls.%lu", kind, process_id);
}

static int receive_line(SOCKET socket_value, char *output, size_t capacity) {
    size_t length = 0;
    while (length + 1 < capacity) {
        char character;
        int received = recv(socket_value, &character, 1, 0);
        if (received != 1) return 0;
        if (character == '\n') {
            output[length] = '\0';
            return 1;
        }
        if (character != '\r') {
            output[length++] = character;
        }
    }
    output[capacity - 1] = '\0';
    return 0;
}

static int receive_exact(SOCKET socket_value, char *output, size_t length) {
    size_t offset = 0;
    while (offset < length) {
        int received = recv(socket_value, output + offset,
                (int)(length - offset), 0);
        if (received <= 0) return 0;
        offset += (size_t)received;
    }
    return 1;
}

static const wchar_t *progress_step_name(int step) {
    switch (step) {
        case 10: return L"JVM found";
        case 20: return L"JVMTI initialized";
        case 30: return L"Minecraft ClassLoader selected";
        case 40: return L"Java payload linked";
        case 50: return L"Java bootstrap started";
        default: return L"progress";
    }
}

/* Keep the native bootstrap diagnostics after the injector console closes. */
static void append_injection_log(const char *message, size_t length) {
    wchar_t appdata[MAX_PATH];
    wchar_t vape_directory[MAX_PATH];
    wchar_t logs_directory[MAX_PATH];
    wchar_t log_path[MAX_PATH];
    SYSTEMTIME now;
    HANDLE file;
    DWORD written;

    if (message == NULL || length == 0
            || GetEnvironmentVariableW(L"APPDATA", appdata, MAX_PATH) == 0) {
        return;
    }
    if (_snwprintf_s(vape_directory, MAX_PATH, _TRUNCATE,
            L"%ls\\Vape", appdata) < 0
            || _snwprintf_s(logs_directory, MAX_PATH, _TRUNCATE,
            L"%ls\\logs", vape_directory) < 0) {
        return;
    }
    CreateDirectoryW(vape_directory, NULL);
    CreateDirectoryW(logs_directory, NULL);
    GetLocalTime(&now);
    if (_snwprintf_s(log_path, MAX_PATH, _TRUNCATE,
            L"%ls\\%04u-%02u-%02u-%02u-%02u.logs", logs_directory,
            now.wYear, now.wMonth, now.wDay, now.wHour, now.wMinute) < 0) {
        return;
    }
    file = CreateFileW(log_path, FILE_APPEND_DATA, FILE_SHARE_READ,
            NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) return;
    WriteFile(file, message, (DWORD)length, &written, NULL);
    CloseHandle(file);
}

static DWORD WINAPI serve_bootstrap_controller(LPVOID parameter) {
    bootstrap_controller *controller = (bootstrap_controller *)parameter;
    SOCKET client = accept(controller->listener, NULL, NULL);
    char code[32];
    if (client == INVALID_SOCKET) {
        return 0;
    }
    InterlockedExchange(&controller->connected, 1);
    while (receive_line(client, code, sizeof(code))) {
        if (strcmp(code, "604") == 0) {
            char value[32];
            char status[32];
            int step;
            if (!receive_line(client, value, sizeof(value))
                    || !receive_line(client, status, sizeof(status))) {
                break;
            }
            step = atoi(value);
            wprintf(L"[bootstrap] %ls (%d)\n", progress_step_name(step), step);
            fflush(stdout);
        } else if (strcmp(code, "606") == 0) {
            char status[32];
            receive_line(client, status, sizeof(status));
            wprintf(L"[bootstrap] completed\n");
            fflush(stdout);
            break;
        } else if (strcmp(code, "618") == 0 || strcmp(code, "610") == 0) {
            char length_text[32];
            size_t length;
            size_t remaining;
            char buffer[4096];
            if (!receive_line(client, length_text, sizeof(length_text))) {
                break;
            }
            length = (size_t)strtoul(length_text, NULL, 10);
            remaining = length;
            if (strcmp(code, "618") == 0) {
                fwprintf(stderr, L"[bootstrap] failure: ");
            }
            while (remaining != 0) {
                size_t chunk = remaining < sizeof(buffer) - 1
                        ? remaining : sizeof(buffer) - 1;
                if (!receive_exact(client, buffer, chunk)) {
                    remaining = 0;
                    break;
                }
                buffer[chunk] = '\0';
                if (strcmp(code, "618") == 0) {
                    fprintf(stderr, "%s", buffer);
                } else {
                    printf("%s", buffer);
                }
                append_injection_log(buffer, chunk);
                remaining -= chunk;
            }
            if (strcmp(code, "618") == 0) {
                fprintf(stderr, "\n");
                fflush(stderr);
                break;
            }
            fflush(stdout);
        } else {
            break;
        }
    }
    closesocket(client);
    InterlockedExchange(&controller->done, 1);
    return 0;
}

static void cleanup_bootstrap_controller(bootstrap_controller *controller) {
    if (controller == NULL) return;
    if (controller->listener != INVALID_SOCKET) {
        closesocket(controller->listener);
        controller->listener = INVALID_SOCKET;
    }
    if (controller->thread != NULL) {
        WaitForSingleObject(controller->thread, 1000);
        CloseHandle(controller->thread);
        controller->thread = NULL;
    }
    if (controller->mapping != NULL) {
        CloseHandle(controller->mapping);
        controller->mapping = NULL;
    }
    if (controller->ack != NULL) {
        CloseHandle(controller->ack);
        controller->ack = NULL;
    }
    if (controller->winsock_started) {
        WSACleanup();
        controller->winsock_started = 0;
    }
}

static int setup_bootstrap_controller(
        bootstrap_controller *controller, DWORD process_id) {
    WSADATA winsock_data;
    struct sockaddr_in address;
    int address_length = sizeof(address);
    wchar_t mapping_name[96];
    wchar_t ack_name[96];
    Vape421BootstrapV3 *block;
    if (controller == NULL) return 0;
    memset(controller, 0, sizeof(*controller));
    controller->listener = INVALID_SOCKET;
    if (WSAStartup(MAKEWORD(2, 2), &winsock_data) != 0) {
        return 0;
    }
    controller->winsock_started = 1;
    controller->listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    address.sin_port = 0;
    if (controller->listener == INVALID_SOCKET
            || bind(controller->listener, (const struct sockaddr *)&address,
                    sizeof(address)) == SOCKET_ERROR
            || listen(controller->listener, 1) == SOCKET_ERROR
            || getsockname(controller->listener, (struct sockaddr *)&address,
                    &address_length) == SOCKET_ERROR) {
        cleanup_bootstrap_controller(controller);
        return 0;
    }
    controller->port = ntohs(address.sin_port);
    object_name(mapping_name, sizeof(mapping_name) / sizeof(mapping_name[0]),
            L"Bootstrap", process_id);
    object_name(ack_name, sizeof(ack_name) / sizeof(ack_name[0]),
            L"BootstrapAck", process_id);
    controller->mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, NULL,
            PAGE_READWRITE, 0, sizeof(*block), mapping_name);
    controller->ack = CreateEventW(NULL, TRUE, FALSE, ack_name);
    block = controller->mapping == NULL ? NULL
            : (Vape421BootstrapV3 *)MapViewOfFile(controller->mapping,
                    FILE_MAP_ALL_ACCESS, 0, 0, sizeof(*block));
    if (controller->mapping == NULL || controller->ack == NULL
            || block == NULL) {
        if (block != NULL) UnmapViewOfFile(block);
        cleanup_bootstrap_controller(controller);
        return 0;
    }
    SecureZeroMemory(block, sizeof(*block));
    block->magic = VAPE421_BOOTSTRAP_MAGIC;
    block->version = VAPE421_BOOTSTRAP_VERSION;
    block->structure_size = (uint16_t)sizeof(*block);
    block->target_pid = process_id;
    block->controller_port = controller->port;
    block->status = VAPE421_BOOTSTRAP_STATUS_CREATED;
    UnmapViewOfFile(block);
    controller->thread = CreateThread(NULL, 0, serve_bootstrap_controller,
            controller, 0, NULL);
    if (controller->thread == NULL) {
        cleanup_bootstrap_controller(controller);
        return 0;
    }
    return 1;
}

static DWORD select_process(void) {
    process_candidate candidates[MAX_CANDIDATES];
    size_t count = 0;
    size_t selected = 0;
    DWORD selected_process_id = 0;
    ULONGLONG next_refresh = 0;
    HANDLE output = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_CURSOR_INFO original_cursor;
    CONSOLE_CURSOR_INFO hidden_cursor;
    int cursor_changed = 0;

    if (GetConsoleCursorInfo(output, &original_cursor)) {
        hidden_cursor = original_cursor;
        hidden_cursor.bVisible = FALSE;
        cursor_changed = SetConsoleCursorInfo(output, &hidden_cursor);
    }
    /* A single high-confidence Minecraft JVM can be selected without UI.
       Multiple JVMs remain manual so a launcher/helper process is not chosen
       accidentally. */
    count = enumerate_candidates(candidates, MAX_CANDIDATES);
    if (count == 1 && candidates[0].kind == TARGET_MINECRAFT) {
        wprintf(L"Auto-detected %ls [%lu].\n",
                candidates[0].executable,
                (unsigned long)candidates[0].process_id);
        if (cursor_changed) SetConsoleCursorInfo(output, &original_cursor);
        return candidates[0].process_id;
    }
    count = 0;
    for (;;) {
        ULONGLONG now = GetTickCount64();
        if (now >= next_refresh) {
            DWORD previous_id = count == 0 ? 0 : candidates[selected].process_id;
            size_t index;
            count = enumerate_candidates(candidates, MAX_CANDIDATES);
            selected = 0;
            for (index = 0; index < count; ++index) {
                if (candidates[index].process_id == previous_id) {
                    selected = index;
                    break;
                }
            }
            render_selector(candidates, count, selected, L"auto-detect");
            next_refresh = now + REFRESH_INTERVAL_MS;
        }
        if (_kbhit()) {
            int key = _getwch();
            if (key == 0 || key == 0xe0) {
                key = _getwch();
                if (key == 72 && count != 0) {
                    selected = selected == 0 ? count - 1 : selected - 1;
                    render_selector(candidates, count, selected, L"auto-detect");
                } else if (key == 80 && count != 0) {
                    selected = (selected + 1) % count;
                    render_selector(candidates, count, selected, L"auto-detect");
                }
            } else if (key == 13 && count != 0) {
                selected_process_id = candidates[selected].process_id;
                break;
            } else if (key == 27) {
                break;
            }
        }
        Sleep(25);
    }
    if (cursor_changed) SetConsoleCursorInfo(output, &original_cursor);
    wprintf(L"\n");
    return selected_process_id;
}

static uintptr_t remote_module_base(DWORD process_id, const wchar_t *module_name) {
    HANDLE snapshot;
    MODULEENTRY32W entry;
    uintptr_t result = 0;
    snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szModule, module_name) == 0) {
                result = (uintptr_t)entry.modBaseAddr;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return result;
}

static uintptr_t remote_module_by_path(
        DWORD process_id, const wchar_t *module_path) {
    HANDLE snapshot;
    MODULEENTRY32W entry;
    uintptr_t result = 0;
    snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szExePath, module_path) == 0) {
                result = (uintptr_t)entry.modBaseAddr;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return result;
}

static int require_x64_target(HANDLE process) {
    typedef BOOL (WINAPI *is_wow64_process2_fn)(HANDLE, USHORT *, USHORT *);
    is_wow64_process2_fn is_wow64_process2 =
            (is_wow64_process2_fn)GetProcAddress(
                    GetModuleHandleW(L"kernel32.dll"), "IsWow64Process2");
    if (is_wow64_process2 != NULL) {
        USHORT process_machine = IMAGE_FILE_MACHINE_UNKNOWN;
        USHORT native_machine = IMAGE_FILE_MACHINE_UNKNOWN;
        if (!is_wow64_process2(process, &process_machine, &native_machine)) {
            return 0;
        }
        return process_machine == IMAGE_FILE_MACHINE_UNKNOWN
                && native_machine == IMAGE_FILE_MACHINE_AMD64;
    }
    {
        BOOL wow64 = FALSE;
        if (!IsWow64Process(process, &wow64)) {
            return 0;
        }
        return !wow64 && sizeof(void *) == 8;
    }
}

static int inject_library(DWORD process_id, const wchar_t *dll_path) {
    HANDLE process = NULL;
    HANDLE thread = NULL;
    HANDLE completion_event = NULL;
    HANDLE failure_event = NULL;
    LPVOID remote_path = NULL;
    HMODULE local_kernel;
    FARPROC local_load_library;
    uintptr_t remote_kernel;
    uintptr_t load_library_offset;
    LPTHREAD_START_ROUTINE remote_load_library;
    SIZE_T path_bytes = (wcslen(dll_path) + 1) * sizeof(wchar_t);
    SIZE_T written = 0;
    DWORD wait_result;
    int attempt;
    int result = 0;
    wchar_t completion_name[96];
    wchar_t failure_name[96];
    HANDLE result_events[2];
    DWORD bootstrap_result;
    bootstrap_controller controller;
    int controller_started = 0;

    _snwprintf_s(completion_name, sizeof(completion_name) / sizeof(completion_name[0]),
            _TRUNCATE, L"Local\\Vape421.InjectComplete.%lu", process_id);
    _snwprintf_s(failure_name, sizeof(failure_name) / sizeof(failure_name[0]),
            _TRUNCATE, L"Local\\Vape421.InjectFailed.%lu", process_id);

    if (remote_module_by_path(process_id, dll_path) != 0) {
        return 2;
    }

    completion_event = CreateEventW(NULL, TRUE, FALSE, completion_name);
    failure_event = CreateEventW(NULL, TRUE, FALSE, failure_name);
    if (completion_event == NULL || failure_event == NULL) {
        print_last_error(L"CreateEventW");
        goto cleanup;
    }
    ResetEvent(completion_event);
    ResetEvent(failure_event);
    controller_started = setup_bootstrap_controller(&controller, process_id);
    if (!controller_started) {
        fwprintf(stderr, L"Warning: bootstrap console log channel is unavailable; "
                L"injection will continue without live logs.\n");
    }

    process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION
                    | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ,
            FALSE, process_id);
    if (process == NULL) {
        print_last_error(L"OpenProcess");
        goto cleanup;
    }
    if (!require_x64_target(process)) {
        fwprintf(stderr, L"Target process is not x64; injection refused.\n");
        goto cleanup;
    }
    remote_path = VirtualAllocEx(process, NULL, path_bytes,
            MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (remote_path == NULL) {
        print_last_error(L"VirtualAllocEx");
        goto cleanup;
    }
    if (!WriteProcessMemory(process, remote_path, dll_path,
            path_bytes, &written) || written != path_bytes) {
        print_last_error(L"WriteProcessMemory");
        goto cleanup;
    }

    local_kernel = GetModuleHandleW(L"kernel32.dll");
    local_load_library = GetProcAddress(local_kernel, "LoadLibraryW");
    remote_kernel = remote_module_base(process_id, L"kernel32.dll");
    if (local_kernel == NULL || local_load_library == NULL || remote_kernel == 0) {
        fwprintf(stderr, L"Could not resolve remote kernel32!LoadLibraryW.\n");
        goto cleanup;
    }
    load_library_offset = (uintptr_t)local_load_library - (uintptr_t)local_kernel;
    remote_load_library = (LPTHREAD_START_ROUTINE)(
            remote_kernel + load_library_offset);
    thread = CreateRemoteThread(process, NULL, 0, remote_load_library,
            remote_path, 0, NULL);
    if (thread == NULL) {
        print_last_error(L"CreateRemoteThread");
        goto cleanup;
    }
    wait_result = WaitForSingleObject(thread, 30000);
    if (wait_result != WAIT_OBJECT_0) {
        fwprintf(stderr, L"Remote LoadLibraryW did not finish within 30 seconds.\n");
        goto cleanup;
    }
    if (controller_started
            && WaitForSingleObject(controller.ack, 5000) != WAIT_OBJECT_0) {
        fwprintf(stderr, L"Warning: DLL did not acknowledge bootstrap log channel; "
                L"continuing to wait for injection status.\n");
    }
    for (attempt = 0; attempt < 100; ++attempt) {
        if (remote_module_by_path(process_id, dll_path) != 0) {
            result = 1;
            break;
        }
        Sleep(50);
    }
    if (result == 0) {
        fwprintf(stderr, L"LoadLibraryW returned, but the DLL is not mapped. "
                L"Check the injector console output for bootstrap failure.\n");
        goto cleanup;
    }

    wprintf(L"DLL mapped into PID %lu; waiting for Java bootstrap confirmation...\n",
            process_id);
    result_events[0] = completion_event;
    result_events[1] = failure_event;
    bootstrap_result = WaitForMultipleObjects(2, result_events, FALSE, 300000);
    if (bootstrap_result == WAIT_OBJECT_0) {
        result = 1;
    } else if (bootstrap_result == WAIT_OBJECT_0 + 1) {
        fwprintf(stderr, L"DLL loaded, but Java bootstrap reported failure. "
                L"Check the injector console output.\n");
        result = 0;
    } else if (bootstrap_result == WAIT_TIMEOUT) {
        fwprintf(stderr, L"DLL was mapped, but Java bootstrap did not confirm "
                L"within 5 minutes. Check the injector console output.\n");
        result = 0;
    } else {
        print_last_error(L"WaitForMultipleObjects");
        result = 0;
    }

cleanup:
    if (completion_event != NULL) CloseHandle(completion_event);
    if (failure_event != NULL) CloseHandle(failure_event);
    if (thread != NULL) CloseHandle(thread);
    if (remote_path != NULL && process != NULL) {
        VirtualFreeEx(process, remote_path, 0, MEM_RELEASE);
    }
    if (process != NULL) CloseHandle(process);
    if (controller_started) cleanup_bootstrap_controller(&controller);
    return result;
}

static void usage(const wchar_t *program) {
    fwprintf(stderr,
            L"Usage: %ls [Vape421Native.dll]\n"
            L"       %ls <minecraft-pid> <target-DLL>\n"
            L"Without a PID, an automatically refreshing Java window selector is shown.\n"
            L"The injector uses the single native DLL for the selected Java JVM.\n",
            program, program);
}

static void wait_for_close(void) {
    wprintf(L"\nInjection finished. Press Enter to close...\n");
    fflush(stdout);
    while (_getwch() != 13) {
        /* Keep the injector open until the user explicitly confirms. */
    }
}

int wmain(int argc, wchar_t **argv) {
    wchar_t dll_path[MAX_PATH];
    wchar_t bundle_root[MAX_PATH];
    wchar_t *end = NULL;
    unsigned long process_id = 0;
    if (argc < 1 || argc > 3) {
        usage(argv[0]);
        return 2;
    }
    if (argc == 3) {
        process_id = wcstoul(argv[1], &end, 10);
        if (process_id == 0 || end == argv[1] || *end != L'\0') {
            fwprintf(stderr, L"Invalid process id: %ls\n", argv[1]);
            return 2;
        }
        if (!absolute_existing_file(argv[2], dll_path, MAX_PATH)) {
            fwprintf(stderr, L"DLL does not exist: %ls\n", argv[2]);
            return 2;
        }
    } else if (argc == 2) {
        if (!absolute_existing_file(argv[1], dll_path, MAX_PATH)) {
            fwprintf(stderr, L"DLL does not exist: %ls\n", argv[1]);
            return 2;
        }
    }
    if (argc != 3) {
        if (argc == 1 && !bundle_root_path(bundle_root, MAX_PATH)) {
            fwprintf(stderr, L"Could not resolve injector bundle directory.\n");
            return 2;
        }
        process_id = (unsigned long)select_process();
        if (process_id == 0) {
            return 1;
        }
    }
    if (argc == 1) {
        wchar_t command_line[COMMAND_LINE_CAPACITY];
        target_kind kind = detect_process_kind((DWORD)process_id,
                command_line, (DWORD)(sizeof(command_line) / sizeof(command_line[0])));
        if (kind == TARGET_UNKNOWN) {
            fwprintf(stderr,
                    L"Could not identify PID %lu as a Minecraft JVM from its command line.\n"
                    L"Use the selector or explicit PID + DLL form when the launcher hides its arguments.\n",
                    process_id);
            return 2;
        }
        if (!payload_path_for_target(bundle_root, kind, dll_path, MAX_PATH)) {
            fwprintf(stderr, L"Payload for %ls is missing beside the injector.\n",
                    target_kind_name(kind));
            return 2;
        }
        wprintf(L"Detected %ls for PID %lu; selecting %ls.\n",
                target_kind_name(kind), process_id, dll_path);
    }
    {
        int injection_result = inject_library((DWORD)process_id, dll_path);
        if (injection_result == 0) {
            return 3;
        }
        if (injection_result == 2) {
            wprintf(L"%ls is already loaded in PID %lu; no second bootstrap was requested.\n",
                    dll_path, process_id);
            wait_for_close();
            return 0;
        }
    }
    wprintf(L"Injection successful: DLL loaded and Java bootstrap completed in PID %lu.\n",
            process_id);
    wait_for_close();
    return 0;
}
