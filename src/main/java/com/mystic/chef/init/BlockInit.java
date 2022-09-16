package com.mystic.chef.init;

import com.mystic.chef.blocks.ChefMechanicalArm;
import com.simibubi.create.Create;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;

public class BlockInit {
    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "chef");

    public static final RegistryObject<Block> CHEF_MECHANICAL_ARM = register("chef_mechanical_arm", () -> new ChefMechanicalArm(Block.Properties.of(Material.METAL).strength(3.0F).sound(SoundType.METAL)));

    private static RegistryObject<Block> register(String name, Supplier<Block> block, Function<RegistryObject<Block>, Supplier<? extends BlockItem>> item) {
        var reg = BLOCKS.register(name, block);
        ItemInit.ITEMS.register(name, () -> item.apply(reg).get());
        return reg;
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        return register(name, block, b -> () -> new BlockItem(b.get(),new Item.Properties().tab(Create.BASE_CREATIVE_TAB)));
    }
}
