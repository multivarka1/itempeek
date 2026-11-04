package ru.multivarka.itempeek.mixin.client;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChatComponent.class, remap = false)
public interface ChatComponentAccessor {
    @Accessor(value = "trimmedMessages", remap = false)
    List<GuiMessage.Line> itempeek$getTrimmedMessages();

    @Accessor(value = "chatScrollbarPos", remap = false)
    int itempeek$getChatScrollbarPos();
}
