#include "vape421_native.h"
#include "loader_bootstrap.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

static volatile LONG g_loaded_by_jni = 0;

#define VAPE421_PRODUCT_JAR_RESOURCE_ID 421

enum {
    VAPE421_BOOT_PROGRESS_JVM = 10,
    VAPE421_BOOT_PROGRESS_JVMTI = 20,
    VAPE421_BOOT_PROGRESS_CLASSLOADER = 30,
    VAPE421_BOOT_PROGRESS_PAYLOAD = 40,
    VAPE421_BOOT_PROGRESS_JAVA = 50
};

static int module_directory(wchar_t *output, size_t capacity) {
    DWORD length;
    wchar_t *separator;
    if (output == NULL || capacity == 0 || g_module == NULL) {
        return 0;
    }
    length = GetModuleFileNameW(g_module, output, (DWORD)capacity);
    if (length == 0 || length >= capacity) {
        return 0;
    }
    separator = wcsrchr(output, L'\\');
    if (separator == NULL) {
        return 0;
    }
    *separator = L'\0';
    return 1;
}

void vape_log(const wchar_t *format, ...) {
    wchar_t message[2048];
    wchar_t line[2304];
    wchar_t directory[MAX_PATH];
    wchar_t log_path[MAX_PATH];
    SYSTEMTIME now;
    FILE *file = NULL;
    va_list arguments;

    va_start(arguments, format);
    _vsnwprintf_s(message, sizeof(message) / sizeof(message[0]),
            _TRUNCATE, format, arguments);
    va_end(arguments);
    GetLocalTime(&now);
    _snwprintf_s(line, sizeof(line) / sizeof(line[0]), _TRUNCATE,
            L"[%04u-%02u-%02u %02u:%02u:%02u.%03u] %ls\r\n",
            now.wYear, now.wMonth, now.wDay, now.wHour, now.wMinute,
            now.wSecond, now.wMilliseconds, message);
    OutputDebugStringW(line);

    if (!module_directory(directory, sizeof(directory) / sizeof(directory[0]))) {
        return;
    }
    _snwprintf_s(log_path, sizeof(log_path) / sizeof(log_path[0]), _TRUNCATE,
            L"%ls\\vape421-native.log", directory);
    if (_wfopen_s(&file, log_path, L"a, ccs=UTF-8") == 0 && file != NULL) {
        fputws(line, file);
        fclose(file);
    }
}

void vape_log_pending_exception(JNIEnv *env, const wchar_t *context) {
    jthrowable throwable;
    jclass throwable_class;
    jmethodID to_string;
    jstring text;
    const jchar *characters;
    jsize length;
    wchar_t buffer[1024];
    if (env == NULL || !(*env)->ExceptionCheck(env)) {
        vape_log(L"%ls failed without a Java exception", context);
        return;
    }
    throwable = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    throwable_class = (*env)->FindClass(env, "java/lang/Throwable");
    to_string = throwable_class == NULL ? NULL : (*env)->GetMethodID(
            env, throwable_class, "toString", "()Ljava/lang/String;");
    text = to_string == NULL ? NULL : (jstring)(*env)->CallObjectMethod(
            env, throwable, to_string);
    if (text == NULL || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        vape_log(L"%ls raised an unreadable Java exception", context);
        return;
    }
    characters = (*env)->GetStringChars(env, text, NULL);
    length = (*env)->GetStringLength(env, text);
    if (characters != NULL) {
        size_t copied = (size_t)length < 1023 ? (size_t)length : 1023;
        memcpy(buffer, characters, copied * sizeof(wchar_t));
        buffer[copied] = L'\0';
        (*env)->ReleaseStringChars(env, text, characters);
        vape_log(L"%ls: %ls", context, buffer);
    }
}

static jstring new_wide_string(JNIEnv *env, const wchar_t *value) {
    if (value == NULL) {
        return NULL;
    }
    return (*env)->NewString(env, (const jchar *)value, (jsize)wcslen(value));
}

