package gg.vape.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Detects a legacy launcher that exposes MCP class and member names. */
final class NamedLegacyRuntimeDetector {
    private NamedLegacyRuntimeDetector() {
    }

    static boolean is189Runtime(VanillaSrgMappings mappings,
                                ClassLoader... preferredLoaders) {
        Class<?> minecraftClass = mappings.resolveClass(
                "net/minecraft/client/Minecraft", preferredLoaders);
        if (minecraftClass == null || !matchesMinecraftStructure(minecraftClass)) {
            return false;
        }
        ClassLoader gameLoader = minecraftClass.getClassLoader();
        String[] requiredClasses = new String[]{
                "net/minecraft/client/renderer/WorldRenderer",
                "net/minecraft/client/renderer/vertex/VertexFormat",
                "net/minecraft/util/BlockPos"
        };
        String[] forbiddenClasses = new String[]{
                "net/minecraft/util/ChunkCoordinates",
                "net/minecraft/util/math/BlockPos"
        };
        for (String className : requiredClasses) {
            Class<?> resolved = mappings.resolveClass(className, preferredLoaders);
            if (resolved == null || resolved.getClassLoader() != gameLoader) {
                return false;
            }
        }
        for (String className : forbiddenClasses) {
            if (mappings.resolveClass(className, preferredLoaders) != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesMinecraftStructure(Class<?> minecraftClass) {
        try {
            Method getter = minecraftClass.getDeclaredMethod("getMinecraft");
            Field instance = minecraftClass.getDeclaredField("theMinecraft");
            return Modifier.isStatic(getter.getModifiers())
                    && getter.getReturnType() == minecraftClass
                    && Modifier.isStatic(instance.getModifiers())
                    && instance.getType() == minecraftClass;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (NoSuchFieldException ignored) {
            return false;
        } catch (SecurityException ignored) {
            return false;
        } catch (LinkageError ignored) {
            return false;
        }
    }
}
