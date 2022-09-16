package com.mystic.chef.blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ChefArmInteractionPointType {

	private static final Map<ResourceLocation, ChefArmInteractionPointType> TYPES = new HashMap<>();
	private static final List<ChefArmInteractionPointType> SORTED_TYPES = new ArrayList<>();

	protected final ResourceLocation id;

	public ChefArmInteractionPointType(ResourceLocation id) {
		this.id = id;
	}

	public static void register(ChefArmInteractionPointType type) {
		ResourceLocation id = type.getId();
		if (TYPES.containsKey(id))
			throw new IllegalArgumentException("Tried to override ChefArmInteractionPointType registration for id '" + id + "'. This is not supported!");
		TYPES.put(id, type);
		SORTED_TYPES.add(type);
		SORTED_TYPES.sort((t1, t2) -> t2.getPriority() - t1.getPriority());
	}

	@Nullable
	public static ChefArmInteractionPointType get(ResourceLocation id) {
		return TYPES.get(id);
	}

	public static void forEach(Consumer<ChefArmInteractionPointType> action) {
		SORTED_TYPES.forEach(action);
	}

	@Nullable
	public static ChefArmInteractionPointType getPrimaryType(Level level, BlockPos pos, BlockState state) {
		for (ChefArmInteractionPointType type : SORTED_TYPES)
			if (type.canCreatePoint(level, pos, state))
				return type;
		return null;
	}

	public final ResourceLocation getId() {
		return id;
	}

	public abstract boolean canCreatePoint(Level level, BlockPos pos, BlockState state);

	@Nullable
	public abstract ChefArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state);

	public int getPriority() {
		return 0;
	}

}
