# Native injector và DLL bridge — Vape 4.21 Offline

Thư mục này chứa phần native dùng để đóng gói payload Java, nạp DLL vào JVM Minecraft và nối Java với JNI/JVMTI. Đây là tài liệu native rút gọn; tài liệu đầy đủ nằm ở README tại thư mục gốc.

## Thành phần

- `injector.c`: tìm `java.exe`/`javaw.exe`, nhận diện JVM Minecraft, chọn PID và gọi `LoadLibraryW` trong tiến trình đích.
- `dllmain.c`: entry point của DLL, tạo worker thread và khởi động JVM bootstrap.
- `loader_bootstrap.c/.h`: trạng thái progress, completion và failure nội bộ.
- `native_bridge.c`: đăng ký native method, JNI/JVMTI bridge, class transform và log native.
- `payload.rc.in`: nhúng injection JAR vào resource `RCDATA` của DLL.
- `CMakeLists.txt`: yêu cầu MSVC x64, JNI/JVMTI headers và đường dẫn JAR.

## Java được nhúng vào DLL như thế nào?

Java được build thành injection JAR trước. CMake nhận JAR qua `VAPE421_PRODUCT_JAR`, sau đó resource compiler nhúng JAR vào DLL với mã resource `421`. Vì vậy DLL phát hành đã chứa payload Java.

Khi DLL chạy trong JVM, nó đọc resource, ghi ra:

```text
%TEMP%\Vape421Recovery\vape421-product-<pid>.jar
```

Sau đó DLL tìm JVM hiện tại bằng `JNI_GetCreatedJavaVMs`, attach thread, khởi tạo JVMTI, chọn ClassLoader Minecraft, load `gg.vape.runtime.NativeBridge` và gọi `NativeBridge.start()`.

## Luồng injector

1. Tự động liệt kê `java.exe` và `javaw.exe`.
2. Đọc command line/metadata để chấm điểm dấu hiệu Minecraft, Badlion, Lunar, Forge hoặc LaunchWrapper.
3. Chọn PID duy nhất hoặc hiển thị danh sách khi có nhiều JVM.
4. Mở process, cấp vùng nhớ, ghi đường dẫn DLL.
5. Dùng `kernel32!LoadLibraryW` qua remote thread để nạp DLL.
6. Chờ tối đa 30 giây cho DLL được map.
7. Chờ tối đa 5 phút cho Java bootstrap báo thành công/thất bại.

Console chỉ báo injection hoàn tất khi nhận được trạng thái Java tương ứng, không chỉ dựa vào việc remote thread đã kết thúc. Cơ chế này là Win32/JNI/JVMTI tiêu chuẩn; native code không triển khai stealth injection hoặc bypass anti-cheat.

## Build

Chạy tại thư mục gốc:

```powershell
.\gradlew.bat prepareInjectionBundle `
  -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_202" `
  --no-daemon
```

Bundle cuối cùng:

```text
build\injection\Vape421Injector.exe
build\injection\Vape421Native.dll
build\injection\README.md
```

## Kiểm thử

```powershell
ctest --test-dir build/native -C Release --output-on-failure
```

## Log bootstrap

```text
BOOT DETECT_RUNTIME
BOOT LOAD_MAPPINGS
BOOT INIT_ACCOUNT_OFFLINE
BOOT INIT_MANAGERS
BOOT JAVA_READY_WAITING_FOR_WORLD
NativeBridge.start completed; injection is active
Minecraft world detected; injection is ready
```

`BOOT INIT_ACCOUNT_OFFLINE` xác nhận phần account không gọi dịch vụ đăng nhập online. Profile/settings được Java lưu ở `%APPDATA%\Vape` dưới dạng JSON, không do native layer quản lý.

Nếu không thể ghi đè file trong `build\injection`, hãy đóng `Vape421Injector.exe` và Minecraft đang giữ DLL rồi chạy lại task build.

