package gg.vape.module.blatant.scaffold;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPostTick;
import gg.vape.event.impl.EventPreEntityUpdate;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.KeyBindingInputState;
import gg.vape.input.KeyboardCodeUtil;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.blatant.Scaffold;
import gg.vape.module.blatant.scaffold.ScaffoldEdgeSneakHelper;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.movement.MovementInputHelper;
import gg.vape.movement.PlayerMovementTaskManager;
import gg.vape.movement.TargetPositionMovementTask;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameSettings;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import java.math.BigDecimal;
import java.util.ArrayList;

public class TellyBridgeScaffoldMode
extends SubModule<Scaffold> {
    private final BooleanValue requireRightClick;
    private final boolean autoStart;
    private final ModeOption rotationNone = new ModeOption("None");
    private final ModeOption rotationDefault = new ModeOption("Default");
    private final ModeOption rotationBackwards = new ModeOption("Backwards");
    private final ModeOption rotationSideways = new ModeOption("Sideways");
    private final ModeOption rotationGodBridge = new ModeOption("GodBridge");
    private final ModeOption rotationSmooth = new ModeOption("Smooth");
    private final ModeOption rotationHypixel = new ModeOption("Hypixel");
    private final ModeOption rotationSnap = new ModeOption("Snap");
    private final ModeOption rotation3Fmc = new ModeOption("3FMC");
    private final ModeOption rotationSnap2 = new ModeOption("Snap2");
    private final ModeValue openMyauRotationMode = ModeValue.create(this, "Rotation mode", (ModeSelection)this.rotationBackwards, this.rotationNone, this.rotationDefault, this.rotationBackwards, this.rotationSideways, this.rotationGodBridge, this.rotationSmooth, this.rotationHypixel, this.rotationSnap, this.rotation3Fmc, this.rotationSnap2);
    private final BooleanValue tellyRotations = BooleanValue.create(this, "Rotation Telly", true, "Uses separate start and normal rotation speeds for Telly bridging");
    private final NumberValue tellyStartRotationMin = NumberValue.create(this, "Telly start rotation min", "#", "deg/s", 1.0, 90.0, 180.0, 1.0);
    private final NumberValue tellyStartRotationMax = NumberValue.create(this, "Telly start rotation max", "#", "deg/s", 1.0, 95.0, 180.0, 1.0);
    private final NumberValue tellyNormalRotationMin = NumberValue.create(this, "Telly normal rotation min", "#", "deg/s", 1.0, 30.0, 180.0, 1.0);
    private final NumberValue tellyNormalRotationMax = NumberValue.create(this, "Telly normal rotation max", "#", "deg/s", 1.0, 35.0, 180.0, 1.0);
    private final ModeOption moveFixNone = new ModeOption("None");
    private final ModeOption moveFixSilent = new ModeOption("Silent");
    private final ModeValue moveFixMode = ModeValue.create(this, "Move fix", (ModeSelection)this.moveFixSilent, this.moveFixNone, this.moveFixSilent);
    private final ModeOption sprintNone = new ModeOption("None");
    private final ModeOption sprintVanilla = new ModeOption("Vanilla");
    private final ModeValue sprintMode = ModeValue.create(this, "Sprint", (ModeSelection)this.sprintVanilla, this.sprintNone, this.sprintVanilla);
    private final NumberValue groundMotion = NumberValue.create(this, "Ground motion", "#", "%", 0.0, 100.0, 200.0, 1.0);
    private final NumberValue airMotion = NumberValue.create(this, "Air motion", "#", "%", 0.0, 100.0, 200.0, 1.0);
    private final NumberValue speedMotion = NumberValue.create(this, "Speed motion", "#", "%", 0.0, 100.0, 200.0, 1.0);
    private final ModeOption towerNone = new ModeOption("None");
    private final ModeOption towerVanilla = new ModeOption("Vanilla");
    private final ModeOption towerExtra = new ModeOption("Extra");
    private final ModeOption towerTelly = new ModeOption("Telly");
    private final ModeValue towerMode = ModeValue.create(this, "Tower mode", (ModeSelection)this.towerNone, this.towerNone, this.towerVanilla, this.towerExtra, this.towerTelly);
    private final BooleanValue hypixelTower = BooleanValue.create(this, "Hypixel tower", false);
    private final BooleanValue safeTower = BooleanValue.create(this, "Safe tower", false);
    private final NumberValue safeTowerDelay = NumberValue.create(this, "Safe delay ticks", "#", "ticks", 1.0, 1.0, 3.0, 1.0);
    private final ModeOption keepYNone = new ModeOption("None");
    private final ModeOption keepYVanilla = new ModeOption("Vanilla");
    private final ModeOption keepYExtra = new ModeOption("Extra");
    private final ModeOption keepYTelly = new ModeOption("Telly");
    private final ModeOption keepYExtraTelly = new ModeOption("ExtraTelly");
    private final ModeValue keepYMode = ModeValue.create(this, "Keep Y", (ModeSelection)this.keepYTelly, this.keepYNone, this.keepYVanilla, this.keepYExtra, this.keepYTelly, this.keepYExtraTelly);
    private final BooleanValue keepYOnPress = BooleanValue.create(this, "Keep Y on press", false);
    private final BooleanValue disableKeepYOnJumpPotion = BooleanValue.create(this, "Disable Keep Y on jump potion", false);
    private final BooleanValue multiPlace = BooleanValue.create(this, "Multi-place", true, "Places multiple valid blocks in one tick");
    private final BooleanValue safeWalk = BooleanValue.create(this, "Safe walk", true, "Prevents walking off a block edge");
    private final BooleanValue swing = BooleanValue.create(this, "Swing", true, "Off sends an animation packet instead of swinging the hand");
    private final BooleanValue itemSpoof = BooleanValue.create(this, "Item spoof", false);
    private final BooleanValue blockCounter = BooleanValue.create(this, "Block counter", true);
    private final BooleanValue eagle = BooleanValue.create(this, "Eagle", false, "Sneaks automatically at the edge of a block");
    private final NumberValue eagleEdgeDistance = NumberValue.create(this, "Edge distance", "#.#", "blocks", 0.0, 0.13, 0.5, 0.01);
    private final NumberValue eagleSneakDelay = NumberValue.create(this, "Sneak delay", "#", "ms", 0.0, 80.0, 500.0, 1.0);
    private final NumberValue eagleBlocksPerSneak = NumberValue.create(this, "Blocks per sneak", "#", "", 1.0, 1.0, 5.0, 1.0);
    private ArrayList<double[]> bridgePath;
    private KeyBinding jumpKey;
    private boolean bridgingActive = false;
    private double[] verticalTransitionTarget = null;
    private int bridgeLevel = 0;
    private ScaffoldEdgeSneakHelper edgeSneakHelper;
    private boolean resetPending;
    private Scaffold scaffold;
    private final NumberValue activationBlocks = NumberValue.createWithDescription(this, "Activation Blocks", "#", "", 1.0, 2.0, 4.0, "Manual blocks placed before bridging");
    private ItemStack activationItemStack;
    private boolean wasAirborne;
    private TargetPositionMovementTask movementTask = null;
    private KeyBinding sprintKey;
    private KeyBinding useItemKey;
    private int manualBlocksPlaced = 0;
    private int consecutiveHeightIncreases = 0;
    private double[] initialPlacement = null;
    private final NumberValue yIncrease = NumberValue.create((Object)this, "Y increase", "#", "", 0.0, 1.0, 3.0, 1.0);
    private KeyBinding backwardKey;
    private double[] lastAimTarget;
    private boolean manualActivationComplete;
    private int direction = 0;
    private ArrayList<Integer> recentPlacements;

    private boolean evaluateManualActivation(EntityPlayerSP player) {
        double blockX = MathUtil.floor(player.z());
        double blockZ = MathUtil.floor(player.h());
        double placementY = this.scaffold.getPlacementY(player);
        if (!this.scaffold.isValidBlockStack(player.B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt())) {
            this.initialPlacement = null;
            this.manualBlocksPlaced = 0;
            return true;
        }
        int currentDirection = this.scaffold.getCardinalDirection();
        if (this.direction != 0 && currentDirection != this.direction) {
            this.initialPlacement = null;
            this.manualBlocksPlaced = 0;
        }
        this.direction = currentDirection;
        double[] playerBlock = new double[]{blockX, placementY, blockZ};
        double[] oneBlockAhead = this.scaffold.offsetPosition(playerBlock, 1, this.direction);
        double[] twoBlocksAhead = this.scaffold.offsetPosition(playerBlock, 2, this.direction);
        if (this.initialPlacement == null && player.b$src$Z$fqlxe4()) {
            if (this.scaffold.isAirBlockAt(playerBlock)) {
                this.initialPlacement = playerBlock;
            } else if (this.scaffold.isAirBlockAt(oneBlockAhead)) {
                this.initialPlacement = oneBlockAhead;
            } else if (this.scaffold.isAirBlockAt(twoBlocksAhead)) {
                this.initialPlacement = twoBlocksAhead;
            }
            if (this.autoStart && this.initialPlacement != null) {
                this.bridgePath.add(this.initialPlacement);
                double[] movementAnchor = this.scaffold.computeElevatedPlacementPoint(this.scaffold.offsetPosition(this.initialPlacement, -1, this.direction), 0.0, this.direction);
                this.submitMovementTask(this.computeTargetPosition(new double[]{movementAnchor[0], movementAnchor[2]}), false);
                this.activationItemStack = player.B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt();
                this.initialPlacement = null;
                this.manualActivationComplete = true;
                return false;
            }
        } else if (this.initialPlacement != null) {
            if ((double)this.manualBlocksPlaced >= (Double)this.activationBlocks.getValue()) {
                this.bridgePath.add(this.initialPlacement);
                this.manualBlocksPlaced = 0;
                double[] movementAnchor = this.scaffold.computeElevatedPlacementPoint(this.scaffold.offsetPosition(this.initialPlacement, -1, this.direction), 0.0, this.direction);
                this.submitMovementTask(this.computeTargetPosition(new double[]{movementAnchor[0], movementAnchor[2]}), false);
                this.activationItemStack = player.B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt();
                this.initialPlacement = null;
                this.manualActivationComplete = true;
                return false;
            }
            if (!this.scaffold.isAirBlockAt(this.initialPlacement)) {
                ++this.manualBlocksPlaced;
                double[] nextPlacement = this.scaffold.offsetPosition(this.initialPlacement, 1, this.direction);
                boolean nextPlacementIsEmpty = this.scaffold.isAirBlockAt(nextPlacement);
                if (nextPlacementIsEmpty && (double)this.manualBlocksPlaced < (Double)this.activationBlocks.getValue()) {
                    this.initialPlacement = nextPlacement;
                } else if (!nextPlacementIsEmpty) {
                    this.initialPlacement = null;
                    this.manualBlocksPlaced = 0;
                }
            } else if (this.scaffold.hasPlacementDrifted(this.initialPlacement, playerBlock, this.direction, (Double)this.activationBlocks.getValue(), this.manualBlocksPlaced)) {
                this.initialPlacement = null;
                this.manualBlocksPlaced = 0;
            }
        }
        return true;
    }

    @EventHandler
    public void onPostTick(EventPostTick eventPostTick) {
        if (this.resetPending) {
            this.edgeSneakHelper.onPostTick(eventPostTick);
        }
    }

    private void repeatLastPathPosition(int targetSize) {
        double[] lastPosition = this.bridgePath.get(this.bridgePath.size() - 1);
        this.bridgePath.clear();
        while (this.bridgePath.size() != targetSize) {
            this.bridgePath.add(lastPosition);
        }
    }

    private double[] computeNextPlacementPosition() {
        double[] nextPosition;
        int pathSize = this.bridgePath.size();
        if (pathSize == 0) {
            return null;
        }
        if (this.bridgeLevel != 0 && pathSize == 4) {
            nextPosition = this.scaffold.computeElevatedPlacementPoint(this.bridgePath.get(pathSize - 1), 0.2, this.direction);
        } else {
            double horizontalOffset = 0.3;
            double verticalOffset = 0.2;
            nextPosition = this.scaffold.computePlacementAimPoint(this.bridgePath.get(pathSize - 1), horizontalOffset, verticalOffset, this.direction);
        }
        return nextPosition;
    }

    private void updateMovementKeys(EntityPlayerSP player) {
        int pathSize = this.bridgePath.size();
        if (pathSize == 0) {
            return;
        }
        double[] lastPosition = this.bridgePath.get(pathSize - 1);
        if (player.b$src$Z$fqlxe4() && (this.bridgeLevel != 0 || player.B$src$Z$f90iek()) && this.hasReachedPathEdge(player, lastPosition)) {
            MovementInputHelper.setKeyPressed(this.jumpKey, true);
            return;
        }
        if (this.jumpKey.isKeyDown()) {
            MovementInputHelper.setKeyPressed(this.jumpKey, false);
        }
        if (this.shouldSprint() && !this.sprintKey.isKeyDown()) {
            MovementInputHelper.setKeyPressed(this.sprintKey, true);
        }
    }

    private void updateBridge(EntityPlayerSP player) {
        block12: {
            if (Minecraft.currentScreen().getObject() == null) {
                KeyboardCodeUtil.disableLegacyRepeatEvents();
            }
            if (!this.bridgingActive) {
                return;
            }
            if (!player.b$src$Z$fqlxe4()) {
                this.wasAirborne = true;
            }
            this.pruneAndExtendPath();
            if (player.b$src$Z$fqlxe4() && (this.manualActivationComplete || this.wasAirborne)) {
                this.bridgeLevel = this.manualActivationComplete || !this.hasAxisMotion(player) && this.hasPassedPathEdge(player) ? 0 : 1;
                this.manualActivationComplete = false;
                this.lastAimTarget = null;
                this.verticalTransitionTarget = null;
                if (this.consecutiveHeightIncreases >= this.randomHeightIncreaseThreshold() && this.bridgeLevel == 1) {
                    if ((Double)this.yIncrease.getValue() == 0.0 && !this.isAxisMotionBelowThreshold(player)) {
                        this.submitMovementTask(this.computeMovementTarget(false), false);
                        this.repeatLastPathPosition(5);
                        this.wasAirborne = false;
                        break block12;
                    } else {
                        this.bridgeLevel = 0;
                        if (this.verticalTransitionTarget == null) {
                            this.verticalTransitionTarget = this.scaffold.computeElevatedPlacementPoint(this.scaffold.offsetPosition(this.bridgePath.get(this.bridgePath.size() - 1), 1, this.direction), 0.0, this.direction);
                            this.submitMovementTask(this.computeTargetPosition(new double[]{this.verticalTransitionTarget[0], this.verticalTransitionTarget[2]}), false);
                        }
                        this.repeatLastPathPosition(1);
                        this.updateRotationTarget();
                        return;
                    }
                }
                if (this.bridgeLevel == 0) {
                    float[] targetRotation = this.computeBridgeRotation(this.direction);
                    this.scaffold.applyFixedRotation(targetRotation, this.getRotationSpeed(targetRotation, true));
                    if (this.shouldSprint()) {
                        MovementInputHelper.setKeyPressed(this.sprintKey, true);
                    }
                    this.submitMovementTask(this.computeMovementTarget(true), false);
                    this.repeatLastPathPosition(1);
                    this.wasAirborne = false;
                    this.consecutiveHeightIncreases = 0;
                } else {
                    this.submitMovementTask(this.computeMovementTarget(false), false);
                    this.repeatLastPathPosition(4);
                    ++this.consecutiveHeightIncreases;
                    this.wasAirborne = false;
                }
            }
        }
        if (!player.b$src$Z$fqlxe4()) {
            this.updateRotationTarget();
        }
        this.updateMovementKeys(player);
    }

    @Override
    public void onEnable() {
        this.resetPending = true;
    }

    private boolean isGroundPositionClear(EntityPlayerSP player) {
        double sampleX = player.z();
        double sampleZ = player.h();
        if (this.direction == 6) {
            sampleX += 0.15;
        } else if (this.direction == 8) {
            sampleX -= 0.15;
        } else if (this.direction == 7) {
            sampleZ += 0.15;
        } else if (this.direction == 5) {
            sampleZ -= 0.15;
        } else {
            return true;
        }
        sampleX = MathUtil.floor(sampleX);
        double placementY = this.scaffold.getPlacementY(player);
        sampleZ = MathUtil.floor(sampleZ);
        return !this.scaffold.isAirBlockAt(sampleX, placementY, sampleZ) && player.b$src$Z$fqlxe4();
    }

    private boolean hasReachedPathEdge(EntityPlayerSP player, double[] pathPosition) {
        double targetX = pathPosition[0];
        double targetZ = pathPosition[2];
        double edgeOffset = 0.8;
        if (this.direction == 6) {
            double distance = player.z() - (targetX += edgeOffset);
            return distance >= -0.05;
        }
        if (this.direction == 8) {
            double distance = player.z() - (targetX -= 1.0 - edgeOffset);
            return distance <= 0.05;
        }
        if (this.direction == 7) {
            double distance = player.h() - (targetZ += edgeOffset);
            return distance >= -0.05;
        }
        if (this.direction == 5) {
            double distance = player.h() - (targetZ -= 1.0 - edgeOffset);
            return distance <= 0.05;
        }
        return false;
    }


    public TellyBridgeScaffoldMode(Mod parent, String name) {
        this(parent, name, false);
    }

    public TellyBridgeScaffoldMode(Mod parent, String name, boolean autoStart) {
        super(parent, name);
        this.autoStart = autoStart;
        this.requireRightClick = BooleanValue.create(this, "Require right click", !autoStart, "On: Holding right click, and backwards move key, continues scaffold. Releasing either deactivates\nOff: Holding backwards move key continues scaffold, releasing deactivates\n");
        this.scaffold = (Scaffold)this.getParent();
        this.edgeSneakHelper = new ScaffoldEdgeSneakHelper((Mod)this.getParent(), "legit");
        this.bridgePath = new ArrayList<>();
        GameSettings settings = Minecraft.gameSettings();
        this.useItemKey = settings.b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
        this.backwardKey = settings.s();
        this.sprintKey = settings.r();
        this.jumpKey = settings.O();
        this.recentPlacements = new ArrayList<>();
        if (autoStart) {
            this.tellyRotations.addDependentValues(this.tellyStartRotationMin, this.tellyStartRotationMax, this.tellyNormalRotationMin, this.tellyNormalRotationMax);
            this.eagle.addDependentValues(this.eagleEdgeDistance, this.eagleSneakDelay, this.eagleBlocksPerSneak);
            this.towerMode.addActiveMode(this.hypixelTower, this.towerTelly);
            this.towerMode.addActiveMode(this.safeTower, this.towerTelly);
            this.safeTower.addDependentValues(this.safeTowerDelay);
            this.addValue(this.openMyauRotationMode, this.tellyRotations, this.tellyStartRotationMin, this.tellyStartRotationMax, this.tellyNormalRotationMin, this.tellyNormalRotationMax, this.moveFixMode, this.sprintMode, this.groundMotion, this.airMotion, this.speedMotion, this.towerMode, this.hypixelTower, this.safeTower, this.safeTowerDelay, this.keepYMode, this.keepYOnPress, this.disableKeepYOnJumpPotion, this.multiPlace, this.safeWalk, this.swing, this.itemSpoof, this.blockCounter, this.eagle, this.eagleEdgeDistance, this.eagleSneakDelay, this.eagleBlocksPerSneak, this.yIncrease);
        } else {
            this.addValue(this.requireRightClick, this.activationBlocks, this.yIncrease);
        }
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        if (Minecraft.thePlayer().isNull() || Minecraft.theWorld().isNull()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (this.bridgingActive) {
            SharedModuleControlClaims.movementInput.lock();
            SharedModuleControlClaims.rightClickUse.blockUse();
        } else {
            SharedModuleControlClaims.movementInput.unlock();
            SharedModuleControlClaims.rightClickUse.clearClaimed();
        }
        if (!this.resetPending && this.bridgingActive && this.isLookingAtPlacement()) {
            this.useItemKey.onTick(1);
        }
        if (this.resetPending) {
            if (this.bridgingActive) {
                this.edgeSneakHelper.onEnable();
                this.scaffold.releaseControls();
            }
            this.resetBridgeState();
            this.resetPending = this.evaluateManualActivation(player);
            return;
        }
        if (this.shouldReset(player)) {
            this.edgeSneakHelper.onEnable();
            return;
        }
        if (!this.bridgingActive && this.movementTask != null && this.movementTask.isCompleted()) {
            this.bridgingActive = true;
        }
        this.updateBridge(player);
    }

    private boolean shouldReset(EntityPlayerSP player) {
        if (!SharedModuleControlClaims.rotation.isOwnedBy(this.scaffold)) {
            this.resetPending = true;
            MovementInputHelper.restorePhysicalInput();
            return true;
        }
        if (!this.ensureBlockSlot(player)) {
            this.resetPending = true;
            MovementInputHelper.releaseAllInput();
            return true;
        }
        boolean backwardPressed = this.autoStart || ClientSettings.isPhysicalKeyDown(this.backwardKey);
        boolean activationPressed = this.autoStart || this.requireRightClick.getEffectiveValue() == false || KeyBindingInputState.isRightButtonDown();
        if (!backwardPressed || !activationPressed) {
            if (this.bridgingActive) {
                if (!player.C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().isFlying() && !this.isGroundPositionClear(player)) {
                    return false;
                }
                MovementInputHelper.releaseAllInput();
            }
            this.resetPending = true;
            return true;
        }
        this.recentPlacements = this.scaffold.updatePlacementHistory(this.recentPlacements);
        if (this.bridgingActive && this.scaffold.sumPlacementHistory(this.recentPlacements) >= 10) {
            MovementInputHelper.releaseAllInput();
            this.resetPending = true;
            return true;
        }
        return false;
    }

    private void updateRotationTarget() {
        double[] aimTarget = this.computeNextPlacementPosition();
        if (this.lastAimTarget == null || MathUtil.floor(this.lastAimTarget[0]) != MathUtil.floor(aimTarget[0]) || MathUtil.floor(this.lastAimTarget[1]) != MathUtil.floor(aimTarget[1]) || MathUtil.floor(this.lastAimTarget[2]) != MathUtil.floor(aimTarget[2])) {
            this.lastAimTarget = aimTarget;
            double[] placementPosition = this.bridgePath.get(this.bridgePath.size() - 1);
            float rotationSpeed = this.getRotationSpeed(null, false);
            this.scaffold.applyPointRotation(this.lastAimTarget, rotationSpeed, this.direction, placementPosition);
        }
    }

    private float getRotationSpeed(float[] targetRotation, boolean start) {
        if (!this.autoStart || !this.tellyRotations.getEffectiveValue()) {
            return targetRotation == null ? this.scaffold.getDirectionRotationSpeed(this.direction) : this.scaffold.computeRotationSpeed(targetRotation);
        }
        double minimum = start ? (Double)this.tellyStartRotationMin.getValue() : (Double)this.tellyNormalRotationMin.getValue();
        double maximum = start ? (Double)this.tellyStartRotationMax.getValue() : (Double)this.tellyNormalRotationMax.getValue();
        return (float)Math.min(12.0, Math.max(2.0, (minimum + Math.random() * Math.max(0.0, maximum - minimum)) / 10.0));
    }

    private boolean shouldSprint() {
        return !this.autoStart || this.sprintVanilla.isSelected();
    }

    private int randomHeightIncreaseThreshold() {
        double random = Math.random();
        int configuredIncrease = ((Double)this.yIncrease.getValue()).intValue();
        if (configuredIncrease != 0) {
            int lowerValue = configuredIncrease - 1;
            int upperValue = configuredIncrease + 1;
            if (random < 0.15) {
                return upperValue;
            }
            if (random < 0.25) {
                return lowerValue;
            }
            return configuredIncrease;
        }
        return 0;
    }

    private float[] computeBridgeRotation(int direction) {
        double yaw = RotationUtil.c();
        double pitch = 90.0;
        if (direction == 6) {
            yaw = 230.0;
        } else if (direction == 8) {
            yaw = 50.0;
        } else if (direction == 7) {
            yaw = 320.0;
        } else if (direction == 5) {
            yaw = 140.0;
        }
        yaw += Math.random() < 0.5 ? Math.random() * -4.0 : Math.random() * 4.0;
        pitch += Math.random() * -5.0;
        return new float[]{(float)yaw, (float)pitch};
    }

    private boolean isLookingAtPlacement() {
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (rayTraceResult.isNull() || !rayTraceResult.getTypeOfHit().equals(RayTraceResult_type.block()) || this.bridgePath.size() < 1) {
            return false;
        }
        double[] placementPosition = this.bridgePath.get(this.bridgePath.size() - 1);
        double[] hitPosition = new double[]{MathUtil.floor((double)rayTraceResult.g()), MathUtil.floor((double)rayTraceResult.T()), MathUtil.floor((double)rayTraceResult.a$src$I$8nuo9d())};
        boolean positionsMatch = placementPosition[0] == hitPosition[0] && placementPosition[1] == hitPosition[1] && placementPosition[2] == hitPosition[2];
        int placementAttempts = rayTraceResult.Z();
        boolean enoughAttempts = this.bridgeLevel != 0 && this.bridgePath.size() == 4 ? placementAttempts == 1 : placementAttempts > 1;
        return positionsMatch && enoughAttempts;
    }

    private void pruneAndExtendPath() {
        int index;
        for (index = this.bridgePath.size() - 1; index >= Math.max(0, this.bridgePath.size() - 3) && this.scaffold.isAirBlockAt(this.bridgePath.get(index)); --index) {
            this.lastAimTarget = null;
            this.bridgePath.remove(index);
        }
        int pathSize = this.bridgePath.size();
        if (pathSize == 0) {
            return;
        }
        double[] nextPosition = this.advancePathPosition(this.bridgePath.get(pathSize - 1), pathSize);
        if (!this.scaffold.isAirBlockAt(nextPosition[0], nextPosition[1], nextPosition[2])) {
            if (pathSize == 6) {
                this.bridgePath.clear();
            }
            this.bridgePath.add(new double[]{nextPosition[0], nextPosition[1], nextPosition[2]});
            this.scaffold.releaseRotation();
        }
    }

    @Override
    public void onDisable() {
        this.scaffold.releaseControls();
        this.resetBridgeState();
    }

    @EventHandler
    public void onPreEntityUpdate(EventPreEntityUpdate eventPreEntityUpdate) {
        if (this.resetPending) {
            this.edgeSneakHelper.onPreEntityUpdate(eventPreEntityUpdate);
        }
    }

    private double[] computeMovementTarget(boolean initialMove) {
        double[] offsetPosition = initialMove ? this.scaffold.offsetPosition(this.bridgePath.get(this.bridgePath.size() - 1), this.manualActivationComplete ? 4 : 3, this.direction) : this.scaffold.offsetPosition(this.bridgePath.get(this.bridgePath.size() - 1), 2, this.direction);
        return this.computeTargetPosition(new double[]{offsetPosition[0], offsetPosition[2]});
    }

    private void resetBridgeState() {
        this.resetPending = true;
        this.consecutiveHeightIncreases = 0;
        this.movementTask = null;
        this.bridgingActive = false;
        this.direction = 0;
        this.bridgePath.clear();
        this.activationItemStack = null;
    }

    private void submitMovementTask(double[] targetPosition, boolean restoreInput) {
        this.movementTask = new TargetPositionMovementTask(targetPosition[0], targetPosition[1]);
        this.movementTask.setRestoreInputOnCompletion(restoreInput);
        this.movementTask.setWaitForGroundAfterArrival(true);
        PlayerMovementTaskManager.INSTANCE.submit(this.movementTask);
    }

    private boolean isAxisMotionBelowThreshold(EntityPlayerSP player) {
        if (this.direction % 2 == 0) {
            return Math.abs(player.t()) < 0.6;
        }
        return Math.abs(player.T()) < 0.6;
    }

    private boolean hasAxisMotion(EntityPlayerSP player) {
        if (this.direction % 2 == 0) {
            return Math.abs(player.t()) >= 0.1;
        }
        return Math.abs(player.T()) >= 0.1;
    }

    private boolean ensureBlockSlot(EntityPlayerSP player) {
        int blockSlot = this.scaffold.findBlockHotbarSlot();
        if (blockSlot == -1 || this.scaffold.countBlocks(1) < 5) {
            return false;
        }
        if (this.activationItemStack != null && !player.B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt().equals(this.activationItemStack)) {
            int originalItemSlot = this.scaffold.findMatchingHotbarSlot(player, this.activationItemStack);
            if (originalItemSlot != -1) {
                this.scaffold.selectHotbarSlot(originalItemSlot);
            } else if (player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v() != blockSlot) {
                this.scaffold.selectHotbarSlot(blockSlot);
            }
        }
        return true;
    }

    private boolean hasPassedPathEdge(EntityPlayerSP player) {
        double targetX = this.bridgePath.get(this.bridgePath.size() - 1)[0];
        double targetZ = this.bridgePath.get(this.bridgePath.size() - 1)[2];
        double fullBlockOffset = 1.0;
        double threshold = 0.1;
        if (this.direction == 6) {
            double distance = player.z() - (targetX += fullBlockOffset);
            return distance < -threshold && Math.abs(player.h() - (targetZ += 0.6)) <= 0.15;
        }
        if (this.direction == 8) {
            double distance = player.z() - (targetX -= 1.0 - fullBlockOffset);
            return distance > threshold && Math.abs(player.h() - (targetZ += 0.4)) <= 0.15;
        }
        if (this.direction == 7) {
            double distance = player.h() - (targetZ += fullBlockOffset);
            return distance < -threshold && Math.abs(player.z() - (targetX += 0.4)) <= 0.15;
        }
        if (this.direction == 5) {
            double distance = player.h() - (targetZ -= 1.0 - fullBlockOffset);
            return distance > threshold && Math.abs(player.z() - (targetX += 0.6)) <= 0.15;
        }
        return false;
    }

    private double[] advancePathPosition(double[] position, int pathSize) {
        double x = position[0];
        double y = position[1];
        double z = position[2];
        if (this.bridgeLevel != 0 && pathSize == 4) {
            y += 1.0;
        } else if (this.direction == 6) {
            x += 1.0;
        } else if (this.direction == 8) {
            x -= 1.0;
        } else if (this.direction == 7) {
            z += 1.0;
        } else if (this.direction == 5) {
            z -= 1.0;
        }
        return new double[]{x, y, z};
    }

    private double[] computeTargetPosition(double[] horizontalPosition) {
        double x = horizontalPosition[0];
        double z = horizontalPosition[1];
        if (this.direction == 6) {
            x = new BigDecimal(String.valueOf((double)MathUtil.floor(x) + 0.3)).doubleValue();
            z = new BigDecimal(String.valueOf((double)MathUtil.floor(z) + 0.6)).doubleValue();
        } else if (this.direction == 8) {
            x = new BigDecimal(String.valueOf((double)MathUtil.floor(x) + 0.7)).doubleValue();
            z = new BigDecimal(String.valueOf((double)MathUtil.floor(z) + 0.4)).doubleValue();
        } else if (this.direction == 7) {
            x = new BigDecimal(String.valueOf((double)MathUtil.floor(x) + 0.4)).doubleValue();
            z = new BigDecimal(String.valueOf((double)MathUtil.floor(z) + 0.3)).doubleValue();
        } else if (this.direction == 5) {
            x = new BigDecimal(String.valueOf((double)MathUtil.floor(x) + 0.6)).doubleValue();
            z = new BigDecimal(String.valueOf((double)MathUtil.floor(z) + 0.7)).doubleValue();
        }
        return new double[]{x, z};
    }
}
