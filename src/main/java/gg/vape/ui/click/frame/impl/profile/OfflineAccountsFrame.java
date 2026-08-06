package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.account.OfflineAccountManager;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.ColorDividerComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.IconTextActionRowComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.frame.SettingsSubpageFrame;
import java.util.List;

/** Local account management screen for supported Minecraft versions. */
public final class OfflineAccountsFrame extends SettingsSubpageFrame {
    private static final double FRAME_WIDTH = 110.0;
    private static final double CONTENT_WIDTH = 100.0;
    private final PanelComponent accountList;
    private final TextInputComponentBase accountInput;

    public OfflineAccountsFrame() {
        super("newprofiles", "Accounts");
        this.setVisible(false);
        this.o(FRAME_WIDTH);
        this.setUseExplicitWidth(true);
        this.N(false);
        this.D(true);
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setToolbarWidth(FRAME_WIDTH);
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setDefaultIconScale(0.5f);

        this.accountInput = new TextInputComponentBase("") {
            @Override
            public void submit() {
                OfflineAccountsFrame.this.addAccount(this.getText());
            }
        };
        this.accountInput.setMaxLength(16);
        this.accountInput.setActionButtonVisible(true);
        this.accountInput.o(CONTENT_WIDTH);
        this.accountInput.Y(20.0);

        this.accountList = new PanelComponent(CONTENT_WIDTH, 0.0);
        this.accountList.o(CONTENT_WIDTH);
        this.accountList.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");

        this.addChildren(
                this.accountInput,
                new SpacerComponent(1.0, 3.0),
                new ColorDividerComponent(OfflineAccountsFrame.J.m),
                this.accountList);
        this.refreshAccountList();
    }

    private void addAccount(String name) {
        OfflineAccountManager manager = Vape.INSTANCE.getOfflineAccountManager();
        if (manager == null) return;
        if (manager.addAndApply(name == null ? "" : name.trim())) {
            this.accountInput.clearText();
            ClientSettings.activeComponent = null;
            this.refreshAccountList();
        }
    }

    private void selectAccount(String name) {
        OfflineAccountManager manager = Vape.INSTANCE.getOfflineAccountManager();
        if (manager != null && manager.selectAndApply(name)) {
            this.refreshAccountList();
        }
    }

    private void refreshAccountList() {
        this.accountList.removeMarkedChildren();
        OfflineAccountManager manager = Vape.INSTANCE.getOfflineAccountManager();
        if (manager == null) return;
        List<String> names = manager.getAccountNames();
        for (String name : names) {
            String label = name.equalsIgnoreCase(manager.getActiveName()) ? name + "  (active)" : name;
            IconTextActionRowComponent row = new IconTextActionRowComponent(label).setIconVisible(false);
            row.o(CONTENT_WIDTH);
            row.setUseExplicitWidth(true);
            row.addClickListener(() -> this.selectAccount(name));
            this.accountList.addChildren(row);
        }
        this.accountList.Y(names.size() * 18.0);
        this.accountList.l$src$V$1mibm4x();
    }

    @Override
    public void u() {
        super.u();
        this.accountList.l$src$V$1mibm4x();
    }

    @Override
    public String getName() {
        return "Accounts";
    }

    public static boolean matchesSavedTitle(String title) {
        return "Accounts".equalsIgnoreCase(title) || "Acoounts".equalsIgnoreCase(title);
    }
}
