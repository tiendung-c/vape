# Vape 4.21 Offline — tài liệu dự án

## 1. Tổng quan

Đây là bản chạy offline của Vape 4.21. Phần Java chứa logic giao diện, module, HUD, profile và lưu cấu hình cục bộ. Phần native chịu trách nhiệm đóng gói payload Java vào DLL, kết nối DLL với JVM Minecraft và báo trạng thái cho injector.

Bản này không dùng HTTP API, Microsoft/Xbox/Minecraft authentication hoặc Zeus protocol. Profile, settings và auto-save đều hoạt động trên máy cục bộ. Injector dùng cơ chế Win32/JNI/JVMTI thông thường để nạp DLL; tài liệu này không mô tả hay triển khai cơ chế né anti-cheat, stealth injection hoặc bypass bảo vệ của game.

### Account crack/offline cho Minecraft 1.8.9

Bản 1.8.9 có account switcher cục bộ, không đăng nhập Microsoft/Xbox và không gửi thông tin tài khoản ra mạng.

- Danh sách account được lưu tại `%APPDATA%\Vape\accounts.json`.
- Mỗi account chỉ cần tên Minecraft hợp lệ: 1–16 ký tự, gồm chữ, số và `_`.
- Bấm icon account trên thanh menu phía trên của Click GUI để chuyển sang account kế tiếp.
- UUID được tạo theo chuẩn `OfflinePlayer:<tên>`, giống cơ chế offline của Minecraft.
- Sau khi đổi tên, cần thoát vào lại world/server để player profile hiện tại được tạo lại.

Ví dụ `accounts.json`:

```json
{
  "active": "Player_Two",
  "accounts": [
    { "name": "Player_One" },
    { "name": "Player_Two" }
  ]
}
```

Chức năng này chỉ đổi session cục bộ của Minecraft 1.8.9. Nó không biến server online-mode thành offline và không thể thay thế xác thực tài khoản thật.

## 2. Kiến trúc tổng thể

```text
Mã Java
  │
  ├─ Gradle compileJava (--release 8)
  ├─ ShadowJar: gom dependency và relocate package
  ├─ verifyInjectionPayload: kiểm tra class/runtime của payload
  │
  └─ CMake + MSVC x64
       └─ payload.rc nhúng JAR vào Vape421Native.dll

Vape421Injector.exe
  ├─ tự tìm java.exe/javaw.exe có dấu hiệu là Minecraft
  ├─ nạp Vape421Native.dll vào JVM đích
  └─ chờ DLL báo LoadLibrary và Java bootstrap hoàn tất

Vape421Native.dll trong javaw.exe
  ├─ chờ JVM/JNI/JVMTI sẵn sàng
  ├─ giải nén JAR nhúng vào thư mục tạm
  ├─ tìm ClassLoader của Minecraft
  ├─ đăng ký NativeBridge
  └─ gọi NativeBridge.start() để khởi động phần Java
```

## 3. Cơ chế build Java thành DLL

Java không được biên dịch trực tiếp thành mã máy DLL. Quy trình hiện tại là đóng gói JAR Java vào tài nguyên của một DLL native, sau đó DLL dùng JNI/JVMTI để nạp JAR vào JVM Minecraft.

### Bước 1 — Biên dịch Java

Gradle biên dịch mã trong `src/main/java` và đóng gói resource trong `src/main/resources`. Khi build bản injection, project dùng Java 8 bytecode (`--release 8`) để tương thích với JVM Minecraft/Badlion đang chạy Java 8.

### Bước 2 — Tạo injection JAR

Task `injectionJar` dùng ShadowJar để tạo payload độc lập. Các dependency cần thiết được gom vào JAR; các package có thể xung đột với game được relocate, ví dụ:

- `javassist` → `gg.vape.shaded.javassist`
- `org.objectweb.asm` → `gg.vape.shaded.org.objectweb.asm`

Task `verifyInjectionPayload` kiểm tra payload có `gg/vape/runtime/NativeBridge.class`, đủ nhóm runtime cần thiết và không chứa class version cao hơn Java 8.

### Bước 3 — Nhúng JAR vào DLL

CMake nhận đường dẫn JAR qua biến `VAPE421_PRODUCT_JAR`. File `native/payload.rc.in` tạo một resource kiểu `RCDATA` có mã `421`. MSVC sau đó biên dịch resource này vào `Vape421Native.dll` cùng các file C native.

Kết quả là DLL chứa sẵn payload Java, nên khi phát hành bundle chỉ cần DLL và injector. Khi DLL chạy trong JVM, payload được materialize thành file tạm dạng:

```text
%TEMP%\Vape421Recovery\vape421-product-<pid>.jar
```

