package com.mystic.chef;

import com.mojang.logging.LogUtils;
import com.mystic.chef.init.BlockInit;
import com.mystic.chef.init.ItemInit;
import com.mystic.chef.init.TileInit;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("chef")
public class Chef
{
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Chef()
    {
        // Register the setup method for modloading
        MinecraftForge.EVENT_BUS.register(this);
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ItemInit.init(bus);
        BlockInit.init(bus);
        TileInit.init(bus);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}
}
