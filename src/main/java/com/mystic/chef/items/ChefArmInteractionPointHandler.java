package com.mystic.chef.items;

import com.mystic.chef.blocks.ChefArmInteractionPoint;
import com.mystic.chef.blocks.ChefMechanicalArmTile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT)
public class ChefArmInteractionPointHandler {

	static List<ChefArmInteractionPoint> currentSelection = new ArrayList<>();
	static ItemStack currentItem;

	static long lastBlockPos = -1;

	@SubscribeEvent
	public static void rightClickingBlocksSelectsThem(PlayerInteractEvent.RightClickBlock event) {
		if (currentItem == null)
			return;
		BlockPos pos = event.getPos();
		Level world = event.getWorld();
		if (!world.isClientSide)
			return;
		Player player = event.getPlayer();
		if (player != null && player.isSpectator())
			return;

		ChefArmInteractionPoint selected = getSelected(pos);
		BlockState state = world.getBlockState(pos);

		if (selected == null) {
			ChefArmInteractionPoint point = ChefArmInteractionPoint.create(world, pos, state);
			if (point == null)
				return;
			selected = point;
			put(point);
		}

		selected.cycleMode();
		if (player != null) {
			ChefArmInteractionPoint.Mode mode = selected.getMode();
			Lang.builder()
				.translate(mode.getTranslationKey(), Lang.blockName(state)
					.style(ChatFormatting.WHITE))
				.color(mode.getColor())
				.sendStatus(player);
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	@SubscribeEvent
	public static void leftClickingBlocksDeselectsThem(PlayerInteractEvent.LeftClickBlock event) {
		if (currentItem == null)
			return;
		if (!event.getWorld().isClientSide)
			return;
		BlockPos pos = event.getPos();
		if (remove(pos) != null) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
		}
	}

	public static void flushSettings(BlockPos pos) {
		if (currentItem == null)
			return;

		int removed = 0;
		for (Iterator<ChefArmInteractionPoint> iterator = currentSelection.iterator(); iterator.hasNext();) {
			ChefArmInteractionPoint point = iterator.next();
			if (point.getPos()
				.closerThan(pos, ChefMechanicalArmTile.getRange()))
				continue;
			iterator.remove();
			removed++;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (removed > 0) {
			Lang.builder()
				.translate("mechanical_arm.points_outside_range", removed)
				.style(ChatFormatting.RED)
				.sendStatus(player);
		} else {
			int inputs = 0;
			int outputs = 0;
			for (ChefArmInteractionPoint armInteractionPoint : currentSelection) {
				if (armInteractionPoint.getMode() == ChefArmInteractionPoint.Mode.DEPOSIT)
					outputs++;
				else
					inputs++;
			}
			if (inputs + outputs > 0)
				Lang.builder()
					.translate("mechanical_arm.summary", inputs, outputs)
					.style(ChatFormatting.WHITE)
					.sendStatus(player);
		}

		AllPackets.channel.sendToServer(new ChefArmPlacementPacket(currentSelection, pos));
		currentSelection.clear();
		currentItem = null;
	}

	public static void tick() {
		Player player = Minecraft.getInstance().player;

		if (player == null)
			return;

		ItemStack heldItemMainhand = player.getMainHandItem();
		if (!AllBlocks.MECHANICAL_ARM.isIn(heldItemMainhand)) {
			currentItem = null;
		} else {
			if (heldItemMainhand != currentItem) {
				currentSelection.clear();
				currentItem = heldItemMainhand;
			}

			drawOutlines(currentSelection);
		}

		checkForWrench(heldItemMainhand);
	}

	private static void checkForWrench(ItemStack heldItem) {
		if (!AllItems.WRENCH.isIn(heldItem)) {
			return;
		}

		HitResult objectMouseOver = Minecraft.getInstance().hitResult;
		if (!(objectMouseOver instanceof BlockHitResult)) {
			return;
		}

		BlockHitResult result = (BlockHitResult) objectMouseOver;
		BlockPos pos = result.getBlockPos();

		BlockEntity te = Minecraft.getInstance().level.getBlockEntity(pos);
		if (!(te instanceof ChefMechanicalArmTile)) {
			lastBlockPos = -1;
			currentSelection.clear();
			return;
		}

		if (lastBlockPos == -1 || lastBlockPos != pos.asLong()) {
			currentSelection.clear();
			ChefMechanicalArmTile arm = (ChefMechanicalArmTile) te;
			arm.inputs.forEach(ChefArmInteractionPointHandler::put);
			arm.outputs.forEach(ChefArmInteractionPointHandler::put);
			lastBlockPos = pos.asLong();
		}

		if (lastBlockPos != -1) {
			drawOutlines(currentSelection);
		}
	}

	private static void drawOutlines(Collection<ChefArmInteractionPoint> selection) {
		for (Iterator<ChefArmInteractionPoint> iterator = selection.iterator(); iterator.hasNext();) {
			ChefArmInteractionPoint point = iterator.next();

			if (!point.isValid()) {
				iterator.remove();
				continue;
			}

			Level level = point.getLevel();
			BlockPos pos = point.getPos();
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty())
				continue;

			int color = point.getMode()
				.getColor();
			CreateClient.OUTLINER.showAABB(point, shape.bounds()
				.move(pos))
				.colored(color)
				.lineWidth(1 / 16f);
		}
	}

	private static void put(ChefArmInteractionPoint point) {
		currentSelection.add(point);
	}

	private static ChefArmInteractionPoint remove(BlockPos pos) {
		ChefArmInteractionPoint result = getSelected(pos);
		if (result != null)
			currentSelection.remove(result);
		return result;
	}

	private static ChefArmInteractionPoint getSelected(BlockPos pos) {
		for (ChefArmInteractionPoint point : currentSelection)
			if (point.getPos()
				.equals(pos))
				return point;
		return null;
	}

}