JAR tạm này chỉ là bản phục vụ bootstrap của tiến trình hiện tại. DLL dùng `DeleteFile`/cleanup theo vòng đời phù hợp; thư mục tạm có thể còn lại nếu JVM bị dừng đột ngột để hỗ trợ chẩn đoán.

## 4. Cơ chế injector vào Minecraft Java

`Vape421Injector.exe` là chương trình native chạy bên ngoài game.

1. Injector liệt kê các tiến trình `java.exe` và `javaw.exe`.
2. Với mỗi tiến trình, injector đọc command line từ PEB bằng native process query và chấm điểm các dấu hiệu như `minecraft`, `net.minecraft`, `com.mojang`, `badlion`, `lunar`, `forge` hoặc `launchwrapper`.
3. Nếu chỉ có một JVM Minecraft phù hợp, PID được chọn tự động. Nếu có nhiều ứng viên, injector hiển thị danh sách để chọn.
4. Injector kiểm tra tiến trình là 64-bit phù hợp, mở process với quyền cần cho việc nạp DLL, cấp vùng nhớ từ xa và ghi đường dẫn DLL.
5. Injector lấy địa chỉ `kernel32!LoadLibraryW` và tạo remote thread để Windows nạp `Vape421Native.dll` vào tiến trình đích.
6. Injector chờ tối đa 30 giây cho `LoadLibraryW` hoàn tất.
7. Sau khi DLL đã được map, injector chờ tối đa 5 phút để nhận sự kiện Java bootstrap thành công hoặc thất bại.
8. Chỉ khi DLL báo `NativeBridge.start completed; injection is active` thì command line injector mới in thông báo hoàn tất.

Đây là cơ chế nạp DLL Win32 tiêu chuẩn, không phải kỹ thuật ẩn tiến trình hay né phát hiện. DLL và injector phải cùng kiến trúc x64 với JVM Minecraft.

## 5. Cơ chế bootstrap bên trong DLL

Khi Windows gọi `DllMain`, DLL không khởi động toàn bộ logic trên loader lock. Nó tạo worker thread riêng để bootstrap an toàn hơn.

Worker thread thực hiện các bước sau:

1. Chờ `jvm.dll` xuất hiện trong tiến trình.
2. Gọi `JNI_GetCreatedJavaVMs` để lấy JVM đang chạy và attach worker bằng `AttachCurrentThreadAsDaemon`.
3. Lấy và khởi tạo JVMTI để hỗ trợ theo dõi class, bytecode và runtime hook cần thiết của project.
4. Giải nén JAR embedded vào `%TEMP%\Vape421Recovery`.
5. Chọn ClassLoader của Minecraft dựa trên thread context, tên loader và điểm nhận diện runtime.
6. Thêm JAR vào URLClassLoader hoặc delegated/child loader phù hợp, đồng thời đặt context ClassLoader cho thread bootstrap.
7. Load `gg.vape.runtime.NativeBridge` bằng chính ClassLoader của Minecraft.
8. Đăng ký các native method giữa Java và C, sau đó gọi `NativeBridge.start()`.
9. Java đọc mapping/runtime, khởi tạo account offline, manager, module và chờ Minecraft có world.
10. Khi world được phát hiện, DLL báo trạng thái injection đã sẵn sàng.

Các mốc log chính:

```text
BOOT DETECT_RUNTIME
BOOT LOAD_MAPPINGS
BOOT INIT_ACCOUNT_OFFLINE
BOOT INIT_MANAGERS
BOOT JAVA_READY_WAITING_FOR_WORLD
NativeBridge.start completed; injection is active
Minecraft world detected; injection is ready
```

Vì manager và wrapper phụ thuộc mapping/version Minecraft, log `Failed to find ... item(s)` có thể xuất hiện khi một số class hoặc item không có trong runtime hiện tại. Đây là cảnh báo mapping, không tự động có nghĩa DLL chưa được nạp.

## 6. NativeBridge làm gì?

`gg.vape.runtime.NativeBridge` là điểm nối giữa phần Java và native:

- đăng ký native method;
- gọi các hàm JNI/JVMTI từ Java;
- theo dõi và hỗ trợ load/retransform class;
- kết nối input/render bridge;
- gửi log, progress, completion và failure về native bootstrap/injector;
- kiểm soát thứ tự khởi động để injector chỉ báo thành công khi Java đã chạy.

Kênh loopback native nếu được dùng chỉ phục vụ progress, completion và failure nội bộ. Nó không phải HTTP API, không thực hiện đăng nhập online và không thay thế cơ chế lưu offline.

## 7. Profile và cấu hình offline

Thư mục cấu hình mặc định là:

```text
%APPDATA%\Vape\
├─ settings.json
├─ Default.json
├─ My_Profile.json
└─ Practice_1.json
```