static int materialize_embedded_product_jar(
        wchar_t *jar_path, size_t jar_capacity) {
    HRSRC resource;
    HGLOBAL loaded_resource;
    const unsigned char *bytes;
    DWORD size;
    wchar_t temp_root[MAX_PATH];
    wchar_t temp_directory[MAX_PATH];
    HANDLE file = INVALID_HANDLE_VALUE;
    DWORD offset = 0;
    int result = 0;

    resource = FindResourceW(g_module,
            MAKEINTRESOURCEW(VAPE421_PRODUCT_JAR_RESOURCE_ID),
            MAKEINTRESOURCEW(10));
    if (resource == NULL) {
        vape_log(L"embedded product JAR resource %d is missing",
                VAPE421_PRODUCT_JAR_RESOURCE_ID);
        return 0;
    }
    size = SizeofResource(g_module, resource);
    loaded_resource = LoadResource(g_module, resource);
    bytes = loaded_resource == NULL ? NULL
            : (const unsigned char *)LockResource(loaded_resource);
    if (bytes == NULL || size < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
        vape_log(L"embedded product JAR resource is invalid");
        return 0;
    }
    if (GetTempPathW((DWORD)(sizeof(temp_root) / sizeof(temp_root[0])),
            temp_root) == 0) {
        vape_log(L"GetTempPathW failed: %lu", GetLastError());
        return 0;
    }
    _snwprintf_s(temp_directory,
            sizeof(temp_directory) / sizeof(temp_directory[0]), _TRUNCATE,
            L"%lsVape421Recovery", temp_root);
    if (!CreateDirectoryW(temp_directory, NULL)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        vape_log(L"CreateDirectoryW failed: %lu", GetLastError());
        return 0;
    }
    if (_snwprintf_s(jar_path, jar_capacity, _TRUNCATE,
            L"%ls\\vape421-product-%lu.jar", temp_directory,
            GetCurrentProcessId()) < 0) {
        vape_log(L"temporary product JAR path is too long");
        return 0;
    }
    file = CreateFileW(jar_path, GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_DELETE, NULL, CREATE_ALWAYS,
            FILE_ATTRIBUTE_TEMPORARY, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        vape_log(L"CreateFileW for embedded product JAR failed: %lu",
                GetLastError());
        return 0;
    }
    while (offset < size) {
        DWORD written = 0;
        DWORD remaining = size - offset;
        if (!WriteFile(file, bytes + offset, remaining, &written, NULL)
                || written == 0) {
            vape_log(L"WriteFile for embedded product JAR failed: %lu",
                    GetLastError());
            goto cleanup;
        }
        offset += written;
    }
    if (!FlushFileBuffers(file)) {
        vape_log(L"FlushFileBuffers for embedded product JAR failed: %lu",
                GetLastError());
        goto cleanup;
    }
    result = 1;

cleanup:
    CloseHandle(file);
    if (!result) {
        DeleteFileW(jar_path);
    } else {
        vape_log(L"materialized embedded product JAR: %ls (%lu bytes)",
                jar_path, size);
    }
    return result;
}

static int class_loader_can_load_minecraft(JNIEnv *env, jobject loader) {
    static const char *anchors[] = {
        "net.minecraft.client.Minecraft",
        "net.minecraft.client.MinecraftClient",
        "net.minecraft.client.main.Main"
    };
    jclass loader_class;
    jmethodID load_class;
    size_t index;

    if (env == NULL || loader == NULL) {
        return 0;
    }
    loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    if (loader_class == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return 0;
    }
    load_class = (*env)->GetMethodID(env, loader_class, "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;");
    if (load_class == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        (*env)->DeleteLocalRef(env, loader_class);
        return 0;
    }
    for (index = 0; index < sizeof(anchors) / sizeof(anchors[0]); ++index) {
        jstring name = (*env)->NewStringUTF(env, anchors[index]);
        jobject loaded;
        if (name == NULL) {
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            }
            continue;
        }
        loaded = (*env)->CallObjectMethod(env, loader, load_class, name);
        (*env)->DeleteLocalRef(env, name);
        if (loaded != NULL && !(*env)->ExceptionCheck(env)) {
            (*env)->DeleteLocalRef(env, loaded);
            (*env)->DeleteLocalRef(env, loader_class);
            return 1;
        }
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (loaded != NULL) {
            (*env)->DeleteLocalRef(env, loaded);
        }
    }
    (*env)->DeleteLocalRef(env, loader_class);
    return 0;
}

