package gg.vape.runtime;

import gg.vape.Vape;
import gg.vape.reflect.Type;
import gg.vape.ui.click.GuiScreenNativeCallbackBridge;
import gg.vape.utils.Base64Util;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class NativeBridge {
    private static final String DEFAULT_CONFIG_JSON = "{"
            + "\"friends\":[],"
            + "\"profiles\":{},"
            + "\"otherdata\":[{\"frames\":["
            + "{\"title\":\"Combat\",\"x\":32,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Render\",\"x\":144,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Utility\",\"x\":256,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"World\",\"x\":368,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Inventory\",\"x\":480,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Favorites\",\"x\":592,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Settings\",\"x\":32,\"y\":32,\"visible\":false,\"pinned\":false},"
            + "{\"title\":\"ModuleSearch\",\"x\":32,\"y\":32,\"visible\":false,\"pinned\":false}"
            + "]}]}";
    private static boolean forgeAbsent = true;
    static boolean alphaTestWasEnabled;
    private static Method glGetFloatMethod;
    private static Method glGetIntegerVectorMethod;
    private static Method glVertexPointerMethod;
    private static Method glColorPointerMethod;
    private static Method glTexCoordPointerMethod;
    private static volatile String bootstrapStage = "CREATED";

    private static void resolveGlGetFloatMethod() {
        if (glGetFloatMethod != null) {
            return;
        }
        try {
            glGetFloatMethod = GL11.class.getMethod("glGetFloatv", Integer.TYPE, FloatBuffer.class);
        }
        catch (NoSuchMethodException modernNameMissing) {
            try {
                glGetFloatMethod = GL11.class.getMethod("glGetFloat", Integer.TYPE, FloatBuffer.class);
            }
            catch (NoSuchMethodException legacyNameMissing) {
                throw new IllegalStateException("Unable to resolve OpenGL float state method", legacyNameMissing);
            }
        }
    }

    private static void readGlFloats(int state, FloatBuffer destination) {
        NativeBridge.resolveGlGetFloatMethod();
        try {
            glGetFloatMethod.invoke(null, state, destination);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read OpenGL state " + state, exception);
        }
    }

    private static void readGlIntegers(int state, IntBuffer destination) {
        if (glGetIntegerVectorMethod == null) {
            try {
                glGetIntegerVectorMethod = GL11.class.getMethod(
                        "glGetIntegerv", Integer.TYPE, IntBuffer.class);
            }
            catch (NoSuchMethodException modernNameMissing) {
                try {
                    glGetIntegerVectorMethod = GL11.class.getMethod(
                            "glGetInteger", Integer.TYPE, IntBuffer.class);
                }
                catch (NoSuchMethodException legacyNameMissing) {
                    throw new IllegalStateException(
                            "Unable to resolve OpenGL integer state method", legacyNameMissing);
                }
            }
        }
        try {
            glGetIntegerVectorMethod.invoke(null, state, destination);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read OpenGL integer state " + state, exception);
        }
    }

    private static Method resolveFloatPointerMethod(String name) {
        try {
            return GL11.class.getMethod(
                    name, Integer.TYPE, Integer.TYPE, Integer.TYPE, FloatBuffer.class);
        }
        catch (NoSuchMethodException modernSignatureMissing) {
            try {
                return GL11.class.getMethod(
                        name, Integer.TYPE, Integer.TYPE, FloatBuffer.class);
            }
            catch (NoSuchMethodException legacySignatureMissing) {
                throw new IllegalStateException("Unable to resolve OpenGL pointer method " + name,
                        legacySignatureMissing);
            }
        }
    }

    private static void invokeFloatPointer(Method method, int size, int stride,
                                           FloatBuffer buffer, String name) {
        try {
            if (method.getParameterCount() == 4) {
                method.invoke(null, size, 5126, stride, buffer);
            } else {
                method.invoke(null, size, stride, buffer);
            }
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to invoke OpenGL pointer method " + name, exception);
        }
    }

    public static void vertexPointer(int size, int stride, FloatBuffer buffer) {
        if (glVertexPointerMethod == null) {
            glVertexPointerMethod = NativeBridge.resolveFloatPointerMethod("glVertexPointer");
        }
        NativeBridge.invokeFloatPointer(glVertexPointerMethod, size, stride, buffer, "glVertexPointer");
    }

    public static void colorPointer(int size, int stride, FloatBuffer buffer) {
        if (glColorPointerMethod == null) {
            glColorPointerMethod = NativeBridge.resolveFloatPointerMethod("glColorPointer");
        }
        NativeBridge.invokeFloatPointer(glColorPointerMethod, size, stride, buffer, "glColorPointer");
    }

    public static void texCoordPointer(int size, int stride, FloatBuffer buffer) {
        if (glTexCoordPointerMethod == null) {
            glTexCoordPointerMethod = NativeBridge.resolveFloatPointerMethod("glTexCoordPointer");
        }
        NativeBridge.invokeFloatPointer(glTexCoordPointerMethod, size, stride, buffer, "glTexCoordPointer");
    }

    public static int ss_3(String value) {
        return 0;
    }

    public static int sts() {
        return 1;
    }

    public static void smdp(int mode, int value) {
        NativeBridge.smd(mode, value);
    }

    //GetRenderHandler
    //Java Layer Unused
    public static Object grh() {
        return null;
    }

    //MakeFont
    //Java Layer Unused
    public static int mf(int fontId, int style, String text) {
        return 0;
    }

    //Controller Exit
    public static void exit(boolean forced) {
        System.out.println("exit " + forced);
    }

    //GetTexture
    //Java Layer Unused
    public static byte[] gt(String key) {
        return new byte[0];
    }

    //Disconnect
    //Disconnect dll with loader after finished loading
    public static void dc() {
    }

    /** Signals the external injector after the first Minecraft world is ready. */
    public static native void injectReady();

    //GetClassFields
    //Java Layer Unused
    public static String[] gcf(Class<?> targetClass) {
        if (targetClass == null) {
            return new String[0];
        }
        java.lang.reflect.Field[] fields = targetClass.getDeclaredFields();
        String[] names = new String[fields.length];
        for (int index = 0; index < fields.length; ++index) {
            names[index] = fields[index].getName();
        }
        return names;
    }

    public static int gts() {
        return 1;
    }

    public static boolean isForgeAbsent() {
        return forgeAbsent;
    }

    //Translate
    public static double[] trn(double worldX, double worldY, double worldZ) {
        FloatBuffer modelViewMatrix = BufferUtils.createFloatBuffer(16);
        FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        NativeBridge.readGlFloats(2982, modelViewMatrix);
        NativeBridge.readGlFloats(2983, projectionMatrix);
        NativeBridge.readGlIntegers(2978, viewport);
        FloatBuffer screenPosition = BufferUtils.createFloatBuffer(3);
        GLU.gluProject((float)worldX, (float)worldY, (float)worldZ,
                modelViewMatrix, projectionMatrix, viewport, screenPosition);
        return new double[]{screenPosition.get(0), screenPosition.get(1), screenPosition.get(2)};
    }

    public static boolean gtcf(Object target, int index, int flags) {
        return false;
    }

    //GetKeyName
    public static native String gkn(long keyCode);

    //MessageBox
    public static void mb(int messageCode) {
        //can't understand why manthe would print error code instead of any meaningful text
    }

    //GetKeyState
    public static native short gks(int keyCode);

    //RenderState
    public static void rs(int phase, double width, double height) {
        GL11.glClear((int)256);
        GL11.glMatrixMode((int)5889);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, width, height, 0.0, 1000.0, 3000.0);
        GL11.glMatrixMode((int)5888);
        GL11.glLoadIdentity();
        GL11.glTranslatef((float)0.0f, (float)0.0f, (float)-2000.0f);
        if (phase > 0) {
            if (alphaTestWasEnabled) {
                GL11.glEnable((int)3008);
            }
        } else {
            alphaTestWasEnabled = GL11.glIsEnabled((int)3008);
            if (alphaTestWasEnabled) {
                GL11.glDisable((int)3008);
            }
        }
    }

    //GetClass
    public static Class<?> gc(String internalName) {
        try {
            return loadRuntimeClass(internalName.replace("/", "."));
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    //GetClassObjects
    //Java Layer Unused
    //since I hate jvmti, so I'm not going to implement this
    public static Object[] gco(Class<?> targetClass) {
        return new Object[0];
    }

    //GetClassBytes
    public static native byte[] gcb(Class<?> targetClass);

    public static native void trs(int state);

    //CopyString
    //Java Layer Unused
    public static String cs(int stringId) {
        return "";
    }

    public static native byte[] gfb(String name);

    public static void scm(String sourceName, String mappedName) {
    }

    //GetClassJava
    public static Class<?> gcj(String descriptor) {
        try {
            return descriptor.startsWith("[")
                    ? Class.forName(descriptor.substring(2, descriptor.length() - 1).replace("/", "."))
                    : Class.forName(Type.getType(descriptor.substring(1, descriptor.length() - 1)).getClassName());
        }
        catch (Exception exception) {
            return null;
        }
    }

    //GetProfile
    public static String gp(String key) {
        if ("all".equals(key)) {
            return Base64Util.encodeUtf8Base64(DEFAULT_CONFIG_JSON);
        }
        return "";
    }

    public static void test() {
    }

    public static String getBootstrapStage() {
        return bootstrapStage;
    }

    private static void enterBootstrapStage(String stage) {
        bootstrapStage = stage;
        try {
            NativeBridge.sce("BOOT " + stage);
        }
        catch (Throwable ignored) {
            System.err.println("[Vape421] BOOT " + stage);
        }
    }

    //GetStringHightV2
    //Java Layer Unused
    public static double gshv2(int fontId, String text) {
        return 0.0;
    }

    //GetClassSignature
    public static String gcs(Class<?> targetClass) {
        if (targetClass == null) {
            return "";
        }
        return Type.getDescriptor(targetClass);
    }

    //MapVirtualKey
    public static native int mvk(int virtualKey, int scanCode);

    //GetStringHeight
    //Java Layer Unused
    public static double gsh(int fontId, String text) {
        return 0.0;
    }

    public static void start() throws Throwable {
        NativeBridge.enterBootstrapStage("DETECT_RUNTIME");
        forgeAbsent = !isAnyClassPresent(
                "net.minecraftforge.versions.forge.ForgeVersion",
                "net.minecraftforge.common.ForgeVersion",
                "net.minecraftforge.fml.loading.FMLLoader");
        Vape vape = new Vape();
        NativeBridge.enterBootstrapStage("LOAD_MAPPINGS");
        NativeBridge.invokeVoidInitOrThrow(vape, "loadMappings");
        NativeBridge.enterBootstrapStage("INIT_ACCOUNT_OFFLINE");
        NativeBridge.sce("LOAD initAccountInfo");
        if (!vape.initAccountInfo()) {
            NativeBridge.sce("WARN initAccountInfo; continuing without account information");
        } else {
            NativeBridge.sce("OK initAccountInfo");
        }
        NativeBridge.enterBootstrapStage("INIT_MANAGERS");
        NativeBridge.invokeVoidInitOrThrow(vape, "initializeManagers");
        NativeBridge.enterBootstrapStage("JAVA_READY_WAITING_FOR_WORLD");
    }

    private static void invokeVoidInitOrThrow(Vape vape, String name) throws Throwable {
        for (Method method : Vape.class.getDeclaredMethods()) {
            if (!method.getName().equals(name) || !method.getReturnType().equals(Void.TYPE)) continue;
            NativeBridge.sce("LOAD " + name);
            method.setAccessible(true);
            try {
                method.invoke(vape, new Object[0]);
                NativeBridge.sce("OK " + name);
            }
            catch (java.lang.reflect.InvocationTargetException wrapper) {
                Throwable cause = wrapper.getCause() != null ? wrapper.getCause() : wrapper;
                NativeBridge.logThrowable(name, cause);
                throw cause;
            }
            catch (Throwable other) {
                NativeBridge.logThrowable(name, other);
                throw other;
            }
            return;
        }
        NativeBridge.sce("MISSING void " + name + "()");
        throw new NoSuchMethodException("Missing Vape initialization method: " + name);
    }

    private static void logThrowable(String context, Throwable error) {
        int depth = 0;
        for (Throwable current = error; current != null && depth < 8; current = current.getCause(), ++depth) {
            NativeBridge.sce("EXC " + context + " -> " + current.getClass().getName() + ": " + current.getMessage());
            StackTraceElement[] frames = current.getStackTrace();
            for (int frameIndex = 0; frameIndex < frames.length && frameIndex < 12; ++frameIndex) {
                NativeBridge.sce("    at " + frames[frameIndex].toString());
            }
        }
    }

    //GetStringWidthV2
    //Java Layer Unused
    public static double gswv2(int fontId, String text) {
        return 0.0;
    }

    //DrawString
    //Java Layer Unused
    public static int ds(int fontId, String text, double x, double y, int color) {
        return 0;
    }

    //GetStringWidth
    //Java Layer Unused
    public static double gsw(int fontId, String text) {
        return 0.0;
    }

    //SetUsername
    //Not available under current recovery project
    public static void su(String username) {
    }

    //ClipboardCopy
    public static native void cpy(String text);

    public static long smpm(boolean pressed, long windowHandle, int button,
                            long cursorPosition, long extraInfo) {
        return 0L;
    }

    //Reload
    //Java Layer Unused
    public static void rl() {
    }

    //GetClassMethods
    //Java Layer Unused
    public static String[] gcm(Class<?> targetClass) {
        if (targetClass == null) {
            return new String[0];
        }
        Method[] methods = targetClass.getDeclaredMethods();
        String[] names = new String[methods.length];
        for (int index = 0; index < methods.length; ++index) {
            names[index] = methods[index].getName();
        }
        return names;
    }

    //GetKey
    //Java Layer Unused
    public static int gk() {
        return 0;
    }

    //SendMouseDown
    public static native void smd(int mode, int value);

    public static void rsc() {
    }

    //UpdateDiscord
    public static void updc(String serverDescription, String clientDescription) {
    }

    public static void fs() {
    }

    //DrawStringV2
    //Java Layer Unused
    public static native int dsv2(int fontId, String text, double x, double y,
                                  int color, float scale);

    //GetMinorVersion
    public static int gmv() {
        Throwable lastFailure = null;

        // Forge 1.21.x moved ForgeVersion from net.minecraftforge.common to
        // net.minecraftforge.versions.forge and exposes the version through
        // the public getVersion() method.
        try {
            Object forgeVersion = invokeStaticMethod(
                    "net.minecraftforge.versions.forge.ForgeVersion", "getVersion");
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException(
                    "ForgeVersion.getVersion() is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        try {
            Object minorVersion = readStaticField(
                    "net.minecraftforge.common.ForgeVersion", "minorVersion");
            if (minorVersion instanceof Number) {
                return ((Number)minorVersion).intValue();
            }
            lastFailure = new IllegalStateException("ForgeVersion.minorVersion is not numeric");
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        try {
            Object forgeVersion = readStaticField(
                    "net.minecraftforge.fml.loading.FMLLoader", "forgeVersion");
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException("FMLLoader.forgeVersion is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        // Newer Forge versions expose a version string instead of minorVersion.
        try {
            Object forgeVersion = readStaticField(
                    "net.minecraftforge.common.ForgeVersion", "forgeVersion");
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException("ForgeVersion.forgeVersion is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        try {
            Object forgeVersion = readStaticField(
                    "net.minecraftforge.versions.forge.ForgeVersion", "forgeVersion");
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException(
                    "ForgeVersion.forgeVersion is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        int standaloneVersion = NativeBridge.readStandaloneMinecraftVersion();
        if (standaloneVersion >= 0) {
            NativeBridge.sce("OK standalone Minecraft version: " + standaloneVersion);
            return standaloneVersion;
        }

        IllegalStateException failure = new IllegalStateException(
                "Unable to determine Minecraft version without Forge");
        if (lastFailure != null) {
            failure.initCause(lastFailure);
        }
        throw failure;
    }

    private static int parseMinecraftVersion(String text) {
        if (text == null) {
            return -1;
        }
        StringBuilder numeric = new StringBuilder();
        boolean started = false;
        for (int index = 0; index < text.length(); ++index) {
            char character = text.charAt(index);
            if (Character.isDigit(character) || character == '.') {
                numeric.append(character);
                started = true;
            } else if (started) {
                break;
            }
        }
        String[] parts = numeric.toString().split("\\.");
        if (parts.length < 2) {
            return -1;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            if (major == 1 && minor == 21) {
                if (patch >= 11) return 61;
                if (patch >= 10) return 60;
                if (patch >= 6) return 56;
                if (patch >= 5) return 55;
                if (patch >= 4) return 54;
                return 51;
            }
            if (major == 1 && minor == 20 && patch >= 6) return 50;
            if (major == 1 && minor == 17) return 37;
            if (major == 1 && minor == 16) return patch == 5 ? 36 : 35;
            if (major == 1 && minor == 12) return 23;
            if (major == 1 && minor == 8) return 15;
            if (major == 1 && minor == 7) return 13;
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
        return -1;
    }

    private static int readStandaloneMinecraftVersion() {
        String[] propertyNames = new String[]{
                "vape.minecraft.version",
                "minecraft.version",
                "sun.java.command"
        };
        String[] versionFields = new String[]{
                "net.minecraft.realms.RealmsSharedConstants",
                "net.minecraft.util.SharedConstants",
                "net.minecraft.SharedConstants"
        };

        for (String propertyName : propertyNames) {
            try {
                int parsed = NativeBridge.parseMinecraftVersion(
                        System.getProperty(propertyName));
                if (parsed >= 0) {
                    return parsed;
                }
            }
            catch (SecurityException ignored) {
                // A restricted launcher may deny access to system properties.
            }
        }

        for (String className : versionFields) {
            try {
                Object version = NativeBridge.readStaticField(className, "VERSION_STRING");
                int parsed = NativeBridge.parseMinecraftVersion(String.valueOf(version));
                if (parsed >= 0) {
                    return parsed;
                }
            }
            catch (Throwable ignored) {
                // The constant is version-dependent and may not exist.
            }
        }
        return -1;
    }

    private static boolean isClassPresent(String className) {
        try {
            loadRuntimeClass(className);
            return true;
        }
        catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isAnyClassPresent(String... classNames) {
        for (String className : classNames) {
            if (isClassPresent(className)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> loadRuntimeClass(String className) throws ClassNotFoundException {
        ClassNotFoundException lastFailure = null;
        ClassLoader[] candidateLoaders = new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                NativeBridge.class.getClassLoader(),
                Vape.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : candidateLoaders) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(className, false, loader);
            }
            catch (ClassNotFoundException failure) {
                lastFailure = failure;
            }
        }

        // Forge 1.21.x may expose its automatic modules through a module layer
        // that is not the same loader as Minecraft's game loader. Use reflection
        // here so the payload remains Java 8 bytecode compatible.
        String[] moduleNames = new String[]{
                "net.minecraftforge.forge",
                "net.minecraftforge.fmlloader",
                "net.minecraftforge.modlauncher"
        };
        try {
            Class<?> moduleLayerClass = Class.forName(
                    "java.lang.ModuleLayer", false, ClassLoader.getSystemClassLoader());
            Method bootMethod = moduleLayerClass.getMethod("boot");
            Method findLoaderMethod = moduleLayerClass.getMethod("findLoader", String.class);
            Object bootLayer = bootMethod.invoke(null);
            for (String moduleName : moduleNames) {
                try {
                    ClassLoader loader = (ClassLoader)findLoaderMethod.invoke(bootLayer, moduleName);
                    if (loader == null) {
                        continue;
                    }
                    return Class.forName(className, false, loader);
                }
                catch (Throwable ignored) {
                    // The module may live in a custom Forge layer instead.
                }
            }
        }
        catch (Throwable ignored) {
            // Java 8 has no ModuleLayer; the normal loaders above are enough.
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ClassNotFoundException(className);
    }

    private static Object readStaticField(String className, String fieldName) throws Exception {
        Class<?> owner = loadRuntimeClass(className);
        Field field;
        try {
            field = owner.getField(fieldName);
        }
        catch (NoSuchFieldException ignored) {
            field = owner.getDeclaredField(fieldName);
        }
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object invokeStaticMethod(String className, String methodName) throws Exception {
        Class<?> owner = loadRuntimeClass(className);
        Method method = owner.getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static int parseForgeVersion(Object value) {
        if (value == null) {
            return -1;
        }
        String text = String.valueOf(value).trim();
        int start = 0;
        while (start < text.length() && !Character.isDigit(text.charAt(start))) {
            ++start;
        }
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            ++end;
        }
        if (start == end) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(text.substring(start, end));
            return isKnownVersionId(parsed) ? parsed : -1;
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isKnownVersionId(int version) {
        switch (version) {
            case 13: case 15: case 23: case 28: case 35: case 36: case 37:
            case 50: case 51: case 54: case 55: case 56: case 60: case 61:
            case 100: case 110:
                return true;
            default:
                return false;
        }
    }

    public static native int ss_2(String value);

    public static String sp(String key, String value) {
        return null;
    }

    public static void reload() {
    }

    public static void printLog(String message) {
        System.out.println(message);
    }

    //SetClassBytes
    public static native int scb(Class<?> targetClass, byte[] bytecode);

    //MakeFontV2
    //Java Layer Unused
    public static native int mfv2(int fontId, int style, String text);

    //SaveSettings
    @Deprecated
    public static native void ss(String value);

    public static boolean[] gls() {
        return new boolean[0];
    }

    //GetVanillaClas
    public static Class<?> gvc(String internalName) {
        try {
            return loadRuntimeClass(internalName.replace("/", "."));
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Resolves a Minecraft/Forge class through the loader selected by the
     * native bootstrap. MappedClasses cannot use its own payload classloader
     * for transformed game classes on Forge 1.21.x.
     */
    public static Class<?> loadRuntimeClassForMappings(String className) {
        try {
            return loadRuntimeClass(className);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    //SendClientError
    public static native void sce(String message);

    public static native Object inv(Method method, Object target, Object ... arguments);

    public static boolean om(int eventId, long firstArgument, long secondArgument) {
        return GuiScreenNativeCallbackBridge.onNotification(eventId, firstArgument, secondArgument);
    }

    public static void wh(long windowHandle) {
        GuiScreenNativeCallbackBridge.h(windowHandle);
    }
}