- `settings.json` lưu settings chung của client, ví dụ profile đang chọn và tùy chọn giao diện.
- Mỗi profile là một file JSON riêng, chứa trạng thái module/HUD/legit và các giá trị setting của profile đó.
- Trên Click GUI, mục `Profiles` được hiển thị là `Config`; danh sách được đồng bộ trực tiếp với các file JSON trong thư mục `%APPDATA%\Vape`.
- Khi xóa file config khỏi thư mục, card config tương ứng sẽ biến mất khỏi menu; khi đặt thêm file JSON hợp lệ, tên trong trường `name` sẽ tự xuất hiện sau lần quét kế tiếp.
- Khi tạo profile, profile được lưu ngay vào file riêng.
- Khi xóa profile, file JSON của profile bị xóa cùng profile.
- Khi đặt thêm một file JSON hợp lệ vào thư mục, `ProfilesManager` có thể load profile đó ở lần refresh/load tiếp theo.
- Tên file lấy từ tên profile; dấu cách và ký tự không hợp lệ trên Windows được đổi thành `_`.
- Tên `settings` được dành riêng cho file chung và được đổi thành `profile_settings` khi dùng làm tên profile.
- Ghi file dùng Gson pretty JSON và cơ chế ghi tạm rồi thay thế để giảm nguy cơ file bị dở dang.
- `SyncThread` debounce auto-save để không ghi đĩa ở mỗi thay đổi nhỏ, nhưng vẫn lưu profile và settings cục bộ.
- Nút `Folder` trong phần Config mở trực tiếp thư mục `%APPDATA%\Vape`.

Nếu còn settings dạng monolithic từ phiên bản cũ, loader có logic migration để đọc profile cũ rồi tách dần sang file JSON riêng.

## 8. Tính năng chính

Danh sách dưới đây là các nhóm tính năng có trong source hiện tại; khả năng hoạt động cụ thể phụ thuộc mapping và phiên bản Minecraft.

### Legit mode và combat

Aim Assist, Auto Clicker, Triggerbot, Hit Select, Velocity, WTap, Sprint, Reach, Hit Boxes, Silent Aura cùng các tùy chọn điều khiển combat. Có FOV Lock để giới hạn FOV của module theo mức FOV mà settings game cho phép.

### Movement và blatant

Kill Aura, Scaffold, Fly, Speed, NoFall, NoSlowdown, InvWalk, Step, LongJump, Blink, Safe Walk và các module movement khác có trong từng mapping.

### Render và HUD

ESP, NameTags, Chams, Fullbright, Freecam, Arrows, Tracers, Storage ESP, Item ESP; Armor Status, Block Overlay, Hit Color, Clock, Compass, Coords, FPS, FPS Boost, Keystrokes, Potion Status, Scoreboard, Reach Display, Time Changer, Weather và các widget HUD.

Ping HUD hiển thị độ trễ của người chơi dựa trên phản hồi/latency mà client nhận từ server khi đang kết nối. Khi offline hoặc chưa có network handler, giá trị có thể hiển thị `N/A` thay vì tự tạo ping giả.

### Utility và world

Auto Armor, Auto Fish, Auto Hotbar, Auto Pearl, Auto Tool, Auto Totem, Block-In/Clutch, Inv Cleaner, Inventory Manager, MLG, Panic, Parkour, Refill, Throw Debuff, Throwpot, WindCharge, Anti-AFK, Bed Breaker, Chest Steal, FakeLag, FastPlace, Murderer Finder và Xray.

### Giao diện và trải nghiệm

Click GUI, Text GUI, tìm kiếm module, keybind, multi-keybinding, HUD editor, theme/font, sound, notification, toggle sprint, inventory blur, cooldown, no hurt cam, no jump delay, clear water và các tùy chọn hiển thị khác.

## 9. Cấu trúc project

