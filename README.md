# Vape 4.21 Offline

## Giới thiệu

Đây là project Java/native chạy offline. Phần Java chứa module, giao diện, HUD, mapping, profile và cấu hình cục bộ. Phần native đóng gói payload Java vào DLL và cung cấp injector để nạp DLL vào JVM Minecraft tương thích.

Project hiện tập trung vào:

- Lưu profile và settings cục bộ dưới dạng JSON.
- Hỗ trợ runtime Minecraft 1.8.9.
- Hỗ trợ Minecraft 1.21.11 Fabric/Knot và mapping hiện đại.
- Kết nối Java–native qua JNI/JVMTI.
- Build payload Java thành JAR, sau đó nhúng JAR vào DLL x64.

Đây là project dành cho môi trường thử nghiệm/offline do người dùng kiểm soát. Không bao gồm dịch vụ HTTP, Microsoft/Xbox authentication hoặc Zeus protocol.

## Cấu trúc project

```text
VapeV4.21-main/
├─ build.gradle                 # Pipeline Gradle, ShadowJar và build native
├─ settings.gradle              # Repository và cấu hình Gradle
├─ gradlew.bat                  # Gradle wrapper cho Windows
├─ README.md                    # Tài liệu project
│
├─ src/main/java/gg/vape/
│  ├─ runtime/                  # NativeBridge và vòng đời bootstrap Java
│  ├─ reflect/                  # Resolver mapping runtime
│  │  ├─ Vanilla189Mappings.java
│  │  ├─ Vanilla12111Mappings.java
│  │  └─ Fabric12111Mappings.java
│  ├─ mapping/                  # Mapping task và bytecode mapping
│  ├─ manager/                  # Khởi tạo, đăng ký và quản lý module
│  ├─ module/                   # Module combat, movement, render, utility...
│  ├─ config/                   # Profile, JSON codec và storage
│  ├─ sync/                     # Auto-save offline và đồng bộ file cục bộ
│  ├─ wrapper/                  # Wrapper truy cập Minecraft runtime
│  ├─ asm/                      # Transformer và bytecode hook
│  ├─ event/                    # Event bus và event game
│  ├─ ui/                       # Click GUI, menu config và HUD editor
│  ├─ render/                   # Backend render và OpenGL helper
│  ├─ input/                    # Xử lý bàn phím, chuột và keybind
│  ├─ combat/, rotation/        # Logic combat và rotation
│  └─ utils/, value/, ...       # Tiện ích và kiểu setting
│
├─ src/main/resources/
│  ├─ mappings/                 # Mapping CSV/SRG cho từng runtime
│  ├─ fonts/                    # Font giao diện
│  └─ assets/                   # Icon, texture và resource GUI
│
├─ native/
│  ├─ CMakeLists.txt            # Cấu hình target MSVC/JNI/JVMTI
│  ├─ injector.c                # Tìm JVM Minecraft và nạp DLL
│  ├─ dllmain.c                 # DLL entry point và bootstrap worker
│  ├─ native_bridge.c           # Native method và JNI/JVMTI bridge
│  ├─ loader_bootstrap.c/.h     # Trạng thái bootstrap và progress
│  ├─ payload.rc.in             # Nhúng JAR vào DLL dưới dạng resource
│  └─ README.md                 # Tài liệu riêng cho native layer
│
└─ build/
   ├─ libs/                     # JAR sau khi compile
   ├─ native/                   # CMake build tree và native test
   └─ injection/                # Bundle dùng để chạy
      ├─ Vape421Injector.exe
      ├─ Vape421Native.dll
      └─ README.md
```

## Luồng build

```text
Java source
   │
   ├─ Gradle compileJava --release 8
   ├─ ShadowJar gom và relocate dependency
   ├─ verifyInjectionPayload kiểm tra payload
   │
   └─ CMake + MSVC x64
        └─ Nhúng injection JAR vào Vape421Native.dll
```

Build bundle hoàn chỉnh:

```powershell
.\gradlew.bat prepareInjectionBundle
```

Kết quả nằm trong `build\injection`. Hai file `Vape421Injector.exe` và `Vape421Native.dll` phải được giữ cùng thư mục.

Payload hiện đại mặc định không đóng gói LWJGL2 để tránh xung đột với LWJGL3 của Fabric. Khi cần build riêng cho runtime 1.8.9 độc lập:

```powershell
.\gradlew.bat -PexcludeLegacyLwjgl=false prepareInjectionBundle
```

## Profile và cấu hình

Cấu hình offline mặc định nằm trong:

```text
%APPDATA%\Vape\
├─ settings.json
├─ Default.json
└─ Ten_Profile.json
```

Mỗi profile là một file JSON riêng. Tên file được tạo từ tên profile; dấu cách và ký tự không hợp lệ trên Windows được đổi thành `_`. Thêm file JSON hợp lệ vào thư mục sẽ cho phép `ProfilesManager` phát hiện profile ở lần quét tiếp theo. Xóa file sẽ loại profile khỏi danh sách.

Profile thường chứa:

- Trạng thái bật/tắt module.
- Giá trị setting của từng module.
- Keybind và activation mode.
- Trạng thái HUD/legit module.
- Thông tin lựa chọn profile hiện tại.

`SyncThread` thực hiện auto-save cục bộ có debounce để hạn chế ghi đĩa liên tục. Nút `Folder` trong menu Config mở trực tiếp thư mục cấu hình.

## Runtime và mapping

`NativeBridge` nhận diện runtime trước khi khởi tạo manager:

- Minecraft 1.8.9 dùng mapping legacy/SRG tương ứng.
- Minecraft 1.21.11 Fabric/Knot dùng mapping intermediary Fabric.
- Runtime hiện đại khác dùng mapping vanilla/Forge-compatible khi có thể.

`MappingRegistry` chọn mapping theo version và class loader đang chạy. Vì wrapper phụ thuộc runtime, log mapping không tìm thấy một số class/method không đồng nghĩa DLL chưa được nạp; cần kiểm tra lỗi đầu tiên trong bootstrap log.

## Kiểm tra sau build

```powershell
.\gradlew.bat test verifyInjectionPayload
ctest --test-dir build/native -C Release --output-on-failure
```

Nếu build không thể ghi đè file trong `build\injection`, hãy đóng injector và Minecraft đang giữ DLL rồi chạy lại task build.
