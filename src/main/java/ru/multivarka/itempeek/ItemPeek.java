package ru.multivarka.itempeek;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;

@Mod(ItemPeek.MODID)
public class ItemPeek {
    public static final String MODID = "itempeek";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemPeek(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(Network::registerPayloads);
    }
}
