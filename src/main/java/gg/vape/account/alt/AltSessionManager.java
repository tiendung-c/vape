package gg.vape.account.alt;

import gg.vape.Vape;
import gg.vape.account.MinecraftSessionWrapper;
import gg.vape.mapping.mappings.MSession;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.util.Optional;
import java.util.UUID;

/**
 * Swaps current Minecraft session. Mirrors SessionManager from AccountManager.
 */
public final class AltSessionManager {
    private AltSessionManager() {}

    public static String getUsername() {
        try {
            MinecraftSessionWrapper s = Minecraft.Q$src$Lgg_vape_account_MinecraftSessionWrapper_$1ftnn3u();
            return s != null ? s.getUsername() : "Unknown";
        } catch (Throwable t) {
            return "Unknown";
        }
    }

    public static String getSessionToken() {
        try {
            // no direct token getter in wrapper; we track via AltAccount, fallback to empty
            // Try to reflectively get token if available? For now return ""
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setSession(AltAccount acc, String username, String uuidStr, String accessToken) {
        try {
            UUID uuid = parseUuid(uuidStr);
            String rawUuid = uuid.toString().replace("-", "");
            Object session;
            if (ForgeVersion.MC_1_21_10.d()) {
                session = MSession.modern(Vape.INSTANCE.getMappings().hw, username, uuid, accessToken, Optional.empty(), Optional.empty());
            } else if (ForgeVersion.MC_1_20_6.d()) {
                session = MSession.B(Vape.INSTANCE.getMappings().hw, username, uuid, accessToken, Optional.empty(), Optional.empty(), Vape.INSTANCE.getMappingsMapperCompat().sessionType.getLegacy());
            } else {
                session = MSession.t(Vape.INSTANCE.getMappings().hw, username, rawUuid, accessToken, "mojang");
            }
            Minecraft.w(new MinecraftSessionWrapper(session));
            Vape.debugLog("[AltSession] switched to " + username + " uuid=" + uuidStr);
            if (Vape.INSTANCE.getNotificationManager() != null) {
                Vape.INSTANCE.getNotificationManager().showInfo("Account", "Switched to " + username, 3000L);
            }
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
    }

    public static void setOffline(String username) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String rawUuid = uuid.toString().replace("-", "");
            Object session;
            if (ForgeVersion.MC_1_21_10.d()) {
                session = MSession.modern(Vape.INSTANCE.getMappings().hw, username, uuid, "0", Optional.empty(), Optional.empty());
            } else if (ForgeVersion.MC_1_20_6.d()) {
                session = MSession.B(Vape.INSTANCE.getMappings().hw, username, uuid, "0", Optional.empty(), Optional.empty(), Vape.INSTANCE.getMappingsMapperCompat().sessionType.getLegacy());
            } else {
                session = MSession.t(Vape.INSTANCE.getMappings().hw, username, rawUuid, "0", "legacy");
            }
            Minecraft.w(new MinecraftSessionWrapper(session));
            Vape.debugLog("[AltSession] offline " + username);
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) return UUID.randomUUID();
        try {
            if (s.contains("-")) return UUID.fromString(s);
            // insert dashes
            return UUID.fromString(s.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}
