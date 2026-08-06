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
 * Slinky Aim Assist — Polar-aware, all three rotation modes.
 *
 * Changes vs original:
 *  1. Gaussian noise (Box-Muller) replaces uniform spread — matches human tremor distribution
 *  2. Adaptive smooth blend factor (angle-distance adaptive) — removes fixed 0.65/0.35 EMA fingerprint
 *  3. Per-axis speed variance resampled on target switch AND periodically mid-lock
 *  4. Free-aim dead zone widens when yaw delta is small — kills micro-snap signature
 *  5. Occasional deliberate overshoot + correction — human flick-correct behaviour
 *  6. Pitch assist gated behind minimum horizontal error threshold — yaw-first human pattern
 *  7. Linear mode: sqrt ease-out curve + micro-stutter — breaks constant-velocity flag
 */
public final class SlinkyAimAssistMode extends SubModule<AimAssist> {

    // ── Rotation modes ─────────────────────────────────────────────────────────
    private final ModeOption regularRotation = new ModeOption("Regular");
    private final ModeOption linearRotation  = new ModeOption("Linear");
    private final ModeOption lockOnRotation  = new ModeOption("Lock-on");
    private final ModeValue rotationMode = ModeValue.create(this, "Rotation mode",
            (ModeSelection) this.regularRotation,
            this.regularRotation, this.linearRotation, this.lockOnRotation);

    // ── Speed ──────────────────────────────────────────────────────────────────
    private final RandomValue horizontalSpeed = RandomValue.createWithDescription(
            this, "Horizontal speed", "#.#", "deg",
            0.0, 4.0, 4.0, 20.0, 0.1,
            "Randomized horizontal camera speed range");
    private final RandomValue verticalSpeed = RandomValue.createWithDescription(
            this, "Vertical speed", "#.#", "deg",
            0.0, 2.5, 2.5, 20.0, 0.1,
            "Randomized vertical camera speed range");
    private final BooleanValue aimVertically = BooleanValue.create(this, "Aim vertically", true);

    // ── Multipoint ─────────────────────────────────────────────────────────────
    private final NumberValue horizontalMultipoint = NumberValue.create(
            this, "Horizontal multipoint", "#", "%", 0.0, 100.0, 100.0, 1.0);
    private final NumberValue verticalMultipoint = NumberValue.create(
            this, "Vertical multipoint", "#", "%", 0.0, 100.0, 100.0, 1.0);

    // ── Behaviour ──────────────────────────────────────────────────────────────
    private final NumberValue prediction    = NumberValue.create(this, "Predict",       "#", "%", 0.0, 5.0,  100.0, 1.0);
    private final NumberValue randomization = NumberValue.create(this, "Randomization", "#", "%", 0.0, 10.0, 100.0, 1.0);

    // ── Target ─────────────────────────────────────────────────────────────────
    private final ModeOption singleTargetMode = new ModeOption("Single");
    private final ModeOption switchTargetMode = new ModeOption("Switch");
    private final ModeValue targetMode = ModeValue.create(this, "Target mode",
            (ModeSelection) this.singleTargetMode,
            this.singleTargetMode, this.switchTargetMode);
    private final BooleanValue increasedFovWhileLocked =
            BooleanValue.create(this, "Increased FOV while locked", true);

    // ── Sort ───────────────────────────────────────────────────────────────────
    private final ModeOption sortDistance = new ModeOption("Distance");
    private final ModeOption sortAimAngle = new ModeOption("Aim angle");
    private final ModeOption sortHealth   = new ModeOption("Health");
    private final ModeValue switchSort = ModeValue.create(this, "Sort by",
            (ModeSelection) this.sortAimAngle,
            this.sortDistance, this.sortAimAngle, this.sortHealth);

    // ── Range / FOV ────────────────────────────────────────────────────────────
    private final RandomValue range = RandomValue.createWithDescription(
            this, "Range", "#.#", "blocks",
            0.0, 0.0, 4.5, 8.0, 0.1,
            "Minimum and maximum target range");
    private final RandomValue fov = RandomValue.createWithDescription(
            this, "FOV", "#", "deg",
            0.0, 0.0, 90.0, 360.0, 1.0,
            "Minimum and maximum field of view");

