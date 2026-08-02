package gg.vape.ui.click.frame.impl.profile;

import gg.vape.config.Profile;
import gg.vape.ui.click.component.GuiComponent;
import org.jetbrains.annotations.Nullable;

/** Local profile placeholder; no public-profile/avatar/network behavior. */
public class ProfileGlyphIconPanel extends GuiComponent {
    @Nullable
    private Profile profile;

    public ProfileGlyphIconPanel(@Nullable Profile profile) {
        this.profile = profile;
        this.setVisible(true);
    }

    public ProfileGlyphIconPanel(@Nullable Profile profile, double width, double height) {
        this(profile);
        this.o(width);
        this.Y(height);
    }

    public void setProfile(@Nullable Profile profile) {
        this.profile = profile;
    }

    public void refreshVisibility() {
        this.setVisible(this.profile != null);
    }
}
