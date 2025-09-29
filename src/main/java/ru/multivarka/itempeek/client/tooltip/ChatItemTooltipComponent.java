package ru.multivarka.itempeek.client.tooltip;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ChatItemTooltipComponent(ItemStack stack, Component title) implements TooltipComponent {
    public ChatItemTooltipComponent {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(title, "title");
        stack = stack.copy();
        title = title.copy();
    }
}
