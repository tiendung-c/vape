package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.ui.click.component.AnimatedIconButtonComponent;
import org.jetbrains.annotations.Nullable;

/** Local profile delete action. Remote/public-profile actions were removed. */
public class ProfileListEntryOpenButtonComponent extends AnimatedIconButtonComponent {
    private Profile profile;
    private Runnable afterDelete;

    private void refreshActionMode() {
        if (this.profile == null) {
            return;
        }
        boolean selected = Vape.INSTANCE.getProfilesManager().getActiveProfile().equals(this.profile);
        this.setIconResource("newtrash");
        this.getBackgroundAnimation().setEndColor(ProfileListEntryOpenButtonComponent.J.d);
        this.w(selected ? "You cannot delete your selected profile" : "Delete this profile");
    }

    private void handleClick() {
        if (this.profile == null || Vape.INSTANCE.getProfilesManager().getActiveProfile().equals(this.profile)) {
            return;
        }
        Vape.INSTANCE.getProfilesManager().removeProfile(this.profile);
        if (this.afterDelete != null) {
            this.afterDelete.run();
        }
    }

    public ProfileListEntryOpenButtonComponent(Profile profile, @Nullable Runnable afterDelete) {
        super("newtrash", ProfileListEntryOpenButtonComponent.J.d);
        this.profile = profile;
        this.afterDelete = afterDelete;
        this.setBorderRadius(2.0f);
        this.setBorderAlpha(1.0f);
        this.setIconScale(0.85);
        this.addClickListener(this::handleClick);
        this.refreshActionMode();
    }

    public ProfileListEntryOpenButtonComponent useOverlayStyle() {
        this.getBackgroundAnimation().setStartColor(ProfileListEntryOpenButtonComponent.J.l);
        this.setAnimatedBorderColor(ProfileListEntryOpenButtonComponent.J.l);
        this.setDisabledOverlayColor(ProfileListEntryOpenButtonComponent.J.m);
        return this;
    }

    @Override
    public void u() {
        this.refreshActionMode();
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Profile getProfile() {
        return this.profile;
    }
}
