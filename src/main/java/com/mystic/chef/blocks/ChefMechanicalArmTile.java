package com.mystic.chef.blocks;

import com.mystic.chef.init.TileInit;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.base.KineticTileEntity;
import com.simibubi.create.content.contraptions.components.structureMovement.ITransformableTE;
import com.simibubi.create.content.contraptions.components.structureMovement.StructureTransform;
import com.simibubi.create.content.logistics.block.mechanicalArm.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.config.AllConfigs;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.tileEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.tileEntity.behaviour.scrollvalue.INamedIconOptions;
import com.simibubi.create.foundation.tileEntity.behaviour.scrollvalue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.NBTHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ChefMechanicalArmTile extends KineticTileEntity implements ITransformableTE {

        // Server
        public List<ChefArmInteractionPoint> inputs;
        public List<ChefArmInteractionPoint> outputs;
        public ListTag interactionPointTag;

        // Both
        float chasedPointProgress;
        int chasedPointIndex;
        ItemStack heldItem;
        Phase phase;

        // Client
        ChefArmAngleTarget previousTarget;
        LerpedFloat lowerArmAngle;
        LerpedFloat upperArmAngle;
        LerpedFloat baseAngle;
        LerpedFloat headAngle;
        LerpedFloat clawAngle;
        float previousBaseAngle;
        boolean updateInteractionPoints;

//
protected ScrollOptionBehaviour<SelectionMode> selectionMode;
protected int lastInputIndex=-1;
protected int lastOutputIndex=-1;
protected boolean redstoneLocked;

public enum Phase {
    SEARCH_INPUTS, MOVE_TO_INPUT, SEARCH_OUTPUTS, MOVE_TO_OUTPUT, DANCING
}

    public ChefMechanicalArmTile(BlockPos pos, BlockState state) {
        super(TileInit.CHEF_MECHANICAL_ARM.get(), pos, state);
        inputs = new ArrayList<>();
        outputs = new ArrayList<>();
        interactionPointTag = new ListTag();
        heldItem = ItemStack.EMPTY;
        phase = ChefMechanicalArmTile.Phase.SEARCH_INPUTS;
        previousTarget = ChefArmAngleTarget.NO_TARGET;
        baseAngle = LerpedFloat.angular();
        baseAngle.startWithValue(previousTarget.baseAngle);
        lowerArmAngle = LerpedFloat.angular();
        lowerArmAngle.startWithValue(previousTarget.lowerArmAngle);
        upperArmAngle = LerpedFloat.angular();
        upperArmAngle.startWithValue(previousTarget.upperArmAngle);
        headAngle = LerpedFloat.angular();
        headAngle.startWithValue(previousTarget.headAngle);
        clawAngle = LerpedFloat.angular();
        previousBaseAngle = previousTarget.baseAngle;
        updateInteractionPoints = true;
        redstoneLocked = false;
    }

    @Override
    public void addBehaviours(List<TileEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        selectionMode = new ScrollOptionBehaviour<ChefMechanicalArmTile.SelectionMode>(ChefMechanicalArmTile.SelectionMode.class,
                Lang.translateDirect("logistics.when_multiple_outputs_available"), this, new ChefMechanicalArmTile.SelectionModeValueBox());
        selectionMode.requiresWrench();
        behaviours.add(selectionMode);

        registerAwardables(behaviours, AllAdvancements.ARM_BLAZE_BURNER, AllAdvancements.ARM_MANY_TARGETS,
                AllAdvancements.MECHANICAL_ARM, AllAdvancements.MUSICAL_ARM);
    }

    @Override
    public void tick() {
        super.tick();
        initInteractionPoints();
        boolean targetReached = tickMovementProgress();

        if (chasedPointProgress < 1) {
            if (phase == ChefMechanicalArmTile.Phase.MOVE_TO_INPUT) {
                ChefArmInteractionPoint point = getTargetedInteractionPoint();
                if (point != null)
                    point.keepAlive();
            }
            return;
        }
        if (level.isClientSide)
            return;

        if (phase == ChefMechanicalArmTile.Phase.MOVE_TO_INPUT)
            collectItem();
        else if (phase == ChefMechanicalArmTile.Phase.MOVE_TO_OUTPUT)
            depositItem();
        else if (phase == ChefMechanicalArmTile.Phase.SEARCH_INPUTS || phase == ChefMechanicalArmTile.Phase.DANCING)
            searchForItem();

        if (targetReached)
            lazyTick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();

        if (level.isClientSide)
            return;
        if (chasedPointProgress < .5f)
            return;
        if (phase == ChefMechanicalArmTile.Phase.SEARCH_INPUTS || phase == ChefMechanicalArmTile.Phase.DANCING)
            checkForMusic();
        if (phase == ChefMechanicalArmTile.Phase.SEARCH_OUTPUTS)
            searchForDestination();
    }

    private void checkForMusic() {
        boolean hasMusic = checkForMusicAmong(inputs) || checkForMusicAmong(outputs);
        if (hasMusic != (phase == ChefMechanicalArmTile.Phase.DANCING)) {
            phase = hasMusic ? ChefMechanicalArmTile.Phase.DANCING : ChefMechanicalArmTile.Phase.SEARCH_INPUTS;
            setChanged();
            sendData();
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(3);
    }

    private boolean checkForMusicAmong(List<ChefArmInteractionPoint> list) {
        for (ChefArmInteractionPoint armInteractionPoint : list) {
            if (!(armInteractionPoint instanceof ChefAllArmInteractionPointTypes.JukeboxPoint))
                continue;
            BlockState state = level.getBlockState(armInteractionPoint.getPos());
            if (state.getOptionalValue(JukeboxBlock.HAS_RECORD)
                    .orElse(false))
                return true;
        }
        return false;
    }

    private boolean tickMovementProgress() {
        boolean targetReachedPreviously = chasedPointProgress >= 1;
        chasedPointProgress += Math.min(256, Math.abs(getSpeed())) / 1024f;
        if (chasedPointProgress > 1)
            chasedPointProgress = 1;
        if (!level.isClientSide)
            return !targetReachedPreviously && chasedPointProgress >= 1;

        ChefArmInteractionPoint targetedInteractionPoint = getTargetedInteractionPoint();
        ChefArmAngleTarget previousTarget = this.previousTarget;
        ChefArmAngleTarget target = targetedInteractionPoint == null ? ChefArmAngleTarget.NO_TARGET
                : targetedInteractionPoint.getTargetAngles(worldPosition, isOnCeiling());

        baseAngle.setValue(AngleHelper.angleLerp(chasedPointProgress, previousBaseAngle,
                target == ChefArmAngleTarget.NO_TARGET ? previousBaseAngle : target.baseAngle));

        // Arm's angles first backup to resting position and then continue
        if (chasedPointProgress < .5f)
            target = ChefArmAngleTarget.NO_TARGET;
        else
            previousTarget = ChefArmAngleTarget.NO_TARGET;
        float progress = chasedPointProgress == 1 ? 1 : (chasedPointProgress % .5f) * 2;

        lowerArmAngle.setValue(Mth.lerp(progress, previousTarget.lowerArmAngle, target.lowerArmAngle));
        upperArmAngle.setValue(Mth.lerp(progress, previousTarget.upperArmAngle, target.upperArmAngle));
        headAngle.setValue(AngleHelper.angleLerp(progress, previousTarget.headAngle % 360, target.headAngle % 360));

        return false;
    }

    protected boolean isOnCeiling() {
        BlockState state = getBlockState();
        return hasLevel() && state.getOptionalValue(ArmBlock.CEILING)
                .orElse(false);
    }

    @Nullable
    private ChefArmInteractionPoint getTargetedInteractionPoint() {
        if (chasedPointIndex == -1)
            return null;
        if (phase == ChefMechanicalArmTile.Phase.MOVE_TO_INPUT && chasedPointIndex < inputs.size())
            return inputs.get(chasedPointIndex);
        if (phase == ChefMechanicalArmTile.Phase.MOVE_TO_OUTPUT && chasedPointIndex < outputs.size())
            return outputs.get(chasedPointIndex);
        return null;
    }

    protected void searchForItem() {
        if (redstoneLocked)
            return;

        boolean foundInput = false;
        // for round robin, we start looking after the last used index, for default we
        // start at 0;
        int startIndex = selectionMode.get() == ChefMechanicalArmTile.SelectionMode.PREFER_FIRST ? 0 : lastInputIndex + 1;

        // if we enforce round robin, only look at the next input in the list,
        // otherwise, look at all inputs
        int scanRange = selectionMode.get() == ChefMechanicalArmTile.SelectionMode.FORCED_ROUND_ROBIN ? lastInputIndex + 2 : inputs.size();
        if (scanRange > inputs.size())
            scanRange = inputs.size();

        InteractionPoints:
        for (int i = startIndex; i < scanRange; i++) {
            ChefArmInteractionPoint armInteractionPoint = inputs.get(i);
            if (!armInteractionPoint.isValid())
                continue;
            for (int j = 0; j < armInteractionPoint.getSlotCount(); j++) {
                if (getDistributableAmount(armInteractionPoint, j) == 0)
                    continue;

                selectIndex(true, i);
                foundInput = true;
                break InteractionPoints;
            }
        }
        if (!foundInput && selectionMode.get() == ChefMechanicalArmTile.SelectionMode.ROUND_ROBIN) {
            // if we didn't find an input, but don't want to enforce round robin, reset the
            // last index
            lastInputIndex = -1;
        }
        if (lastInputIndex == inputs.size() - 1) {
            // if we reached the last input in the list, reset the last index
            lastInputIndex = -1;
        }
    }

    protected void searchForDestination() {
        ItemStack held = heldItem.copy();

        boolean foundOutput = false;
        // for round robin, we start looking after the last used index, for default we
        // start at 0;
        int startIndex = selectionMode.get() == ChefMechanicalArmTile.SelectionMode.PREFER_FIRST ? 0 : lastOutputIndex + 1;

        // if we enforce round robin, only look at the next index in the list,
        // otherwise, look at all
        int scanRange = selectionMode.get() == ChefMechanicalArmTile.SelectionMode.FORCED_ROUND_ROBIN ? lastOutputIndex + 2 : outputs.size();
        if (scanRange > outputs.size())
            scanRange = outputs.size();

        for (int i = startIndex; i < scanRange; i++) {
            ChefArmInteractionPoint armInteractionPoint = outputs.get(i);
            if (!armInteractionPoint.isValid())
                continue;

            ItemStack remainder = armInteractionPoint.insert(held, true);
            if (remainder.equals(heldItem, false))
                continue;

            selectIndex(false, i);
            foundOutput = true;
            break;
        }

        if (!foundOutput && selectionMode.get() == ChefMechanicalArmTile.SelectionMode.ROUND_ROBIN) {
            // if we didn't find an input, but don't want to enforce round robin, reset the
            // last index
            lastOutputIndex = -1;
        }
        if (lastOutputIndex == outputs.size() - 1) {
            // if we reached the last input in the list, reset the last index
            lastOutputIndex = -1;
        }
    }

    // input == true => select input, false => select output
    private void selectIndex(boolean input, int index) {
        phase = input ? ChefMechanicalArmTile.Phase.MOVE_TO_INPUT : ChefMechanicalArmTile.Phase.MOVE_TO_OUTPUT;
        chasedPointIndex = index;
        chasedPointProgress = 0;
        if (input)
            lastInputIndex = index;
        else
            lastOutputIndex = index;
        sendData();
        setChanged();
    }

    protected int getDistributableAmount(ChefArmInteractionPoint armInteractionPoint, int i) {
        ItemStack stack = armInteractionPoint.extract(i, true);
        ItemStack remainder = simulateInsertion(stack);
        if (stack.sameItem(remainder)) {
            return stack.getCount() - remainder.getCount();
        } else {
            return stack.getCount();
        }
    }

    private ItemStack simulateInsertion(ItemStack stack) {
        for (ChefArmInteractionPoint armInteractionPoint : outputs) {
            if (armInteractionPoint.isValid())
                stack = armInteractionPoint.insert(stack, true);
            if (stack.isEmpty())
                break;
        }
        return stack;
    }

    protected void depositItem() {
        ChefArmInteractionPoint armInteractionPoint = getTargetedInteractionPoint();
        if (armInteractionPoint != null && armInteractionPoint.isValid()) {
            ItemStack toInsert = heldItem.copy();
            ItemStack remainder = armInteractionPoint.insert(toInsert, false);
            heldItem = remainder;

            if (armInteractionPoint instanceof ChefAllArmInteractionPointTypes.JukeboxPoint && remainder.isEmpty())
                award(AllAdvancements.MUSICAL_ARM);
        }

        phase = heldItem.isEmpty() ? ChefMechanicalArmTile.Phase.SEARCH_INPUTS : ChefMechanicalArmTile.Phase.SEARCH_OUTPUTS;
        chasedPointProgress = 0;
        chasedPointIndex = -1;
        sendData();
        setChanged();

        if (!level.isClientSide)
            award(AllAdvancements.MECHANICAL_ARM);
    }

    protected void collectItem() {
        ChefArmInteractionPoint armInteractionPoint = getTargetedInteractionPoint();
        if (armInteractionPoint != null && armInteractionPoint.isValid())
            for (int i = 0; i < armInteractionPoint.getSlotCount(); i++) {
                int amountExtracted = getDistributableAmount(armInteractionPoint, i);
                if (amountExtracted == 0)
                    continue;

                ItemStack prevHeld = heldItem;
                heldItem = armInteractionPoint.extract(i, amountExtracted, false);
                phase = ChefMechanicalArmTile.Phase.SEARCH_OUTPUTS;
                chasedPointProgress = 0;
                chasedPointIndex = -1;
                sendData();
                setChanged();

                if (!prevHeld.sameItem(heldItem))
                    level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .125f,
                            .5f + Create.RANDOM.nextFloat() * .25f);
                return;
            }

        phase = ChefMechanicalArmTile.Phase.SEARCH_INPUTS;
        chasedPointProgress = 0;
        chasedPointIndex = -1;
        sendData();
        setChanged();
    }

    public void redstoneUpdate() {
        if (level.isClientSide)
            return;
        boolean blockPowered = level.hasNeighborSignal(worldPosition);
        if (blockPowered == redstoneLocked)
            return;
        redstoneLocked = blockPowered;
        sendData();
        if (!redstoneLocked)
            searchForItem();
    }

    @Override
    public void transform(StructureTransform transform) {
        if (interactionPointTag == null)
            return;

        for (Tag tag : interactionPointTag) {
            ChefArmInteractionPoint.transformPos((CompoundTag) tag, transform);
        }

        notifyUpdate();
    }

    protected void initInteractionPoints() {
        if (!updateInteractionPoints || interactionPointTag == null)
            return;
        if (!level.isAreaLoaded(worldPosition, getRange() + 1))
            return;
        inputs.clear();
        outputs.clear();

        boolean hasBlazeBurner = false;
        for (Tag tag : interactionPointTag) {
            ChefArmInteractionPoint point = ChefArmInteractionPoint.deserialize((CompoundTag) tag, level, worldPosition);
            if (point == null)
                continue;
            if (point.getMode() == ChefArmInteractionPoint.Mode.DEPOSIT)
                outputs.add(point);
            else if (point.getMode() == ChefArmInteractionPoint.Mode.TAKE)
                inputs.add(point);
            hasBlazeBurner |= point instanceof ChefAllArmInteractionPointTypes.BlazeBurnerPoint;
        }

        if (!level.isClientSide) {
            if (outputs.size() >= 10)
                award(AllAdvancements.ARM_MANY_TARGETS);
            if (hasBlazeBurner)
                award(AllAdvancements.ARM_BLAZE_BURNER);
        }

        updateInteractionPoints = false;
        sendData();
        setChanged();
    }

    public void writeInteractionPoints(CompoundTag compound) {
        if (updateInteractionPoints) {
            compound.put("InteractionPoints", interactionPointTag);
        } else {
            ListTag pointsNBT = new ListTag();
            inputs.stream()
                    .map(aip -> aip.serialize(worldPosition))
                    .forEach(pointsNBT::add);
            outputs.stream()
                    .map(aip -> aip.serialize(worldPosition))
                    .forEach(pointsNBT::add);
            compound.put("InteractionPoints", pointsNBT);
        }
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        writeInteractionPoints(compound);

        NBTHelper.writeEnum(compound, "Phase", phase);
        compound.putBoolean("Powered", redstoneLocked);
        compound.put("HeldItem", heldItem.serializeNBT());
        compound.putInt("TargetPointIndex", chasedPointIndex);
        compound.putFloat("MovementProgress", chasedPointProgress);
    }

    @Override
    public void writeSafe(CompoundTag compound, boolean clientPacket) {
        super.writeSafe(compound, clientPacket);

        writeInteractionPoints(compound);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        int previousIndex = chasedPointIndex;
        ChefMechanicalArmTile.Phase previousPhase = phase;
        ListTag interactionPointTagBefore = interactionPointTag;

        super.read(compound, clientPacket);
        heldItem = ItemStack.of(compound.getCompound("HeldItem"));
        phase = NBTHelper.readEnum(compound, "Phase", ChefMechanicalArmTile.Phase.class);
        chasedPointIndex = compound.getInt("TargetPointIndex");
        chasedPointProgress = compound.getFloat("MovementProgress");
        interactionPointTag = compound.getList("InteractionPoints", Tag.TAG_COMPOUND);
        redstoneLocked = compound.getBoolean("Powered");

        if (!clientPacket)
            return;

        boolean ceiling = isOnCeiling();
        if (interactionPointTagBefore == null || interactionPointTagBefore.size() != interactionPointTag.size())
            updateInteractionPoints = true;
        if (previousIndex != chasedPointIndex || (previousPhase != phase)) {
            ChefArmInteractionPoint previousPoint = null;
            if (previousPhase == ChefMechanicalArmTile.Phase.MOVE_TO_INPUT && previousIndex < inputs.size())
                previousPoint = inputs.get(previousIndex);
            if (previousPhase == ChefMechanicalArmTile.Phase.MOVE_TO_OUTPUT && previousIndex < outputs.size())
                previousPoint = outputs.get(previousIndex);
            previousTarget = previousPoint == null ? ChefArmAngleTarget.NO_TARGET
                    : previousPoint.getTargetAngles(worldPosition, ceiling);
            if (previousPoint != null)
                previousBaseAngle = previousPoint.getTargetAngles(worldPosition, ceiling).baseAngle;

            ChefArmInteractionPoint targetedPoint = getTargetedInteractionPoint();
            if (targetedPoint != null)
                targetedPoint.updateCachedState();
        }
    }

    public static int getRange() {
        return AllConfigs.SERVER.logistics.mechanicalArmRange.get();
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (super.addToTooltip(tooltip, isPlayerSneaking))
            return true;
        if (isPlayerSneaking)
            return false;
        if (!inputs.isEmpty())
            return false;
        if (!outputs.isEmpty())
            return false;

        TooltipHelper.addHint(tooltip, "hint.mechanical_arm_no_targets");
        return true;
    }

    public void setLevel(Level level) {
        super.setLevel(level);
        for (ChefArmInteractionPoint input : inputs) {
            input.setLevel(level);
        }
        for (ChefArmInteractionPoint output : outputs) {
            output.setLevel(level);
        }
    }

private class SelectionModeValueBox extends CenteredSideValueBoxTransform {

    public SelectionModeValueBox() {
        super((blockState, direction) -> !direction.getAxis()
                .isVertical());
    }

    @Override
    protected Vec3 getLocalOffset(BlockState state) {
        int yPos = state.getValue(ArmBlock.CEILING) ? 16 - 3 : 3;
        Vec3 location = VecHelper.voxelSpace(8, yPos, 15.95);
        location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(getSide()), Direction.Axis.Y);
        return location;
    }

    @Override
    protected float getScale() {
        return .3f;
    }

}

public enum SelectionMode implements INamedIconOptions {
    ROUND_ROBIN(AllIcons.I_ARM_ROUND_ROBIN),
    FORCED_ROUND_ROBIN(AllIcons.I_ARM_FORCED_ROUND_ROBIN),
    PREFER_FIRST(AllIcons.I_ARM_PREFER_FIRST),

    ;

    private final String translationKey;
    private final AllIcons icon;

    SelectionMode(AllIcons icon) {
        this.icon = icon;
        this.translationKey = "mechanical_arm.selection_mode." + Lang.asId(name());
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}
}