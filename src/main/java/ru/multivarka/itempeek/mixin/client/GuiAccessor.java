package ru.multivarka.itempeek.mixin.client;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Gui.class, remap = false)
public interface GuiAccessor {
    @Accessor(value = "tickCount", remap = false)
    int itempeek$getTickCount();
}
