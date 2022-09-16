package com.mystic.chef.init;

import com.mystic.chef.blocks.ChefMechanicalArmTile;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class TileInit {

    public static void init(IEventBus bus) {
        TILES.register(bus);
    }

    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, "chef");

    public static final RegistryObject<BlockEntityType<?>> CHEF_MECHANICAL_ARM = register("chef_mechanical_arm", () -> BlockEntityType.Builder.of(ChefMechanicalArmTile::new, BlockInit.CHEF_MECHANICAL_ARM.get()).build(null));

    public static RegistryObject<BlockEntityType<?>> register(String string, Supplier<BlockEntityType<?>> tile) {
        return TILES.register(string, tile);
    }
}