static void diagnose_loaded_minecraft_classes(void) {
    jint class_count = 0;
    jclass *classes = NULL;
    jint index;
    int matches = 0;
    if (g_jvmti == NULL
            || (*g_jvmti)->GetLoadedClasses(g_jvmti, &class_count, &classes)
                    != JVMTI_ERROR_NONE
            || classes == NULL) {
        return;
    }
    for (index = 0; index < class_count; ++index) {
        char *signature = NULL;
        if ((*g_jvmti)->GetClassSignature(g_jvmti, classes[index],
                &signature, NULL) != JVMTI_ERROR_NONE || signature == NULL) {
            continue;
        }
        if (strstr(signature, "EnumFacing") != NULL
                || strstr(signature, "net/minecraft/util/") != NULL) {
            if (matches < 64) {
                vape_log(L"loaded Minecraft utility class: %hs", signature);
            }
            ++matches;
        }
        (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)signature);
    }
    vape_log(L"loaded Minecraft utility class matches: %d", matches);
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)classes);
}

static int preferred_client_thread(const char *name) {
    return name != NULL
            && (strcmp(name, "Client thread") == 0
                    || strcmp(name, "Render thread") == 0
                    || strcmp(name, "Main thread") == 0);
}

static jobject find_client_class_loader(JNIEnv *env) {
    jint thread_count = 0;
    jthread *threads = NULL;
    jobject result = NULL;
    jvmtiError error;
    jint index;
    int best_score = -1;
    if (g_jvmti == NULL) {
        return NULL;
    }
    error = (*g_jvmti)->GetAllThreads(g_jvmti, &thread_count, &threads);
    if (error != JVMTI_ERROR_NONE || threads == NULL) {
        vape_log(L"GetAllThreads failed: %d", error);
        return NULL;
    }
    for (index = 0; index < thread_count; ++index) {
        jvmtiThreadInfo info;
        memset(&info, 0, sizeof(info));
        if ((*g_jvmti)->GetThreadInfo(g_jvmti, threads[index], &info)
                == JVMTI_ERROR_NONE) {
            if (info.context_class_loader != NULL) {
                int score = 1;
                if (preferred_client_thread(info.name)) {
                    score += 4;
                }
                if (class_loader_can_load_minecraft(env,
                        info.context_class_loader)) {
                    score += 8;
                }
                if (score > best_score) {
                    if (result != NULL) {
                        (*env)->DeleteLocalRef(env, result);
                    }
                    result = (*env)->NewLocalRef(env,
                            info.context_class_loader);
                    best_score = result == NULL ? best_score : score;
                }
            }
            if (info.name != NULL) {
                (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)info.name);
            }
            if (info.thread_group != NULL) {
                (*env)->DeleteLocalRef(env, info.thread_group);
            }
            if (info.context_class_loader != NULL) {
                (*env)->DeleteLocalRef(env, info.context_class_loader);
            }
        }
        (*env)->DeleteLocalRef(env, threads[index]);
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)threads);

    if (result == NULL) {
        jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
        jmethodID system_loader = loader_class == NULL ? NULL
                : (*env)->GetStaticMethodID(env, loader_class,
                        "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
        if (system_loader != NULL) {
            result = (*env)->CallStaticObjectMethod(env, loader_class,
                    system_loader);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
                result = NULL;
            }
        } else if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (loader_class != NULL) {
            (*env)->DeleteLocalRef(env, loader_class);
        }
    }
    vape_log(L"selected Minecraft ClassLoader with score %d", best_score);
    return result;
}