```text
VapeV4.21-main/
├─ build.gradle                  # pipeline Java, ShadowJar, CMake và bundle
├─ settings.gradle
├─ gradlew.bat                   # Gradle wrapper trên Windows
├─ README.md                     # tài liệu tổng hợp này
├─ src/
│  ├─ main/java/gg/vape/
│  │  ├─ runtime/                # NativeBridge và lifecycle bootstrap Java
│  │  ├─ config/                 # Profile, VapeStorage, JSON codec
│  │  ├─ sync/                   # SyncThread và auto-save offline
│  │  ├─ module/                 # module combat, movement, render, utility, world...
│  │  ├─ manager/                # khởi tạo và quản lý module/profile
│  │  ├─ mapping/                # mapping class/method/field theo runtime
│  │  ├─ wrapper/                # wrapper Minecraft/LWJGL
│  │  ├─ asm/                    # bytecode transformation
│  │  ├─ event/                  # event bus và game events
│  │  ├─ ui/                     # GUI, HUD, config screen
│  │  ├─ input/                  # key/mouse input
│  │  ├─ render/                 # rendering helpers
│  │  └─ utils/, value/, ...      # tiện ích và kiểu setting
│  └─ main/resources/            # resource, font, mapping và metadata Java
├─ native/
│  ├─ CMakeLists.txt              # cấu hình MSVC/JNI/JVMTI và target native
│  ├─ injector.c                  # detect JVM, chọn PID và nạp DLL
│  ├─ dllmain.c                   # DLL entry point và JVM bootstrap
│  ├─ loader_bootstrap.c/.h       # trạng thái/progress loopback nội bộ
│  ├─ native_bridge.c             # cầu JNI/JVMTI và native methods
│  ├─ payload.rc.in               # nhúng injection JAR thành RCDATA
│  └─ README.md                   # hướng dẫn riêng cho native
└─ build/
   ├─ libs/                       # JAR Java sau khi build
   ├─ native/                     # CMake build tree và test native
   └─ injection/                  # bundle phát hành cuối cùng
      ├─ Vape421Injector.exe
      ├─ Vape421Native.dll
      └─ README.md
```

## 10. Yêu cầu build

- Windows x64.
- JDK 17 để chạy Gradle và biên dịch project.
- JDK 8, có JNI/JVMTI headers, để target bytecode Java 8 và build native. Có thể dùng `C:\Program Files\Java\jdk1.8.0_202` hoặc đặt đường dẫn khác qua `-PnativeJavaHome`.
- Visual Studio Build Tools/MSVC và CMake.
- JVM Minecraft phải cùng kiến trúc x64 với DLL.

## 11. Build bundle injection

Chạy từ thư mục gốc project:

```powershell
.\gradlew.bat prepareInjectionBundle `
  -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_202" `
  --no-daemon
```

Kết quả chính nằm tại:

```text
build\injection\Vape421Injector.exe
build\injection\Vape421Native.dll
build\injection\README.md
```

`prepareInjectionBundle` là task phát hành canonical. Không cần copy thủ công sang `build\injection-config` hoặc tạo thêm folder staging.

### Tải bản phát hành

Binary không nằm trong Git vì thư mục `build/` bị loại khỏi source commit. Khi tải bản phát hành từ [GitHub Releases](https://github.com/tiendung-c/vape-source/releases), bắt buộc tải đủ cả hai file sau:

```text
Vape421Injector.exe
Vape421Native.dll
```

Đặt hai file trong cùng một thư mục rồi chạy `Vape421Injector.exe`. Không chỉ tải riêng EXE hoặc DLL; injector cần DLL cùng thư mục để nạp vào JVM Minecraft.

## 12. Kiểm thử native

Sau khi build:

```powershell
ctest --test-dir build/native -C Release --output-on-failure
```

Các test kiểm tra payload resource, kích thước/kiến trúc DLL và các điều kiện native cơ bản. Nếu build báo không thể ghi đè file trong `build\injection`, hãy đóng injector/Minecraft đang dùng DLL đó rồi chạy lại.

## 13. Chẩn đoán lỗi thường gặp

- `DLL mapped` nhưng chưa có `NativeBridge.start completed`: DLL đã được nạp, nhưng JVM bootstrap hoặc Java initialization chưa xong. Xem log trong `%TEMP%\Vape421Recovery`.
- `selected Minecraft ClassLoader` nhưng `initializeManagers` lỗi: thường là mapping hoặc wrapper không khớp runtime. Kiểm tra phiên bản game/launcher và stack trace đầu tiên.
- `EnumFacing runtime owner unavailable`: wrapper chưa tìm thấy runtime owner; cần sửa mapping/wrapper tương ứng thay vì chỉ bỏ qua toàn bộ manager.
- Injector không tìm thấy PID: khởi động Minecraft đến khi `javaw.exe` có command line chứa token nhận diện, hoặc chọn PID thủ công nếu có nhiều JVM.
- Profile không hiện: kiểm tra file có đuôi `.json`, JSON hợp lệ và nằm trực tiếp trong `%APPDATA%\Vape`.
- Ping là `N/A`: game đang ở menu/offline hoặc runtime chưa có network handler/response.

## 14. Tóm tắt một câu

Gradle tạo payload Java tương thích Java 8, ShadowJar gom dependency, CMake/MSVC nhúng JAR vào DLL x64, injector nạp DLL vào `javaw.exe`, rồi DLL dùng JNI/JVMTI và ClassLoader của Minecraft để gọi `NativeBridge.start()`; toàn bộ profile và settings được lưu offline dưới dạng JSON.
