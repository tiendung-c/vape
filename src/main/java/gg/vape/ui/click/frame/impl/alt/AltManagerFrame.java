package gg.vape.ui.click.frame.impl.alt;

import gg.vape.Vape;
import gg.vape.account.alt.AltAccount;
import gg.vape.account.alt.AltManager;
import gg.vape.account.alt.AltSessionManager;
import gg.vape.account.alt.MicrosoftAuthService;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.utils.render.GuiRenderPrimitives;
import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Account Manager UI integrated into Vape ClickGUI.
 * Shows list of alts, login/add/delete. Displayed via SessionSpoofStack or standalone.
 */
public class AltManagerFrame extends Frame {
    private static final double FRAME_WIDTH = 240;
    private static final double FRAME_HEIGHT = 260;

    private final FlowLayoutComponent root = new FlowLayoutComponent(FRAME_WIDTH - 10);
    private final FlowLayoutComponent listContainer = new FlowLayoutComponent(FRAME_WIDTH - 10);
    private SimpleTextLabelComponent titleLabel;
    private SimpleTextLabelComponent statusLabel;
    private SimpleTextLabelComponent currentUserLabel;
    private TextButton loginButton;
    private TextButton addMsButton;
    private TextButton addCrackedButton;
    private TextButton deleteButton;
    private TextButton closeButton;
    private TextInputComponentBase offlineInput;

    private int selectedIndex = -1;
    private ExecutorService executor;
    private CompletableFuture<Void> task;
    private String statusText = "Ready";