    // ── Guards ─────────────────────────────────────────────────────────────────
    private final BooleanValue requireMouseMoved = BooleanValue.create(this, "Require mouse moved", false,
            "Requires a change in camera rotation before aiming");
    private final BooleanValue ignoreInvisibles  = BooleanValue.create(this, "Ignore invisibles",   true);
    private final BooleanValue requireVisible    = BooleanValue.create(this, "Not behind blocks",   true);

    // ── Runtime state ──────────────────────────────────────────────────────────
    private final Random random = new Random();

    @Nullable private EntityLivingBase lockedTarget;
    @Nullable private EntityLivingBase rotationTarget;

    private float   smoothedYawStep;
    private float   smoothedPitchStep;
    private boolean rotationSmoothingInitialized;

    private float lockedHorizontalSpeed;
    private float lockedVerticalSpeed;

    // Speed resample timer — resample every 8–18 ticks to break constant-speed flag
    private int speedResampleCountdown = 0;

    // Overshoot state — occasional deliberate over-flick + correction
    private boolean overshootActive;
    private float   overshootYawBias;
    private float   overshootPitchBias;
    private int     overshootTicks;

    private float   lastViewYaw;
    private float   lastViewPitch;
    private boolean viewInitialized;

    // ── Constructor ────────────────────────────────────────────────────────────
    public SlinkyAimAssistMode(Mod parent, String name) {
        super(parent, name);
        this.aimVertically.addDependentValues(this.verticalSpeed, this.verticalMultipoint);
        this.targetMode.addActiveMode(this.increasedFovWhileLocked, this.singleTargetMode);
        this.targetMode.addActiveMode(this.switchSort, this.switchTargetMode);
        this.addValue(
                this.rotationMode, this.horizontalSpeed, this.aimVertically, this.verticalSpeed,
                this.horizontalMultipoint, this.verticalMultipoint, this.prediction,
                this.randomization, this.targetMode, this.increasedFovWhileLocked,
                this.switchSort, this.range, this.fov,
                this.requireMouseMoved, this.ignoreInvisibles, this.requireVisible);
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    @Nullable
    public EntityLivingBase getTarget() {
        return this.lockedTarget;
    }

    // ── Gaussian noise — Box-Muller transform ──────────────────────────────────
    // Human micro-tremor is Gaussian distributed.
    // Uniform noise produces a flat frequency spectrum — Polar's FFT check flags it.
    private float gaussianNoise(float stddev) {
        double u1 = Math.max(1e-10, random.nextDouble());
        double u2 = random.nextDouble();
        double z  = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return (float) (z * stddev);
    }

    // ── Adaptive smooth factor ─────────────────────────────────────────────────
    // Fixed EMA ratio (e.g. 0.65/0.35) is a trivial fingerprint for rotation ACs.
    // This varies the blend with angular error and adds per-tick jitter.
    //
    // Large angle → heavier momentum (0.75) — lazy far-tracking
    // Small angle → lighter momentum (0.45) — snappy close-tracking
    private float adaptiveSmoothFactor(float angularError, float maxSpeed) {
        float normalised = Math.min(1.0f, Math.abs(angularError) / Math.max(0.001f, maxSpeed * 20.0f));
        float base = 0.45f + normalised * 0.30f;
        base += gaussianNoise(0.015f);
        return Math.max(0.30f, Math.min(0.82f, base));
    }

    // ── Periodic mid-lock speed resample ───────────────────────────────────────
    // Constant speed through an entire lock session is a statistical flag.
    // Resample every 8–18 ticks (0.4s–0.9s at 20 tps).
    private void tickSpeedResample() {
        if (--speedResampleCountdown <= 0) {
            lockedHorizontalSpeed  = (float) horizontalSpeed.getRandomValue();
            lockedVerticalSpeed    = (float) verticalSpeed.getRandomValue();
            speedResampleCountdown = 8 + random.nextInt(11);
        }
    }

    // ── Camera moved check ─────────────────────────────────────────────────────
    private boolean hasCameraMoved(EntityPlayerSP player) {
        float yaw   = player.J();
        float pitch = player.V();
        boolean moved = !this.viewInitialized
                || Math.abs(MathUtil.wrapAngleTo180(yaw - this.lastViewYaw)) > 0.01f
                || Math.abs(pitch - this.lastViewPitch) > 0.01f;
        this.lastViewYaw   = yaw;
        this.lastViewPitch = pitch;
        this.viewInitialized = true;
        return moved;
    }

    // ── Target validity ────────────────────────────────────────────────────────
    private boolean isValid(EntityLivingBase target, boolean locked) {
        AimAssist      aimAssist = (AimAssist) this.getParent();
        EntityPlayerSP player   = Minecraft.thePlayer();
        if (!aimAssist.isValidTarget(target)) return false;
        float distance = player.getDistanceToEntity(target);
        if (distance < this.range.getMinimumValue() || distance > this.range.getMaximumValue()) return false;
        if (this.ignoreInvisibles.getEffectiveValue() && target.J$src$Z$fdev5g()) return false;
        if (this.requireVisible.getEffectiveValue() && !player.canEntityBeSeen(target)) return false;
        double allowedFov = this.fov.getMaximumValue();
        if (locked && this.increasedFovWhileLocked.getEffectiveValue())
            allowedFov = Math.min(360.0, allowedFov * 1.5);
        return RotationUtil.a(player, target) <= allowedFov / 2.0;
    }

    // ── Target selection ───────────────────────────────────────────────────────
    @Nullable
    private EntityLivingBase selectTarget() {
        WorldClient world = Minecraft.theWorld();
        if (world.isNull()) return null;
        if (this.targetMode.getValue() == this.singleTargetMode
                && this.lockedTarget != null
                && this.isValid(this.lockedTarget, true))
            return this.lockedTarget;

        List<EntityLivingBase> candidates = new ArrayList<>();
        for (Object object : world.z()) {
            Entity entity = new Entity(object);
            if (entity.isNull()) continue;
            EntityLivingBase target = new EntityLivingBase(object);
            if (this.isValid(target, false)) candidates.add(target);
        }
        if (candidates.isEmpty()) return null;

        Comparator<EntityLivingBase> comparator;
        if (this.switchSort.getValue() == this.sortDistance) {
            comparator = Comparator.comparingDouble(t -> Minecraft.thePlayer().getDistanceToEntity(t));
        } else if (this.switchSort.getValue() == this.sortHealth) {
            comparator = Comparator.comparingDouble(t -> t.w$src$F$15l9epb());
        } else {
            comparator = Comparator.comparingDouble(t -> RotationUtil.a(Minecraft.thePlayer(), t));
        }
        candidates.sort(comparator);
        return candidates.get(0);
    }

    // ── Aim point resolver ─────────────────────────────────────────────────────
    private double[] resolveAimPoint(EntityPlayerSP player, EntityLivingBase target) {
        AxisAlignedBB box     = target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
        double centerX  = (box.getMinX() + box.getMaxX()) * 0.5;
        double centerY  = (box.getMinY() + box.getMaxY()) * 0.5;
        double centerZ  = (box.getMinZ() + box.getMaxZ()) * 0.5;
        double closestX = Math.max(box.getMinX(), Math.min(box.getMaxX(), player.c()));
        double closestY = Math.max(box.getMinY(), Math.min(box.getMaxY(), player.A() + player.X()));
        double closestZ = Math.max(box.getMinZ(), Math.min(box.getMaxZ(), player.Z()));
        double hBlend   = (Double) this.horizontalMultipoint.getValue() / 100.0;
        double vBlend   = (Double) this.verticalMultipoint.getValue()   / 100.0;
        double lead     = (Double) this.prediction.getValue()           / 100.0;
        return new double[]{
                centerX + (closestX - centerX) * hBlend + target.t() * lead,
                centerY + (closestY - centerY) * vBlend + target.q() * lead,
                centerZ + (closestZ - centerZ) * hBlend + target.T() * lead
        };
    }

    // ── Core rotation applier ──────────────────────────────────────────────────
    private void applyRotation(EntityPlayerSP player, EntityLivingBase target) {

        // Target switch: reset smoothing, resample speeds immediately
        if (target != this.rotationTarget) {
            this.rotationTarget               = target;
            this.rotationSmoothingInitialized = false;
            this.lockedHorizontalSpeed        = (float) this.horizontalSpeed.getRandomValue();
            this.lockedVerticalSpeed          = (float) this.verticalSpeed.getRandomValue();
            this.speedResampleCountdown       = 8 + random.nextInt(11);
            this.overshootActive              = false;
        }

        // Periodic mid-lock speed resample
        tickSpeedResample();

        // Raw angular deltas
        double[] point = this.resolveAimPoint(player, target);
        double dx = point[0] - player.c();
        double dz = point[2] - player.Z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float targetYaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(
                Math.atan2(point[1] - player.A() - player.X(),
                           Math.max(0.0001, horizontalDistance))));

        float yawDifference   = MathUtil.wrapAngleTo180(targetYaw - player.J());
        float pitchDifference = targetPitch - player.V();

        float yawStep;
        float pitchStep;

        // ── Lock-on mode ───────────────────────────────────────────────────────
        if (this.rotationMode.getValue() == this.lockOnRotation) {
            // Hard snap — no smoothing, no noise. Still adds spread so it's not
            // a perfect 0-error frame every tick (that itself is a flag).
            float spreadStddev = ((Double) this.randomization.getValue()).floatValue() / 100.0f * 0.12f;
            yawStep   = yawDifference   + gaussianNoise(spreadStddev);
            pitchStep = pitchDifference + gaussianNoise(spreadStddev * 0.6f);

        // ── Linear mode ────────────────────────────────────────────────────────
        } else if (this.rotationMode.getValue() == this.linearRotation) {
            // Original problem: constant velocity every tick → flat velocity profile
            // → Polar's linear regression check flags zero variance immediately.
            //
            // Fix: sqrt ease-out curve makes velocity decrease as target is approached.
            // Micro-stutter (3% chance) adds inter-frame delta variance.
            // Light EMA smoothing with per-tick jitter on the blend ratio.

            float horizontal = lockedHorizontalSpeed;
            float vertical   = lockedVerticalSpeed;

            // Sqrt ease-out: fast approach, slow arrival
            float yawScale   = (float) Math.sqrt(Math.min(1.0f, Math.abs(yawDifference)   / 30.0f));
            float pitchScale = (float) Math.sqrt(Math.min(1.0f, Math.abs(pitchDifference) / 20.0f));

            yawStep   = Math.max(-horizontal, Math.min(horizontal, yawDifference))   * Math.max(0.20f, yawScale);
            pitchStep = Math.max(-vertical,   Math.min(vertical,   pitchDifference)) * Math.max(0.20f, pitchScale);

            // Pitch gate: humans correct yaw first, pitch second
            if (Math.abs(yawDifference) > 10.0f) {
                pitchStep *= 0.20f;
            }

            // Micro-stutter: 3% chance to drop one tick — human hesitation pattern
            if (random.nextFloat() < 0.03f) {
                yawStep   = 0.0f;
                pitchStep = 0.0f;
            }

            // Light EMA — Linear needs less momentum than Regular
            if (rotationSmoothingInitialized) {
                float yawBlend   = 0.30f + gaussianNoise(0.04f);
                float pitchBlend = 0.25f + gaussianNoise(0.03f);
                yawBlend   = Math.max(0.15f, Math.min(0.55f, yawBlend));
                pitchBlend = Math.max(0.12f, Math.min(0.50f, pitchBlend));
                yawStep    = smoothedYawStep   * yawBlend   + yawStep   * (1.0f - yawBlend);
                pitchStep  = smoothedPitchStep * pitchBlend + pitchStep * (1.0f - pitchBlend);
            }
            smoothedYawStep   = yawStep;
            smoothedPitchStep = pitchStep;
            rotationSmoothingInitialized = true;

            // Gaussian spread
            float spreadStddev = ((Double) this.randomization.getValue()).floatValue() / 100.0f * 0.18f;
            yawStep   += gaussianNoise(spreadStddev);
            pitchStep += gaussianNoise(spreadStddev * 0.6f);

        // ── Regular mode ───────────────────────────────────────────────────────
        } else {
            // Original problem: fixed 0.65/0.35 EMA ratio — trivial AC fingerprint.
            // Uniform spread — flat frequency spectrum, flagged by FFT checks.
            //
            // Fix: adaptive blend factor + Gaussian noise + overshoot simulation.

            float horizontal = lockedHorizontalSpeed;
            float vertical   = lockedVerticalSpeed;

            // Distance-proportional scaling — unchanged from original behaviour
            horizontal *= Math.max(0.15f, Math.min(1.0f, Math.abs(yawDifference)   / 45.0f));
            vertical   *= Math.max(0.15f, Math.min(1.0f, Math.abs(pitchDifference) / 30.0f));

            yawStep   = Math.max(-horizontal, Math.min(horizontal, yawDifference));
            pitchStep = Math.max(-vertical,   Math.min(vertical,   pitchDifference));

            // Pitch gate: yaw correction dominates when error is large
            if (Math.abs(yawDifference) > 12.0f) {
                pitchStep *= 0.25f;
            }

            // Overshoot simulation: ~4% chance when close to target
            // Simulates human over-flick + pull-back correction behaviour
            if (!overshootActive && Math.abs(yawDifference) < 8.0f && random.nextFloat() < 0.04f) {
                overshootActive    = true;
                overshootYawBias   = (random.nextFloat() * 2.0f + 0.8f) * Math.signum(yawDifference);
                overshootPitchBias = gaussianNoise(0.3f);
                overshootTicks     = 2 + random.nextInt(3);
            }
            if (overshootActive) {
                yawDifference   += overshootYawBias;
                pitchDifference += overshootPitchBias;
                if (--overshootTicks <= 0) {
                    overshootActive    = false;
                    overshootYawBias   = 0f;
                    overshootPitchBias = 0f;
                }
            }

            // Adaptive EMA — blend ratio varies with angular error + per-tick jitter
            if (rotationSmoothingInitialized) {
                float yawBlend   = adaptiveSmoothFactor(yawDifference,   horizontal);
                float pitchBlend = adaptiveSmoothFactor(pitchDifference, vertical);
                yawStep   = smoothedYawStep   * yawBlend   + yawStep   * (1.0f - yawBlend);
                pitchStep = smoothedPitchStep * pitchBlend + pitchStep * (1.0f - pitchBlend);
            }
            smoothedYawStep   = yawStep;
            smoothedPitchStep = pitchStep;
            rotationSmoothingInitialized = true;

            // Dynamic dead zone — widens when error is small to kill micro-snap signature
            float freeAimHalfFov  = (float) this.fov.getMinimumValue() * 0.5f;
            float dynamicDeadZone = freeAimHalfFov
                    * (1.0f + (1.0f - Math.min(1.0f, Math.abs(yawDifference) / 20.0f)) * 0.4f);
            if (Math.abs(yawDifference) <= dynamicDeadZone) {
                yawStep = 0.0f;
            }

            // Gaussian spread noise
            float spreadStddev = ((Double) this.randomization.getValue()).floatValue() / 100.0f * 0.18f;
            yawStep   += gaussianNoise(spreadStddev);
            pitchStep += gaussianNoise(spreadStddev * 0.6f);
        }

        // ── Apply to camera ────────────────────────────────────────────────────
        PlayerMouseRotationApplier.applyTrackedMouseDelta(
                yawStep / 0.15f,
                this.aimVertically.getEffectiveValue() ? -pitchStep / 0.15f : 0.0f
        );
    }

    // ── Event handler ──────────────────────────────────────────────────────────
    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || Minecraft.theWorld().isNull() || Minecraft.currentScreen().isNotNull()) return;

        boolean moved = this.hasCameraMoved(player);
        if (!this.getParent().canAim()
                || this.requireMouseMoved.getEffectiveValue() && !moved) {
            this.lockedTarget             = null;
            this.rotationTarget           = null;
            this.rotationSmoothingInitialized = false;
            return;
        }

        this.lockedTarget = this.selectTarget();
        if (this.lockedTarget != null) this.applyRotation(player, this.lockedTarget);
    }

    // ── Disable ────────────────────────────────────────────────────────────────
    @Override
    public void onDisable() {
        this.lockedTarget             = null;
        this.rotationTarget           = null;
        this.rotationSmoothingInitialized = false;
        this.viewInitialized          = false;
        this.overshootActive          = false;
        this.speedResampleCountdown   = 0;
    }
}
