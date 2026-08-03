package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.config.VapeStorage;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.frame.CenteredPopupFrame;
import gg.vape.ui.click.frame.impl.profile.ProfileCreateActionButtonComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileCreateDividerComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileCreateNameInputComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileCreateSubmitNameInputComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileModuleSnapshotListComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilesSettingsFrame;
import java.util.UUID;

public class ProfileCreatePanelComponent
extends GuiComponent {
    private final ProfileCreateActionButtonComponent createButton;
    private Profile pendingProfile;
    private final ProfileCreateNameInputComponent nameInput;
    private final ProfileCreateActionButtonComponent folderButton;
    private final ProfilesSettingsFrame settingsFrame;
    private final ProfileCreateDividerComponent divider;

    @Override
    public void I() {
    }

    public ProfileCreateNameInputComponent getNameInput() {
        return this.nameInput;
    }


    @Override
    public void F() {
    }

    public Profile getPendingProfile() {
        return this.pendingProfile;
    }

    @Override
    public void u() {
    }

    private void startProfileCreation() {
        Profile activeProfile = Vape.INSTANCE.getProfilesManager().getActiveProfile();
        activeProfile.captureCurrentState();
        this.pendingProfile = activeProfile;
        Profile draftProfile = new Profile(activeProfile.getName(), "4.21");
        draftProfile.loadJson(activeProfile.toJson(true));
        draftProfile.setLocalId(UUID.randomUUID());
        draftProfile.setPublicProfileFlag(false);
        draftProfile.setDraft(true);
        Vape.INSTANCE.getProfilesManager().switchProfile(draftProfile);
        PanelComponent popupContent = new PanelComponent(this.settingsFrame.A(), this.settingsFrame.getContentLayout().L());
        popupContent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        ProfileCreateSubmitNameInputComponent submitNameInput = new ProfileCreateSubmitNameInputComponent(this, "Type name", draftProfile);
        submitNameInput.o(this.settingsFrame.A() - 2.0);
        submitNameInput.Y(22.5);
        popupContent.h(submitNameInput, new Object[0]);
        popupContent.h(new ProfileModuleSnapshotListComponent(draftProfile, 105.0, 110.0), new Object[0]);
        CenteredPopupFrame popup = ClientSettings.createPopup(this.settingsFrame.getContentLayout(), popupContent, CenteredPopupFrame.class);
        this.settingsFrame.setActivePopup(popup);
        this.settingsFrame.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().showBackNavigation("New Config", false);
    }

    private void openConfigFolder() {
        if (!VapeStorage.openFolder()) {
            Vape.debugLog("Could not open config folder: " + VapeStorage.root());
        }
    }

    public ProfileCreatePanelComponent(ProfilesSettingsFrame profilesSettingsFrame) {
        this.folderButton = new ProfileCreateActionButtonComponent("Folder", true, false, 0.8, null, "newsettings", 0.8, null, ProfileCreatePanelComponent.J.l);
        this.createButton = new ProfileCreateActionButtonComponent("Create new", true, false, 0.8, null, "newadd", 0.8, J.z(), ProfileCreatePanelComponent.J.l);
        this.nameInput = new ProfileCreateNameInputComponent("Type name", null);
        this.divider = new ProfileCreateDividerComponent();
        this.settingsFrame = profilesSettingsFrame;
        this.folderButton.w("Open config folder");
        this.folderButton.addClickListener(this::openConfigFolder);
        this.createButton.addClickListener(this::startProfileCreation);
        this.createButton.w("Create a new config");
        this.addChildren(this.createButton, this.folderButton, this.divider);
    }

    ProfilesSettingsFrame getSettingsFrame() {
        return this.settingsFrame;
    }

    @Override
    public void H() {
        this.createButton.setTextScale(0.7);
        this.folderButton.setTextScale(0.7);
        this.createButton.setIconOffset(2.0);
        this.createButton.K(this.G$src$D$1b2f02a() + 5.0);
        this.createButton.S(this.n());
        this.createButton.Y(this.L() - 5.5);
        this.folderButton.setIconOffset(1.0);
        this.folderButton.S(this.n());
        this.folderButton.K(this.G$src$D$1b2f02a() + this.A() - this.folderButton.A() - 5.0);
        this.folderButton.Y(this.L() - 5.5);
        this.divider.setVisible(false);
    }

    @Override
    public double C() {
        return 20.0;
    }

    @Override
    public void g(GuiMouseEvent event) {
    }

    public void setPendingProfile(Profile profile) {
        this.pendingProfile = profile;
    }

    @Override
    public double x() {
        return 110.0;
    }
}