    public AltManagerFrame() {
        this.o(FRAME_WIDTH);
        this.Y(FRAME_HEIGHT);
        this.setVisible(false);
        this.L(false, true);
        this.g(true);
        this.Y(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().t(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().I(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().U(false);

        this.titleLabel = new SimpleTextLabelComponent("Account Manager (0)");
        this.currentUserLabel = new SimpleTextLabelComponent("Current: " + AltSessionManager.getUsername());
        this.statusLabel = new SimpleTextLabelComponent(statusText);
        this.statusLabel.setFontScale(0.75f);

        // buttons row
        Color btnBg = new Color(35,35,35);
        Color btnHover = new Color(60,60,60);
        this.loginButton = new TextButton("Login", 0.8, btnBg, btnHover);
        this.loginButton.addClickListener(this::onLogin);
        this.addMsButton = new TextButton("Add Microsoft", 0.8, btnBg, btnHover);
        this.addMsButton.addClickListener(this::onAddMicrosoft);
        this.addCrackedButton = new TextButton("Add Offline", 0.8, btnBg, btnHover);
        this.addCrackedButton.addClickListener(this::onAddOffline);
        this.deleteButton = new TextButton("Delete", 0.8, new Color(120,40,40), new Color(180,60,60));
        this.deleteButton.addClickListener(this::onDelete);
        this.closeButton = new TextButton("Close", 0.8, btnBg, btnHover);
        this.closeButton.addClickListener(() -> this.setVisible(false));

        this.offlineInput = new TextInputComponentBase("offline name") {
            @Override public void submit() { onAddOffline(); }
        };
        this.offlineInput.o(FRAME_WIDTH - 14);
        this.offlineInput.Y(18);

        // layout
        this.root.addChildren(
                titleLabel,
                currentUserLabel,
                new SpacerComponent(5, 4),
                listContainer,
                new SpacerComponent(5, 6),
                buildButtonRow(loginButton, deleteButton),
                new SpacerComponent(5, 2),
                buildButtonRow(addMsButton, addCrackedButton),
                offlineInput,
                new SpacerComponent(5, 2),
                statusLabel,
                closeButton
        );

        this.h(this.root, new Object[0]);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AltManager");
            t.setDaemon(true);
            return t;
        });

        AltManager.getInstance().load();
        refreshList();
    }

    private GuiComponent buildButtonRow(GuiComponent a, GuiComponent b) {
        FlowLayoutComponent row = new FlowLayoutComponent(FRAME_WIDTH - 10);
        a.o((FRAME_WIDTH - 18) / 2);
        a.Y(16);
        b.o((FRAME_WIDTH - 18) / 2);
        b.Y(16);
        row.addChildren(a, b);
        return row;
    }

    private void refreshList() {
        listContainer.f().clear();
        List<AltAccount> accounts = AltManager.getInstance().getAccountsUnsafe();
        // we need to access underlying list via synchronized copy; we use unsafe for display but ensure sync
        synchronized (accounts) {
            titleLabel.setText("Account Manager (" + accounts.size() + ")");
            currentUserLabel.setText("Current: " + AltSessionManager.getUsername());
            if (accounts.isEmpty()) {
                listContainer.addChildren(new SimpleTextLabelComponent("No accounts - Add one"));
            } else {
                for (int i = 0; i < accounts.size(); i++) {
                    AltAccount acc = accounts.get(i);
                    final int idx = i;
                    String rawDisplay = acc.getUsername();
                    if (rawDisplay == null || rawDisplay.isEmpty()) rawDisplay = "Unknown";
                    final String display = rawDisplay;
                    // mark selected and current
                    boolean isSelected = idx == selectedIndex;
                    boolean isCurrent = display.equalsIgnoreCase(AltSessionManager.getUsername());
                    String label = (isSelected ? "> " : "  ") + display + (isCurrent ? " [active]" : "");
                    Color bg = isSelected ? new Color(60,80,120) : new Color(35,35,35);
                    Color hover = isSelected ? new Color(80,100,150) : new Color(60,60,60);
                    TextButton entry = new TextButton(label, 0.75, bg, hover);
                    entry.o(FRAME_WIDTH - 14);
                    entry.Y(14);
                    entry.setCornerRadius(1.5f);
                    entry.addClickListener(() -> {
                        selectedIndex = idx;
                        statusText = "Selected: " + display;
                        statusLabel.setText(statusText);
                        refreshList();
                    });
                    // double click to login: we handle via loginButton; could also listen for double click but TextButton single
                    listContainer.addChildren(entry);
                }
            }
        }
        // update button states
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < AltManager.getInstance().size();
        boolean busy = task != null && !task.isDone();
        loginButton.setInteractionDisabled(!hasSelection || busy);
        deleteButton.setInteractionDisabled(!hasSelection);
        addMsButton.setInteractionDisabled(busy);
        statusLabel.setText(statusText);
        this.H(true);
        this.u();
    }

    private void setStatus(String msg) {
        statusText = msg;
        statusLabel.setText(msg);
        Vape.debugLog("[AltManager] " + msg);
    }

    private void onLogin() {
        if (selectedIndex < 0) return;
        List<AltAccount> list = AltManager.getInstance().getAccountsUnsafe();
        AltAccount acc;
        synchronized (list) {
            if (selectedIndex >= list.size()) return;
            acc = list.get(selectedIndex);
        }
        String username = acc.getUsername();
        if (username == null || username.isEmpty()) username = "???";
        setStatus("Logging in " + username + "...");
        final String displayName = username;
        AtomicReference<String> newRefresh = new AtomicReference<>(""); 
        AtomicReference<String> newAccess = new AtomicReference<>("");
        AtomicReference<MicrosoftAuthService.AltLoginResult> profileRef = new AtomicReference<>();

        // First try direct token (if valid), else refresh flow
        task = MicrosoftAuthService.fetchProfile(acc.getAccessToken(), executor)
                .handle((res, ex) -> {
                    if (res != null) {
                        profileRef.set(res);
                        return true;
                    }
                    return false;
                })
                .thenComposeAsync(completed -> {
                    if (completed) {
                        // direct token worked
                        throw new java.util.NoSuchElementException("direct_success"); // use exception to skip refresh chain
                    }
                    setStatus("Refreshing " + displayName + "...");
                    return MicrosoftAuthService.refreshMSAccessTokens(acc.getRefreshToken(), executor);
                })
                .thenComposeAsync(msTokens -> {
                    setStatus("Xbox token " + displayName + "...");
                    newRefresh.set(msTokens.get("refresh_token"));
                    return MicrosoftAuthService.acquireXboxAccessToken(msTokens.get("access_token"), executor);
                })
                .thenComposeAsync(xboxToken -> {
                    setStatus("XSTS " + displayName + "...");
                    return MicrosoftAuthService.acquireXboxXstsToken(xboxToken, executor);
                })
                .thenComposeAsync(xsts -> {
                    setStatus("MC token " + displayName + "...");
                    return MicrosoftAuthService.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), executor);
                })
                .thenComposeAsync(mcToken -> {
                    setStatus("Profile " + displayName + "...");
                    newAccess.set(mcToken);
                    return MicrosoftAuthService.fetchProfile(mcToken, executor);
                })
                .thenAccept(result -> {
                    if (result == null) return;
                    profileRef.set(result);
                    acc.setRefreshToken(newRefresh.get().isEmpty() ? acc.getRefreshToken() : newRefresh.get());
                    acc.setAccessToken(newAccess.get().isEmpty() ? acc.getAccessToken() : newAccess.get());
                    acc.setUsername(result.username);
                    acc.setUuid(result.uuid);
                    AltManager.getInstance().save();
                    AltSessionManager.setSession(acc, result.username, result.uuid, result.mcToken);
                    setStatus("Logged in as " + result.username);
                    refreshList();
                })
                .exceptionally(err -> {
                    Throwable cause = err.getCause();
                    if (cause instanceof java.util.NoSuchElementException && "direct_success".equals(cause.getMessage())) {
                        // direct token success path
                        MicrosoftAuthService.AltLoginResult res = profileRef.get();
                        if (res != null) {
                            acc.setUsername(res.username);
                            acc.setUuid(res.uuid);
                            AltManager.getInstance().save();
                            AltSessionManager.setSession(acc, res.username, res.uuid, acc.getAccessToken());
                            setStatus("Logged in as " + res.username + " (cached)");
                            refreshList();
                        }
                        return null;
                    }
                    String msg = err.getMessage();
                    if (cause != null && cause.getMessage() != null) msg = cause.getMessage();
                    if (msg == null) msg = "Login failed";
                    // unwrap
                    Throwable root = err;
                    while (root.getCause() != null) root = root.getCause();
                    if (root.getMessage() != null) msg = root.getMessage();
                    setStatus("Failed: " + msg);
                    refreshList();
                    return null;
                });
    }

    private void onAddMicrosoft() {
        if (task != null && !task.isDone()) {
            setStatus("Busy, wait...");
            return;
        }
        String state = UUID.randomUUID().toString().substring(0,8);
        java.net.URI uri = MicrosoftAuthService.getMSAuthLink(state);
        if (uri != null) {
            MicrosoftAuthService.openWebLink(uri);
            try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(uri.toString()), null); } catch (Exception ignored) {}
        }
        setStatus("Browser opened, waiting...");
        AtomicReference<String> refreshRef = new AtomicReference<>("");
        AtomicReference<String> accessRef = new AtomicReference<>("");
        task = MicrosoftAuthService.acquireMSAuthCode(state, executor)
                .thenComposeAsync(code -> {
                    setStatus("MS tokens...");
                    return MicrosoftAuthService.acquireMSAccessTokens(code, executor);
                })
                .thenComposeAsync(msTokens -> {
                    setStatus("Xbox...");
                    refreshRef.set(msTokens.get("refresh_token"));
                    return MicrosoftAuthService.acquireXboxAccessToken(msTokens.get("access_token"), executor);
                })
                .thenComposeAsync(xbox -> {
                    setStatus("XSTS...");
                    return MicrosoftAuthService.acquireXboxXstsToken(xbox, executor);
                })
                .thenComposeAsync(xsts -> {
                    setStatus("MC token...");
                    return MicrosoftAuthService.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), executor);
                })
                .thenComposeAsync(mcToken -> {
                    setStatus("Profile...");
                    accessRef.set(mcToken);
                    return MicrosoftAuthService.fetchProfile(mcToken, executor);
                })
                .thenAccept(res -> {
                    AltAccount acc = new AltAccount(refreshRef.get(), accessRef.get(), res.username, res.uuid, 0L);
                    AltManager.getInstance().addAccount(acc);
                    AltSessionManager.setSession(acc, res.username, res.uuid, res.mcToken);
                    setStatus("Added " + res.username);
                    selectedIndex = AltManager.getInstance().size() - 1;
                    refreshList();
                })
                .exceptionally(err -> {
                    Throwable root = err;
                    while (root.getCause() != null) root = root.getCause();
                    String msg = root.getMessage();
                    if (msg == null) msg = err.getMessage();
                    setStatus("Add failed: " + msg);
                    refreshList();
                    return null;
                });
    }

    private void onAddOffline() {
        String name = offlineInput.getText();
        if (name == null || name.trim().isEmpty()) {
            setStatus("Enter offline name");
            return;
        }
        name = name.trim();
        if (!name.matches("^\\w{3,16}$")) {
            setStatus("Invalid name 3-16 alphanum");
            return;
        }
        AltAccount acc = new AltAccount("", "0", name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).toString(), 0L);
        AltManager.getInstance().addAccount(acc);
        AltSessionManager.setOffline(name);
        setStatus("Offline added " + name);
        offlineInput.setText("");
        selectedIndex = AltManager.getInstance().size() - 1;
        refreshList();
    }

    private void onDelete() {
        if (selectedIndex < 0) return;
        AltManager.getInstance().removeAccount(selectedIndex);
        selectedIndex = -1;
        setStatus("Deleted");
        refreshList();
    }

    @Override public String getName() { return "alts"; }

    @Override public void v() {}

    @Override public void Y() {
        GuiRenderPrimitives.y(0f,0f, gg.vape.wrapper.impl.Minecraft.J(), gg.vape.wrapper.impl.Minecraft.h(), new Color(0,0,0,180));
    }

    public void show() {
        AltManager.getInstance().load();
        selectedIndex = -1;
        refreshList();
        this.setVisible(true);
        this.H(true);
    }
}
