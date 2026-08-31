package gg.vape.account.alt;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.frame.impl.alt.AltManagerFrame;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.GuiScreen;
import java.awt.Color;

/**
 * Draws "Accounts" button overlay when in server selection or world selection.
 * Handles click to open AltManagerFrame.
 * Works across versions by checking class name strings, not only MappedClasses.
 */
public class AltMenuIntegration implements EventListener {
    public static final AltMenuIntegration INSTANCE = new AltMenuIntegration();

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_X_OFFSET = 106; // width - 106
    private static final int BUTTON_Y = 6;

    private boolean isServerSelectionScreen(GuiScreen screen) {
        if (screen == null || screen.getObject() == null) return false;
        // fast path via mapped classes
        try {
            if (screen.isInstance(MappedClasses.u5)) return true; // GuiMultiplayer
        } catch (Throwable ignored) {}
        // fallback string checks for multi-version support
        String className = screen.getObject().getClass().getName().toLowerCase();
        // Covers: GuiMultiplayer, JoinMultiplayerScreen, MultiplayerScreen, GuiSelectWorld, SelectWorldScreen, WorldSelectionScreen
        return className.contains("multiplayer") || className.contains("selectworld") || className.contains("worldselection");
    }

    private int getScreenWidth(GuiScreen screen) {
        try {
            return screen.g(); // width
        } catch (Throwable t) {
            // fallback via reflection
            try {
                Object obj = screen.getObject();
                for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                    if (f.getType() == int.class) {
                        f.setAccessible(true);
                        // Try to find width field by checking GuiScreen's mapping? For now brute force: try field named width
                    }
                }
            } catch (Throwable ignored) {}
            return Minecraft.J(); // fallback display width
        }
    }

    private int getScreenHeight(GuiScreen screen) {
        try { return screen.k(); } catch (Throwable t) { return Minecraft.h(); }
    }

    @EventHandler
    public void onRender(EventRender2D event) {
        try {
            GuiScreen current = Minecraft.currentScreen();
            if (current == null || current.isNull()) return;
            if (!isServerSelectionScreen(current)) return;
            // If AltManagerFrame is visible, don't draw overlay button (it has its own UI)
            AltManagerFrame altFrame = ClientSettings.getFrame(AltManagerFrame.class);
            if (altFrame != null && altFrame.V$src$Z$1xhop3l()) {
                return;
            }
            // Don't draw if Vape GUI is open (inputEnabled false means GUI open and would conflict)
            try {
                gg.vape.module.none.ClientSettings cs = Vape.INSTANCE.getModManager().getMod(gg.vape.module.none.ClientSettings.class);
                if (cs != null && !cs.inputEnabled) return;
            } catch (Throwable ignored) {}

            int screenW = getScreenWidth(current);
            // int screenH = getScreenHeight(current);
            int bx = screenW - BUTTON_X_OFFSET;
            int by = BUTTON_Y;

            // draw button background
            // Use same theme as Vape but ensure visible on vanilla background
            Color bg = new Color(35,35,35,220);
            Color hoverBg = new Color(60,60,60,220);
            // check hover
            int mouseX = gg.vape.utils.render.RenderUtils.h().O;
            int mouseY = gg.vape.utils.render.RenderUtils.h().H;
            // Convert mouse coords: RenderUtils gives scaled coords? For overlay we use scaled via GuiRenderPrimitives? Use same as EventRender2D scaling.
            // EventRender2D uses displayWidth/Height with scaling, but mouse via RenderUtils is already scaled by guiScale
            boolean hovered = mouseX >= bx && mouseX <= bx + BUTTON_WIDTH && mouseY >= by && mouseY <= by + BUTTON_HEIGHT;

            GuiRenderPrimitives.B((double)bx, (double)by, (double)BUTTON_WIDTH, (double)BUTTON_HEIGHT, hovered ? hoverBg : bg, 2.0f);
            GuiRenderPrimitives.P((double)bx, (double)by, (double)BUTTON_WIDTH, (double)BUTTON_HEIGHT, new Color(80,80,80), 2.0f, 0.8f, 1.0f);

            // draw text
            String text = "Accounts";
            // Use FontManager
            try {
                gg.vape.ui.font.SmoothFontRenderer fr = Vape.INSTANCE.getFontManager().Y(0.85);
                double tw = fr.N(text);
                double th = fr.d(text);
                double tx = bx + BUTTON_WIDTH/2.0 - tw/2.0;
                double ty = by + BUTTON_HEIGHT/2.0 - th/2.0;
                fr.d(text, tx, ty, Color.WHITE);
                // draw current username below button or at top-left like original
                String currentUserText = "User: " + AltSessionManager.getUsername();
                gg.vape.ui.font.SmoothFontRenderer small = Vape.INSTANCE.getFontManager().Y(0.65);
                small.d(currentUserText, 3, 3, new Color(200,200,200));
            } catch (Throwable t) {
                // fallback log
            }

        } catch (Throwable t) {
            // silent
        }
    }

    @EventHandler
    public void onMouse(EventMouseButton event) {
        try {
            if (!event.getButtonState()) return; // only on press
            if (event.getButton() != 0) return; // left click only
            GuiScreen current = Minecraft.currentScreen();
            if (current == null || current.isNull()) return;
            if (!isServerSelectionScreen(current)) return;
            try {
                gg.vape.module.none.ClientSettings cs2 = Vape.INSTANCE.getModManager().getMod(gg.vape.module.none.ClientSettings.class);
                if (cs2 != null && !cs2.inputEnabled) return;
            } catch (Throwable ignored) {}
            // check alt frame already visible -> don't intercept
            AltManagerFrame altFrame = ClientSettings.getFrame(AltManagerFrame.class);
            if (altFrame != null && altFrame.V$src$Z$1xhop3l()) return;

            int screenW = getScreenWidth(current);
            int bx = screenW - BUTTON_X_OFFSET;
            int by = BUTTON_Y;

            // mouse pos via RenderUtils (scaled)
            int mouseX = gg.vape.utils.render.RenderUtils.h().O;
            int mouseY = gg.vape.utils.render.RenderUtils.h().H;

            if (mouseX >= bx && mouseX <= bx + BUTTON_WIDTH && mouseY >= by && mouseY <= by + BUTTON_HEIGHT) {
                event.setCancelled(true);
                openAltManager();
            }
        } catch (Throwable t) {
        }
    }

    private void openAltManager() {
        try {
            AltManagerFrame frame = ClientSettings.getFrame(AltManagerFrame.class);
            if (frame == null) {
                frame = new AltManagerFrame();
                // if not registered, try to register via ClientSettings - but we register in initializeFrames, so should exist
                // fallback: just toggle visibility if exists
            }
            frame.show();
            // Ensure its stack is visible via activeStack? AltManagerFrame is in sessionSpoofStack by default, but we placed it in mainStack
            // Bring to front
            ClientSettings.INSTANCE.getActiveStack().v(frame);
            // If currently in mainStack, ensure it's visible; if we use sessionSpoofStack logic, we might need to switch stack
            // For simplicity, ensure we are not in hudEditor etc, and show frame via queue
            ClientSettings.queueFrameOpen(frame);
            Vape.debugLog("[AltMenu] opened AltManager");
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
    }
}
