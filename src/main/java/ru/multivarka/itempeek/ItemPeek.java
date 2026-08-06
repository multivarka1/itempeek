package ru.multivarka.itempeek;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(ItemPeek.MODID)
public class ItemPeek {
    public static final String MODID = "itempeek";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemPeek(ModContainer container) {
        Network.init();
        NeoForge.EVENT_BUS.addListener(Network::onServerChat);
        NeoForge.EVENT_BUS.addListener(Network::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ItemPeekCommands::register);
    }
}
