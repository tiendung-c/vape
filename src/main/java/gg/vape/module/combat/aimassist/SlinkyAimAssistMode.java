package gg.vape.module.combat.aimassist;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.AimAssist;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Camera-based Aim Assist mode with the user-facing controls documented by
 * Slinky. Silent packet rotations intentionally remain outside this mode.
 */
public final class SlinkyAimAssistMode extends SubModule<AimAssist> {
    private final ModeOption regularRotation = new ModeOption("Regular");
    private final ModeOption linearRotation = new ModeOption("Linear");
    private final ModeOption lockOnRotation = new ModeOption("Lock-on");
    private final ModeValue rotationMode = ModeValue.create(this, "Rotation mode", (ModeSelection)this.regularRotation, this.regularRotation, this.linearRotation, this.lockOnRotation);
    private final RandomValue horizontalSpeed = RandomValue.createWithDescription(this, "Horizontal speed", "#.#", "deg", 0.0, 4.0, 4.0, 20.0, 0.1, "Randomized horizontal camera speed range");
    private final RandomValue verticalSpeed = RandomValue.createWithDescription(this, "Vertical speed", "#.#", "deg", 0.0, 2.5, 2.5, 20.0, 0.1, "Randomized vertical camera speed range");
    private final BooleanValue aimVertically = BooleanValue.create(this, "Aim vertically", true);
    private final NumberValue horizontalMultipoint = NumberValue.create(this, "Horizontal multipoint", "#", "%", 0.0, 100.0, 100.0, 1.0);
    private final NumberValue verticalMultipoint = NumberValue.create(this, "Vertical multipoint", "#", "%", 0.0, 100.0, 100.0, 1.0);
    private final NumberValue prediction = NumberValue.create(this, "Predict", "#", "%", 0.0, 5.0, 100.0, 1.0);
    private final NumberValue randomization = NumberValue.create(this, "Randomization", "#", "%", 0.0, 10.0, 100.0, 1.0);
    private final ModeOption singleTargetMode = new ModeOption("Single");
    private final ModeOption switchTargetMode = new ModeOption("Switch");
    private final ModeValue targetMode = ModeValue.create(this, "Target mode", (ModeSelection)this.singleTargetMode, this.singleTargetMode, this.switchTargetMode);
    private final BooleanValue increasedFovWhileLocked = BooleanValue.create(this, "Increased FOV while locked", true);
    private final ModeOption sortDistance = new ModeOption("Distance");
    private final ModeOption sortAimAngle = new ModeOption("Aim angle");
    private final ModeOption sortHealth = new ModeOption("Health");
    private final ModeValue switchSort = ModeValue.create(this, "Sort by", (ModeSelection)this.sortAimAngle, this.sortDistance, this.sortAimAngle, this.sortHealth);
    private final RandomValue range = RandomValue.createWithDescription(this, "Range", "#.#", "blocks", 0.0, 0.0, 4.5, 8.0, 0.1, "Minimum and maximum target range");
    private final RandomValue fov = RandomValue.createWithDescription(this, "FOV", "#", "deg", 0.0, 0.0, 90.0, 360.0, 1.0, "Minimum and maximum field of view");
    private final BooleanValue requireSprint = BooleanValue.create(this, "Require sprint key", false);
    private final BooleanValue requireMouseMoved = BooleanValue.create(this, "Require mouse moved", false, "Requires a change in camera rotation before aiming");
    private final BooleanValue ignoreInvisibles = BooleanValue.create(this, "Ignore invisibles", true);
    private final BooleanValue requireVisible = BooleanValue.create(this, "Not behind blocks", true);
    private final Random random = new Random();
    @Nullable
    private EntityLivingBase lockedTarget;
    private float lastViewYaw;
    private float lastViewPitch;
    private boolean viewInitialized;

    public SlinkyAimAssistMode(Mod parent, String name) {
        super(parent, name);
        this.aimVertically.addDependentValues(this.verticalSpeed, this.verticalMultipoint);
        this.targetMode.addActiveMode(this.increasedFovWhileLocked, this.singleTargetMode);
        this.targetMode.addActiveMode(this.switchSort, this.switchTargetMode);
        this.addValue(this.rotationMode, this.horizontalSpeed, this.aimVertically, this.verticalSpeed,
                this.horizontalMultipoint, this.verticalMultipoint, this.prediction, this.randomization,
                this.targetMode, this.increasedFovWhileLocked, this.switchSort, this.range,
                this.fov, this.requireSprint,
                this.requireMouseMoved, this.ignoreInvisibles, this.requireVisible);
    }

    @Nullable
    public EntityLivingBase getTarget() {
        return this.lockedTarget;
    }

    private boolean hasCameraMoved(EntityPlayerSP player) {
        float yaw = player.J();
        float pitch = player.V();
        boolean moved = !this.viewInitialized || Math.abs(MathUtil.wrapAngleTo180(yaw - this.lastViewYaw)) > 0.01f || Math.abs(pitch - this.lastViewPitch) > 0.01f;
        this.lastViewYaw = yaw;
        this.lastViewPitch = pitch;
        this.viewInitialized = true;
        return moved;
    }

