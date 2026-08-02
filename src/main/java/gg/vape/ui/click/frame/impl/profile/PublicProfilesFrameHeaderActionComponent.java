package gg.vape.ui.click.frame.impl.profile;

import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameHeaderComponent;

/** Generic local frame header action; the old public-profile transport is gone. */
public class PublicProfilesFrameHeaderActionComponent extends FrameHeaderComponent {
    private String iconResource;
    private String title;
    private final SquareIconButtonComponent closeButton;

    public PublicProfilesFrameHeaderActionComponent(Frame frame, String iconResource, String title) {
        this(frame, iconResource, title, 1.0);
    }

    public PublicProfilesFrameHeaderActionComponent(Frame frame, String iconResource, String title, double iconScale) {
        super(frame);
        this.iconResource = iconResource;
        this.title = title;
        this.closeButton = new SquareIconButtonComponent(iconResource, iconScale);
        this.addChildren(this.closeButton);
    }

    public SquareIconButtonComponent O$src$Lgg_vape_ui_click_component_SquareIconButtonComp$z3cp96() {
        return this.closeButton;
    }

    public PublicProfilesFrameHeaderActionComponent Q(GuiClickListener listener) {
        this.closeButton.addClickListener(listener);
        return this;
    }

    public String K$src$Ljava_lang_String_$bvh3j6() {
        return this.title;
    }

    public void j(String title) {
        this.title = title;
    }

    @Override
    public void H() {
        this.closeButton.K(this.G$src$D$1b2f02a() + 4.0);
        this.closeButton.S(this.n());
        this.closeButton.Y(this.L());
    }
}
