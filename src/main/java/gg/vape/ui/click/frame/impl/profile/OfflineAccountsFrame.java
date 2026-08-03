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

/** Offline account management screen for Minecraft 1.8.9. */
public final class OfflineAccountsFrame extends SettingsSubpageFrame {
    private final PanelComponent accountList;
    private final TextInputComponentBase accountInput;

    public OfflineAccountsFrame() {
        super("newprofiles", "Acoounts");
        this.setVisible(false);
        this.o(103.0);
        this.N(false);
        this.D(true);

        this.accountInput = new TextInputComponentBase("Tên account offline") {
            @Override
            public void submit() {
                OfflineAccountsFrame.this.addAccount(this.getText());
            }
        };
        this.accountInput.setMaxLength(16);
        this.accountInput.getActionButton().setIconResource("newadd");
        this.accountInput.getActionButton().o(8.0);
        this.accountInput.getActionButton().Y(8.0);
        this.accountInput.getActionButton().setIconWidth(6.0);
        this.accountInput.getActionButton().setIconHeight(6.0);
        this.accountInput.getActionButton().w("Add");

        this.accountList = new PanelComponent(100.0, 20.0);
        this.accountList.o(110.0);
        this.accountList.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");

        this.addChildren(
                new SimpleTextLabelComponent("Thêm hoặc chọn account offline (Minecraft 1.8.9)", 0.72),
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
            String label = name.equalsIgnoreCase(manager.getActiveName()) ? name + "  (đang dùng)" : name;
            IconTextActionRowComponent row = new IconTextActionRowComponent(label);
            row.addClickListener(() -> this.selectAccount(name));
            this.accountList.addChildren(row);
        }
        this.accountList.l$src$V$1mibm4x();
    }

    @Override
    public void u() {
        super.u();
        this.accountList.l$src$V$1mibm4x();
    }

    @Override
    public String getName() {
        return "Acoounts";
    }
}