static int add_jar_to_loader(
        JNIEnv *env, jobject *loader, const wchar_t *jar_path) {
    jclass url_loader_class;
    jclass url_class;
    jclass file_class;
    jclass uri_class;
    jmethodID add_url;
    jmethodID url_loader_init;
    jmethodID file_init;
    jmethodID to_uri;
    jmethodID to_url;
    jstring path;
    jobject file;
    jobject uri;
    jobject url;
    jobjectArray urls;
    jobject child_loader;
    jclass runtime_loader_class;
    jfieldID delegated_loader_field;
    jobject delegated_loader;

    url_loader_class = (*env)->FindClass(env, "java/net/URLClassLoader");
    url_class = (*env)->FindClass(env, "java/net/URL");
    if (url_loader_class == NULL || url_class == NULL) {
        vape_log_pending_exception(env, L"resolve URLClassLoader classes");
        return 0;
    }
    file_class = (*env)->FindClass(env, "java/io/File");
    uri_class = (*env)->FindClass(env, "java/net/URI");
    file_init = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "<init>", "(Ljava/lang/String;)V");
    to_uri = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "toURI", "()Ljava/net/URI;");
    to_url = uri_class == NULL ? NULL : (*env)->GetMethodID(
            env, uri_class, "toURL", "()Ljava/net/URL;");
    if (file_init == NULL || to_uri == NULL || to_url == NULL) {
        vape_log_pending_exception(env, L"resolve product JAR URL methods");
        return 0;
    }
    path = new_wide_string(env, jar_path);
    file = path == NULL ? NULL : (*env)->NewObject(env, file_class, file_init, path);
    uri = file == NULL ? NULL : (*env)->CallObjectMethod(env, file, to_uri);
    url = uri == NULL ? NULL : (*env)->CallObjectMethod(env, uri, to_url);
    if (url == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create product JAR URL");
        return 0;
    }
    if ((*env)->IsInstanceOf(env, *loader, url_loader_class)) {
        add_url = (*env)->GetMethodID(env, url_loader_class,
                "addURL", "(Ljava/net/URL;)V");
        if (add_url == NULL) {
            vape_log_pending_exception(env, L"resolve URLClassLoader.addURL");
            return 0;
        }
        (*env)->CallVoidMethod(env, *loader, add_url, url);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env, L"URLClassLoader.addURL");
            return 0;
        }
        return 1;
    }

    runtime_loader_class = (*env)->GetObjectClass(env, *loader);
    delegated_loader_field = runtime_loader_class == NULL ? NULL
            : (*env)->GetFieldID(env, runtime_loader_class,
                    "delegatedClassLoader",
                    "Lcpw/mods/modlauncher/TransformingClassLoader$DelegatedClassLoader;");
    if (delegated_loader_field != NULL) {
        delegated_loader = (*env)->GetObjectField(
                env, *loader, delegated_loader_field);
        if (delegated_loader != NULL
                && (*env)->IsInstanceOf(env, delegated_loader,
                        url_loader_class)) {
            add_url = (*env)->GetMethodID(env, url_loader_class,
                    "addURL", "(Ljava/net/URL;)V");
            if (add_url == NULL) {
                vape_log_pending_exception(env,
                        L"resolve delegated URLClassLoader.addURL");
                return 0;
            }
            (*env)->CallVoidMethod(env, delegated_loader, add_url, url);
            if ((*env)->ExceptionCheck(env)) {
                vape_log_pending_exception(env,
                        L"delegated URLClassLoader.addURL");
                return 0;
            }
            vape_log(L"appended product JAR to ModLauncher delegated ClassLoader");
            return 1;
        }
    } else if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    url_loader_init = (*env)->GetMethodID(env, url_loader_class, "<init>",
            "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    urls = (*env)->NewObjectArray(env, 1, url_class, NULL);
    if (url_loader_init == NULL || urls == NULL) {
        vape_log_pending_exception(env, L"resolve child URLClassLoader constructor");
        return 0;
    }
    (*env)->SetObjectArrayElement(env, urls, 0, url);
    child_loader = (*env)->NewObject(env, url_loader_class, url_loader_init,
            urls, *loader);
    if (child_loader == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create child product URLClassLoader");
        return 0;
    }
    (*env)->DeleteLocalRef(env, *loader);
    *loader = child_loader;
    vape_log(L"created child URLClassLoader for non-URL Minecraft ClassLoader");
    return 1;
}

