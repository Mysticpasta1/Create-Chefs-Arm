package com.mystic.chef.items;

import java.util.Collection;
import java.util.function.Supplier;

import com.mystic.chef.blocks.ChefArmInteractionPoint;
import com.mystic.chef.blocks.ChefMechanicalArmTile;
import com.simibubi.create.foundation.networking.SimplePacketBase;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;

public class ChefArmPlacementPacket extends SimplePacketBase {

	private Collection<ChefArmInteractionPoint> points;
	private ListTag receivedTag;
	private BlockPos pos;

	public ChefArmPlacementPacket(Collection<ChefArmInteractionPoint> points, BlockPos pos) {
		this.points = points;
		this.pos = pos;
	}

	public ChefArmPlacementPacket(FriendlyByteBuf buffer) {
		CompoundTag nbt = buffer.readNbt();
		receivedTag = nbt.getList("Points", Tag.TAG_COMPOUND);
		pos = buffer.readBlockPos();
	}

	@Override
	public void write(FriendlyByteBuf buffer) {
		CompoundTag nbt = new CompoundTag();
		ListTag pointsNBT = new ListTag();
		points.stream()
			.map(aip -> aip.serialize(pos))
			.forEach(pointsNBT::add);
		nbt.put("Points", pointsNBT);
		buffer.writeNbt(nbt);
		buffer.writeBlockPos(pos);
	}

	@Override
	public void handle(Supplier<Context> context) {
		context.get()
			.enqueueWork(() -> {
				ServerPlayer player = context.get()
					.getSender();
				if (player == null)
					return;
				Level world = player.level;
				if (world == null || !world.isLoaded(pos))
					return;
				BlockEntity tileEntity = world.getBlockEntity(pos);
				if (!(tileEntity instanceof ChefMechanicalArmTile))
					return;

				ChefMechanicalArmTile arm = (ChefMechanicalArmTile) tileEntity;
				arm.interactionPointTag = receivedTag;
			});
		context.get()
			.setPacketHandled(true);

	}

}