    private boolean isValid(EntityLivingBase target, boolean locked) {
        AimAssist aimAssist = (AimAssist)this.getParent();
        EntityPlayerSP player = Minecraft.thePlayer();
        if (!aimAssist.isValidTarget(target)) return false;
        float distance = player.getDistanceToEntity(target);
        if (distance < this.range.getMinimumValue() || distance > this.range.getMaximumValue()) return false;
        if (this.ignoreInvisibles.getEffectiveValue() && target.J$src$Z$fdev5g()) return false;
        if (this.requireVisible.getEffectiveValue() && !player.canEntityBeSeen(target)) return false;
        double allowedFov = this.fov.getMaximumValue();
        if (locked && this.increasedFovWhileLocked.getEffectiveValue()) allowedFov = Math.min(360.0, allowedFov * 1.5);
        return RotationUtil.a(player, target) <= allowedFov / 2.0;
    }

    @Nullable
    private EntityLivingBase selectTarget() {
        WorldClient world = Minecraft.theWorld();
        if (world.isNull()) return null;
        if (this.targetMode.getValue() == this.singleTargetMode && this.lockedTarget != null && this.isValid(this.lockedTarget, true)) return this.lockedTarget;
        List<EntityLivingBase> candidates = new ArrayList<EntityLivingBase>();
        for (Object object : world.z()) {
            Entity entity = new Entity(object);
            if (entity.isNull()) continue;
            EntityLivingBase target = new EntityLivingBase(object);
            if (this.isValid(target, false)) candidates.add(target);
        }
        if (candidates.isEmpty()) return null;
        Comparator<EntityLivingBase> comparator;
        if (this.switchSort.getValue() == this.sortDistance) {
            comparator = Comparator.comparingDouble(target -> Minecraft.thePlayer().getDistanceToEntity(target));
        } else if (this.switchSort.getValue() == this.sortHealth) {
            comparator = Comparator.comparingDouble(target -> target.w$src$F$15l9epb());
        } else {
            comparator = Comparator.comparingDouble(target -> RotationUtil.a(Minecraft.thePlayer(), target));
        }
        candidates.sort(comparator);
        return candidates.get(0);
    }

    private double[] resolveAimPoint(EntityPlayerSP player, EntityLivingBase target) {
        AxisAlignedBB box = target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
        double centerX = (box.getMinX() + box.getMaxX()) * 0.5;
        double centerY = (box.getMinY() + box.getMaxY()) * 0.5;
        double centerZ = (box.getMinZ() + box.getMaxZ()) * 0.5;
        double closestX = Math.max(box.getMinX(), Math.min(box.getMaxX(), player.c()));
        double closestY = Math.max(box.getMinY(), Math.min(box.getMaxY(), player.A() + player.X()));
        double closestZ = Math.max(box.getMinZ(), Math.min(box.getMaxZ(), player.Z()));
        double horizontalBlend = (Double)this.horizontalMultipoint.getValue() / 100.0;
        double verticalBlend = (Double)this.verticalMultipoint.getValue() / 100.0;
        double lead = (Double)this.prediction.getValue() / 100.0;
        return new double[]{centerX + (closestX - centerX) * horizontalBlend + target.t() * lead,
                centerY + (closestY - centerY) * verticalBlend + target.q() * lead,
                centerZ + (closestZ - centerZ) * horizontalBlend + target.T() * lead};
    }

    private void applyRotation(EntityPlayerSP player, EntityLivingBase target) {
        double[] point = this.resolveAimPoint(player, target);
        double dx = point[0] - player.c();
        double dz = point[2] - player.Z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(point[1] - player.A() - player.X(), Math.max(0.0001, horizontalDistance))));
        float yawDifference = MathUtil.wrapAngleTo180(targetYaw - player.J());
        float pitchDifference = targetPitch - player.V();
        float yawStep;
        float pitchStep;
        if (this.rotationMode.getValue() == this.lockOnRotation) {
            yawStep = yawDifference;
            pitchStep = pitchDifference;
        } else {
            float horizontal = (float)this.horizontalSpeed.getRandomValue();
            float vertical = (float)this.verticalSpeed.getRandomValue();
            if (this.rotationMode.getValue() == this.regularRotation) {
                horizontal *= Math.max(0.15f, Math.min(1.0f, Math.abs(yawDifference) / 45.0f));
                vertical *= Math.max(0.15f, Math.min(1.0f, Math.abs(pitchDifference) / 30.0f));
            }
            yawStep = Math.max(-horizontal, Math.min(horizontal, yawDifference));
            pitchStep = Math.max(-vertical, Math.min(vertical, pitchDifference));
        }
        float spread = ((Double)this.randomization.getValue()).floatValue() / 100.0f * 0.25f;
        float freeAimHalfFov = (float)this.fov.getMinimumValue() * 0.5f;
        if (Math.abs(yawDifference) <= freeAimHalfFov) {
            yawStep = 0.0f;
        }
        yawStep += (this.random.nextFloat() - 0.5f) * spread;
        pitchStep += (this.random.nextFloat() - 0.5f) * spread;
        PlayerMouseRotationApplier.applyTrackedMouseDelta(yawStep / 0.15f, this.aimVertically.getEffectiveValue() ? -pitchStep / 0.15f : 0.0f);
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || Minecraft.theWorld().isNull() || Minecraft.currentScreen().isNotNull()) return;
        boolean moved = this.hasCameraMoved(player);
        if (!this.getParent().canAim()
                || this.requireSprint.getEffectiveValue() && !Minecraft.gameSettings().r().isKeyDown()
                || this.requireMouseMoved.getEffectiveValue() && !moved) {
            this.lockedTarget = null;
            return;
        }
        this.lockedTarget = this.selectTarget();
        if (this.lockedTarget != null) this.applyRotation(player, this.lockedTarget);
    }

    @Override
    public void onDisable() {
        this.lockedTarget = null;
        this.viewInitialized = false;
    }
}