static jclass load_bridge_class(JNIEnv *env, jobject loader) {
    jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = (*env)->NewStringUTF(env, "gg.vape.runtime.NativeBridge");
    jclass result;
    if (load_class == NULL || name == NULL) {
        return NULL;
    }
    result = (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
    if (result == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"load gg.vape.runtime.NativeBridge");
        return NULL;
    }
    return result;
}

static int set_current_context_class_loader(JNIEnv *env, jobject loader) {
    jclass thread_class = (*env)->FindClass(env, "java/lang/Thread");
    jmethodID current_thread;
    jmethodID set_context_loader;
    jobject thread;
    if (thread_class == NULL) {
        vape_log_pending_exception(env, L"resolve java.lang.Thread");
        return 0;
    }
    current_thread = (*env)->GetStaticMethodID(env, thread_class,
            "currentThread", "()Ljava/lang/Thread;");
    set_context_loader = (*env)->GetMethodID(env, thread_class,
            "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    if (current_thread == NULL || set_context_loader == NULL) {
        vape_log_pending_exception(env, L"resolve Thread context ClassLoader methods");
        return 0;
    }
    thread = (*env)->CallStaticObjectMethod(env, thread_class, current_thread);
    if (thread == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"Thread.currentThread");
        return 0;
    }
    (*env)->CallVoidMethod(env, thread, set_context_loader, loader);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"Thread.setContextClassLoader");
        return 0;
    }
    return 1;
}

static int pin_native_module(void) {
    HMODULE pinned = NULL;
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS
                    | GET_MODULE_HANDLE_EX_FLAG_PIN,
            (LPCWSTR)(const void *)&g_module, &pinned)) {
        vape_log(L"GetModuleHandleExW(PIN) failed: %lu", GetLastError());
        return 0;
    }
    return 1;
}

static int call_bridge_start(JNIEnv *env, jclass bridge_class) {
    jmethodID start = (*env)->GetStaticMethodID(env, bridge_class, "start", "()V");
    if (start == NULL) {
        vape_log_pending_exception(env, L"resolve NativeBridge.start");
        return 0;
    }
    (*env)->CallStaticVoidMethod(env, bridge_class, start);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"NativeBridge.start");
        return 0;
    }
    return 1;
}

