package gg.vape.ui.click.frame.impl.hud;

import gg.vape.Vape;
import gg.vape.module.render.hud.PingDisplayHudModule;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import java.awt.Color;
import java.util.Collection;
import java.util.UUID;

public class PingDisplayHudFrame extends HudModuleConfigFrameBase {
    private static final int SHADOW_COLOR_ARGB = 0x80000000;
    private static final long PING_CACHE_WINDOW_MS = 250L;
    private static volatile int cachedPing = -1;
    private static volatile long lastPingReadAt;

    public PingDisplayHudFrame() {
        super(PingDisplayHudModule.class);
    }

    public static int getCurrentPing() {
        long now = System.currentTimeMillis();
        if (now - lastPingReadAt < PING_CACHE_WINDOW_MS) {
            return cachedPing;
        }

        int ping = readCurrentPing();
        cachedPing = ping;
        lastPingReadAt = now;
        return ping;
    }

    private static int readCurrentPing() {
        try {
            EntityPlayerSP localPlayer = Minecraft.thePlayer();
            if (localPlayer == null || localPlayer.isNull()) {
                return -1;
            }

            UUID localPlayerId = localPlayer.X$src$Ljava_util_UUID_$1o5dyg6();
            String localPlayerName = null;
            GameProfile localProfile = localPlayer.c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
            if (localProfile != null && localProfile.isNotNull()) {
                localPlayerName = localProfile.getName();
                if (localPlayerId == null) {
                    localPlayerId = localProfile.getUUID();
                }
            }

            if (Minecraft.N() == null || Minecraft.N().isNull()) {
                return -1;
            }
            Collection playerInfoMap = Minecraft.N().getPlayerInfoMap();
            if (playerInfoMap == null) {
                return -1;
            }

            for (Object object : playerInfoMap) {
                try {
                    PlayerInfo playerInfo = new PlayerInfo(object);
                    if (playerInfo.isNull()) {
                        continue;
                    }

                    GameProfile profile = playerInfo.v();
                    if (profile == null || profile.isNull()) {
                        continue;
                    }

                    UUID playerId = profile.getUUID();
                    String playerName = profile.getName();
                    boolean samePlayer = localPlayerId != null && localPlayerId.equals(playerId);
                    if (!samePlayer && localPlayerName != null && playerName != null) {
                        samePlayer = localPlayerName.equalsIgnoreCase(playerName);
                    }
                    if (samePlayer) {
                        return Math.max(0, playerInfo.z());
                    }
                }
                catch (Throwable ignoredEntry) {
                    // One malformed/unsupported entry must not hide the local player's ping.
                }
            }
        }
        catch (Throwable ignored) {
            // No world/connection or an unsupported mapping: show the offline marker.
        }
        return -1;
    }

    @Override
    public void renderHudContent() {
        SmoothFontRenderer renderer = Vape.INSTANCE.getFontManager().W(1.2, false);
        int ping = getCurrentPing();
        String pingText = ping < 0 ? "Ping: --" : "Ping: " + ping + " ms";
        float textX = (int)(this.G$src$D$1b2f02a() + this.A() / 2.0
                - renderer.N(pingText) / 2.0);
        float textY = (int)(this.n() + this.L() / 2.0
                - renderer.d(pingText) / 2.0);
        if (this.shouldRenderHudBackground()) {
            renderer.d(pingText, textX, textY, this.getEditorForegroundColor());
        } else {
            renderer.T(pingText, textX, textY, this.getEditorForegroundColor(),
                    this.applyDefaultEditorAlpha(new Color(SHADOW_COLOR_ARGB, true)));
        }
    }

    @Override
    public String getName() {
        return "PingFrame";
    }

    @Override
    public double A() {
        return 65.0;
    }

    @Override
    public double L() {
        return 20.0;
    }
}
