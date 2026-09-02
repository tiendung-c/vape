package gg.vape.module.combat.aimassist;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.AimAssist;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.Vec3d;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

public class AimAssistLinearMode
extends SubModule<AimAssist> {
    @Nullable
    private EntityLivingBase target;
    private int retargetCounter;
    private final Random random;

    public AimAssistLinearMode(Mod parent, String name) {
        super(parent, name);
        this.random = new Random();
    }

    @Nullable
    public EntityLivingBase getTarget() {
        return this.target;
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        if (event.getWorld().isNull()) {
            return;
        }
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        AimAssist aimAssist = (AimAssist) this.getParent();
        if (!aimAssist.canAim()) {
            this.target = null;
            return;
        }
        if (aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()
                && !gg.vape.config.ClientSettings.isAttackButtonDown()) {
            this.target = null;
            return;
        }
        if (this.target != null && this.target.isNull()) {
            this.target = null;
        }
        if (this.target != null && (RotationUtil.C(this.target)
                || (double) Minecraft.thePlayer().getDistanceToEntity(this.target) > (Double) aimAssist.getDistance().getValue())) {
            this.target = null;
        }
        if (aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()
                && gg.vape.config.ClientSettings.isAttackButtonDown() && this.target == null
                || !aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()) {
            EntityLivingBase candidateTarget = aimAssist.findBestTarget();
            // Fix self-aim from findBestTarget
            if (candidateTarget != null && candidateTarget.equals(Minecraft.thePlayer())) {
                candidateTarget = null;
            }
            if (!aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()) {
                ++this.retargetCounter;
                if (this.retargetCounter > 700 || this.target == null || !aimAssist.isValidTarget(this.target)) {
                    if (candidateTarget != null && !aimAssist.isValidTarget(candidateTarget)) candidateTarget = null;
                    this.target = candidateTarget;
                    this.retargetCounter = 0;
                }
            } else {
                if (candidateTarget != null && !aimAssist.isValidTarget(candidateTarget)) candidateTarget = null;
                this.target = candidateTarget;
            }
        }
        if (this.target == null) {
            return;
        }
        // Fix self-aim: never aim at self
        if (this.target.equals(Minecraft.thePlayer())) {
            this.target = null;
            return;
        }
        if (Minecraft.currentScreen().isNotNull() || !gg.vape.module.none.ClientSettings.INSTANCE.inputEnabled) {
            return;
        }
        this.applyLinearRotation(aimAssist);
    }

    private void applyLinearRotation(AimAssist aimAssist) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || this.target == null || this.target.isNull()) return;
        // Fix self-aim: abort if target is self or same entity as player
        if (this.target.equals(player)) return;
        float partialTicks = Minecraft.getTimer().renderPartialTicks();

        Vec3d closestPoint = RotationUtil.T(player, this.target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(), 0.0, 0.0, 0.0);
        double targetX = closestPoint.getX();
        double targetY = closestPoint.getY();
        double targetZ = closestPoint.getZ();

        if (ForgeVersion.MC_1_7_10.L()) {
            targetY += (double) this.target.X();
        }
        if (aimAssist.targetArea.getValue() == aimAssist.closestAreaMode) {
            // closest point already computed
        } else {
            targetX = this.target.c();
            targetY = this.target.A();
            targetZ = this.target.Z();
            if (ForgeVersion.MC_1_7_10.L()) {
                targetY += (double) this.target.X();
            }
        }

        float viewYaw = RotationManager.getViewYaw(player);
        double horizontalAngleDiff = RotationUtil.C(player.c(), player.Z(), viewYaw, targetX, targetZ);
        int verticalAngleDiff = RotationUtil.H(player, targetX, targetY, targetZ);

        float horSpeed = ((Double) aimAssist.getHorizontalSpeed().getValue()).floatValue();
        float verSpeed = ((Double) aimAssist.getVerticalSpeed().getValue()).floatValue();

        float yawNeeded = (float) horizontalAngleDiff;
        float pitchNeeded = (float) verticalAngleDiff;

        float aimRandomization = 8.0f;
        if (aimRandomization > 0.01f) {
            float factor = aimRandomization / 100.0f;
            float rYaw = (float) this.random.nextGaussian() * aimRandomization * factor * 1.5f;
            float rPitch = (float) this.random.nextGaussian() * aimRandomization * factor;
            yawNeeded += rYaw;
            pitchNeeded += rPitch;
        }

        float horFactor = clamp(horSpeed * 0.022f * partialTicks, 0.0f, 1.0f);
        float verFactor = clamp(verSpeed * 0.022f * partialTicks, 0.0f, 1.0f);

        float yawStep = yawNeeded * horFactor;
        float pitchStep = pitchNeeded * verFactor;

        yawStep = applyGCDFix(yawStep);
        pitchStep = applyGCDFix(pitchStep);

        float mouseDeltaX = yawStep / 0.15f;
        float mouseDeltaY = -pitchStep / 0.15f;
        PlayerMouseRotationApplier.applyTrackedMouseDelta(mouseDeltaX, mouseDeltaY);
    }

    private float applyGCDFix(float delta) {
        float mouseSensitivity = Minecraft.gameSettings().y();
        if (mouseSensitivity <= 0.0f) return delta;
        float f = mouseSensitivity * 0.6f + 0.2f;
        float gcd = f * f * f * 1.2f;
        return Math.round(delta / gcd) * gcd;
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.retargetCounter = 0;
    }
}
