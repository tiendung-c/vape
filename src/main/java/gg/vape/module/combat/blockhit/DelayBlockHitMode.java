package gg.vape.module.combat.blockhit;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventLivingUpdate;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.Mod;
import gg.vape.module.combat.BlockHit;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Minecraft;

public class DelayBlockHitMode
extends BlockHitMode {
    private final NumberValue maxHoldTime = NumberValue.create(
            this,
            "Max hold time",
            "#",
            "ms",
            50.0,
            250.0,
            500.0,
            10.0,
            "Maximum time to hold block before releasing");
    private final NumberValue cooldown = NumberValue.create(
            this,
            "Cooldown",
            "#",
            "ms",
            0.0,
            100.0,
            500.0,
            10.0,
            "Cooldown between blocks in milliseconds");
    private final BooleanValue unblockOnDamage = BooleanValue.create(
            this,
            "Unblock on damage",
            true,
            "Release block when taking damage");
    private final BooleanValue fakeBlockAnimation = BooleanValue.create(
            this,
            "Fake block animation",
            false,
            "Force client-side block animation only");
    private final BooleanValue requireLeftMousePressed = BooleanValue.create(
            this,
            "Require attack held",
            false,
            "Require left click to be held to block");
    private final BooleanValue requireRightMousePressed = BooleanValue.create(
            this,
            "Require use held",
            false,
            "Require right click to be held to block");

    private boolean blocking;
    private final TimerUtil blockTimer;
    private final TimerUtil cooldownTimer;
    private boolean inCooldown;

    public DelayBlockHitMode(Mod parent, String name) {
        super(parent, name);
        this.blockTimer = new TimerUtil();
        this.cooldownTimer = new TimerUtil();
        this.addValue(this.maxHoldTime, this.cooldown, this.unblockOnDamage,
                this.fakeBlockAnimation, this.requireLeftMousePressed, this.requireRightMousePressed);
    }

    @Override
    public boolean isBlocking() {
        return this.blocking;
    }

    @Override
    public String getDetailedSuffix() {
        return (int)this.maxHoldTime.getValue().doubleValue() + "ms / "
                + (int)this.cooldown.getValue().doubleValue() + "ms";
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (Minecraft.thePlayer().isNull()) {
            this.setBlocking(false);
            return;
        }
        if (!this.isHoldingSword()) {
            this.setBlocking(false);
            this.inCooldown = false;
            return;
        }
        if (Minecraft.currentScreen().isNotNull()) {
            return;
        }

        if (this.requireLeftMousePressed.getEffectiveValue()
                && !ClientSettings.isAttackButtonDown()) {
            this.setBlocking(false);
            return;
        }
        if (this.requireRightMousePressed.getEffectiveValue()
                && !ClientSettings.isUseItemButtonDown()) {
            this.setBlocking(false);
            return;
        }

        if (this.inCooldown) {
            if (this.cooldownTimer.hasTimeElapsed((long)this.cooldown.getValue().doubleValue())) {
                this.inCooldown = false;
            } else {
                return;
            }
        }

        if (this.blocking) {
            if (this.blockTimer.hasTimeElapsed((long)this.maxHoldTime.getValue().doubleValue())) {
                this.setBlocking(false);
                this.inCooldown = true;
                this.cooldownTimer.reset();
            }
            return;
        }

        if (this.shouldBlock()) {
            this.setBlocking(true);
            this.blockTimer.reset();
        }
    }

    @EventHandler
    public void onDamaged(EventLivingUpdate event) {
        if (!this.unblockOnDamage.getEffectiveValue()) {
            return;
        }
        if (!event.getEntity().getObject().equals(Minecraft.thePlayer().getObject())) {
            return;
        }
        if (event.getThePlayer().c$src$I$15a9iwo() <= 0) {
            return;
        }
        if (this.blocking) {
            this.setBlocking(false);
            this.inCooldown = true;
            this.cooldownTimer.reset();
        }
    }

    @Override
    public boolean shouldBlock() {
        BlockHit parent = (BlockHit)this.getParent();
        if (!parent.isHoldingSword()) {
            return false;
        }
        if (parent.requiresMouseDown() && !ClientSettings.isUseItemButtonDown()) {
            return false;
        }
        return parent.findDefaultTarget() != null;
    }

    private boolean isHoldingSword() {
        if (Minecraft.currentScreen().isNotNull()) {
            return false;
        }
        return Minecraft.thePlayer().getHeldItemHand().isNotNull()
                && ItemStackScoreUtil.h(Minecraft.thePlayer().getHeldItemHand().getItem());
    }

    private void setBlocking(boolean blocking) {
        if (this.blocking != blocking) {
            this.blocking = blocking;
            Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362().setPressed(blocking);
        }
    }
}
