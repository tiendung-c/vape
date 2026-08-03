package gg.vape.module.combat.aimassist;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.AimAssist;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** OpenMyau-style AimAssist mode, ported to the current wrapper/event API. */
public final class AimModeMyau extends SubModule<AimAssist> {
    private final TimerUtil attackWindow = new TimerUtil();
    private final NumberValue horizontalSpeed = NumberValue.create(this, "Horizontal speed", "#.#", "", 0.0, 3.0, 10.0);
    private final NumberValue verticalSpeed = NumberValue.create(this, "Vertical speed", "#.#", "", 0.0, 0.0, 10.0);
    private final NumberValue smoothing = NumberValue.create(this, "Smoothing", "#", "%", 0.0, 50.0, 100.0);
    private final NumberValue range = NumberValue.create(this, "Range", "#.#", "", 3.0, 4.5, 8.0);
    private final NumberValue fov = NumberValue.create(this, "FOV", "#", "°", 30.0, 90.0, 360.0);
    private final BooleanValue weaponsOnly = BooleanValue.create(this, "Weapons only", true);
    private final BooleanValue allowTools = BooleanValue.create(this, "Allow tools", false);
    private final BooleanValue botCheck = BooleanValue.create(this, "Bot check", true);
    private final BooleanValue teamCheck = BooleanValue.create(this, "Team check", true);
    private final BooleanValue offlineJitter = BooleanValue.create(this, "Offline jitter", false,
            "Adds a small bounded visual variation for offline testing");
    private final NumberValue jitterAmount = NumberValue.create(this, "Jitter amount", "#.#", "°", 0.0, 0.2, 2.0, 0.1,
            "Maximum bounded aim variation");
    private final Random random = new Random();

    public AimModeMyau(Mod parent, String name) {
        super(parent, name);
        this.addValue(this.horizontalSpeed, this.verticalSpeed, this.smoothing, this.range, this.fov,
                this.weaponsOnly, this.allowTools, this.botCheck, this.teamCheck, this.offlineJitter, this.jitterAmount);
        this.offlineJitter.addDependentValues(this.jitterAmount);
    }

    public void recordAttack() {
        this.attackWindow.reset();
    }

    private boolean itemAllowed() {
        if (!this.weaponsOnly.getEffectiveValue()) return true;
        ItemStack stack = Minecraft.thePlayer().getHeldItemHand();
        if (stack.isNull()) return false;
        String id = stack.x().toLowerCase();
        return id.contains("sword") || this.allowTools.getEffectiveValue() &&
                (id.contains("pickaxe") || id.contains("shovel") || id.contains("axe"));
    }

    private boolean valid(EntityLivingBase target) {
        if (!this.getParent().isValidTarget(target)) return false;
        if (Minecraft.thePlayer().getDistanceToEntity(target) > ((Double)this.range.getValue()).floatValue()) return false;
        if (RotationUtil.a(Minecraft.thePlayer(), target) > ((Double)this.fov.getValue()).floatValue()) return false;
        if (this.botCheck.getEffectiveValue() && gg.vape.Vape.INSTANCE.getClientSettings().isBot(target)) return false;
        return !this.teamCheck.getEffectiveValue() || !gg.vape.Vape.INSTANCE.getClientSettings().isTeammate(target);
    }

    private EntityLivingBase findNearest() {
        WorldClient world = Minecraft.theWorld();
        if (world.isNull()) return null;
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        for (Object object : world.z()) {
            EntityLivingBase target = new EntityLivingBase(object);
            if (this.valid(target)) targets.add(target);
        }
        targets.sort(Comparator.comparingDouble((EntityLivingBase t) -> Minecraft.thePlayer().getDistanceToEntity(t)));
        return targets.isEmpty() ? null : targets.get(0);
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull() || Minecraft.currentScreen().isNotNull()) return;
        boolean attacking = ClientSettings.isAttackButtonDown();
        if (attacking) this.attackWindow.reset();
        if (!attacking && this.attackWindow.hasTimeElapsed(350L) || !itemAllowed() || !this.getParent().canAim()) return;
        EntityLivingBase target = findNearest();
        if (target == null) return;
        EntityPlayerSP player = Minecraft.thePlayer();
        double yawDiff = MathUtil.wrapAngleTo180(RotationUtil.g(player, target.c(), target.A(), target.Z()));
        double pitchDiff = RotationUtil.h(player, target.c(), target.A(), target.Z()) - player.V();
        float smooth = 1.0f - ((Double)this.smoothing.getValue()).floatValue() / 100.0f;
        float yawStep = (float)(yawDiff * 0.1 * ((Double)this.horizontalSpeed.getValue()).floatValue() * smooth);
        float pitchStep = (float)(pitchDiff * 0.1 * ((Double)this.verticalSpeed.getValue()).floatValue() * smooth);
        if (this.offlineJitter.getEffectiveValue()) {
            float bound = ((Double)this.jitterAmount.getValue()).floatValue();
            yawStep += (this.random.nextFloat() * 2.0f - 1.0f) * bound;
            pitchStep += (this.random.nextFloat() * 2.0f - 1.0f) * bound;
        }
        PlayerMouseRotationApplier.applyTrackedMouseDelta(yawStep, -pitchStep);
    }
}
