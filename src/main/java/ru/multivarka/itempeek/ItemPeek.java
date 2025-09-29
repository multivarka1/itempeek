package ru.multivarka.itempeek;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ItemPeek.MODID)
public class ItemPeek {
    public static final String MODID = "itempeek";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Forge requires a no-arg constructor for @Mod classes
    public ItemPeek() {
        Network.init();
    }
}
