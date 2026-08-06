package ru.multivarka.itempeek;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ItemPeek.MODID)
public class ItemPeek {
    public static final String MODID = "itempeek";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemPeek(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(Network::registerPayloads);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Network::onServerChat);
        NeoForge.EVENT_BUS.addListener(Network::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ItemPeekCommands::register);
    }
}