static DWORD WINAPI bootstrap_thread(LPVOID parameter) {
    HMODULE jvm_module;
    FARPROC created_vms_address;
    typedef jint (JNICALL *get_created_vms_fn)(JavaVM **, jsize, jsize *);
    get_created_vms_fn get_created_vms;
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jsize vm_count = 0;
    jobject loader = NULL;
    jclass bridge_class = NULL;
    wchar_t jar_path[MAX_PATH];
    int attached = 0;
    int registered = 0;
    int completed = 0;
    int attempt;
    HMODULE worker_module = (HMODULE)parameter;
    DWORD exit_code = 1;

    if (!vape_loader_bootstrap_initialize()) {
        vape_log(L"Offline bootstrap is invalid");
        exit_code = 6;
        goto cleanup;
    }
    Sleep(150);
    if (InterlockedCompareExchange(&g_loaded_by_jni, 0, 0) != 0) {
        return 0;
    }
    for (attempt = 0; attempt < 600; ++attempt) {
        jvm_module = GetModuleHandleW(L"jvm.dll");
        if (jvm_module != NULL) break;
        Sleep(100);
    }
    if (jvm_module == NULL) {
        vape_log(L"jvm.dll is not loaded");
        exit_code = 2;
        goto cleanup;
    }
    vape_loader_report_progress(VAPE421_BOOT_PROGRESS_JVM);
    created_vms_address = GetProcAddress(jvm_module, "JNI_GetCreatedJavaVMs");
    if (created_vms_address == NULL) {
        vape_log(L"JNI_GetCreatedJavaVMs export is unavailable");
        exit_code = 3;
        goto cleanup;
    }
    get_created_vms = (get_created_vms_fn)created_vms_address;
    for (attempt = 0; attempt < 600; ++attempt) {
        if (get_created_vms(&vm, 1, &vm_count) == JNI_OK
                && vm != NULL && vm_count >= 1) {
            break;
        }
        vm = NULL;
        vm_count = 0;
        Sleep(100);
    }
    if (vm == NULL || vm_count < 1) {
        vape_log(L"JNI_GetCreatedJavaVMs returned no VM");
        exit_code = 4;
        goto cleanup;
    }
    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&env, NULL) != JNI_OK
            || env == NULL) {
        vape_log(L"AttachCurrentThreadAsDaemon failed");
        exit_code = 5;
        goto cleanup;
    }
    attached = 1;
    if (vape_initialize_jvmti(vm) != JNI_OK) {
        goto cleanup;
    }
    vape_loader_report_progress(VAPE421_BOOT_PROGRESS_JVMTI);
    if (!materialize_embedded_product_jar(jar_path,
            sizeof(jar_path) / sizeof(jar_path[0]))) {
        goto cleanup;
    }
    for (attempt = 0; attempt < 600 && loader == NULL; ++attempt) {
        loader = find_client_class_loader(env);
        if (loader == NULL) {
            Sleep(100);
        }
    }
    if (loader == NULL) {
        vape_log(L"Minecraft ClassLoader was not found within 60 seconds");
        goto cleanup;
    }
    diagnose_loaded_minecraft_classes();
    vape_loader_report_progress(VAPE421_BOOT_PROGRESS_CLASSLOADER);
    if (!add_jar_to_loader(env, &loader, jar_path)) {
        goto cleanup;
    }
    if (!set_current_context_class_loader(env, loader)) {
        goto cleanup;
    }
    bridge_class = load_bridge_class(env, loader);
    if (bridge_class == NULL) {
        goto cleanup;
    }
    if (vape_register_native_bridge(env, bridge_class) != JNI_OK) {
        goto cleanup;
    }
    registered = 1;
    vape_loader_report_progress(VAPE421_BOOT_PROGRESS_PAYLOAD);
    if (!pin_native_module()) {
        goto cleanup;
    }
    vape_log(L"NativeBridge linked from %ls", jar_path);
    if (!call_bridge_start(env, bridge_class)) {
        goto cleanup;
    }
    vape_loader_report_progress(VAPE421_BOOT_PROGRESS_JAVA);
    vape_loader_report_completed();
    vape_log(L"NativeBridge.start completed; injection is active");
    completed = 1;
    exit_code = 0;

cleanup:
    if (!completed) {
        char failure[96];
        _snprintf_s(failure, sizeof(failure), _TRUNCATE,
                "Native bootstrap failed with code %lu", exit_code);
        vape_loader_report_failure(failure);
    }
    if (attached) {
        (*vm)->DetachCurrentThread(vm);
    }
    if (!registered && worker_module != NULL) {
        vape_log(L"bootstrap failed before native registration; unloading DLL");
        FreeLibraryAndExitThread(worker_module, exit_code);
    }
    if (!completed) {
        vape_log(L"bootstrap did not complete; DLL remains loaded because natives may be registered");
    }
    return exit_code;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jclass bridge_class;
    (void)reserved;
    InterlockedExchange(&g_loaded_by_jni, 1);
    if (!vape_loader_bootstrap_initialize()) {
        return JNI_ERR;
    }
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK
            || env == NULL) {
        return JNI_ERR;
    }
    if (vape_initialize_jvmti(vm) != JNI_OK) {
        return JNI_ERR;
    }
    bridge_class = (*env)->FindClass(env, "gg/vape/runtime/NativeBridge");
    if (bridge_class == NULL
            || vape_register_native_bridge(env, bridge_class) != JNI_OK) {
        vape_log_pending_exception(env, L"JNI_OnLoad NativeBridge registration");
        return JNI_ERR;
    }
    vape_log(L"JNI_OnLoad completed");
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    (void)reserved;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) == JNI_OK) {
        vape_release_native_bridge(env);
    }
    vape_loader_bootstrap_clear();
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    HANDLE thread;
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = instance;
        DisableThreadLibraryCalls(instance);
        thread = CreateThread(NULL, 0, bootstrap_thread, instance, 0, NULL);
        if (thread != NULL) {
            CloseHandle(thread);
        }
    } else if (reason == DLL_PROCESS_DETACH) {
        vape_loader_bootstrap_clear();
    }
    return TRUE;
}
