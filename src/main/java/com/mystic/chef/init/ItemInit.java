package com.mystic.chef.init;

import com.mystic.chef.items.ChefHat;
import com.simibubi.create.Create;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ItemInit {
    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "chef");

    public static final RegistryObject<Item> CHEF_HAT = register("chef_hat", () -> new ChefHat(new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    public static <T extends Item> RegistryObject<T> register(String name, Supplier<T> item) {
        return ITEMS.register(name, item);
    }
}
