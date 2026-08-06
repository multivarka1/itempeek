package ru.multivarka.itempeek;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import ru.multivarka.itempeek.chat.PrivateMessageCommandParser;
import ru.multivarka.itempeek.config.ItemPeekConfigService;
import ru.multivarka.itempeek.config.ItemPeekConfigSnapshot;
import java.util.*;

public final class ItemPeekCommands {
    private ItemPeekCommands() {}
    public static void register(RegisterCommandsEvent event) {
        var root=Commands.literal("itempeek").requires(s->{if(ItemPeekConfigService.path()==null)ItemPeekConfigService.initialize(s.getServer());return s.hasPermission(ItemPeekConfigService.snapshot().bypassPermissionLevel());});
        var config=Commands.literal("config").then(Commands.literal("list").executes(c->{ItemPeekConfigService.values().forEach((k,v)->c.getSource().sendSuccess(()->Component.literal(k+" = "+v),false));return 1;}))
                .then(Commands.literal("get").then(Commands.argument("option",StringArgumentType.word()).executes(c->{String k=StringArgumentType.getString(c,"option");String v=ItemPeekConfigService.values().get(k);if(v==null)throw error("Unknown option: "+k);c.getSource().sendSuccess(()->Component.literal(k+" = "+v),false);return 1;})))
                .then(Commands.literal("set").then(Commands.argument("option",StringArgumentType.word()).then(Commands.argument("value",StringArgumentType.greedyString()).executes(c->{boolean ok=ItemPeekConfigService.set(StringArgumentType.getString(c,"option"),StringArgumentType.getString(c,"value"));if(!ok)throw error("Invalid option or value");c.getSource().sendSuccess(()->Component.literal("Configuration updated"),true);return 1;}))))
                .then(Commands.literal("reset").then(Commands.argument("option",StringArgumentType.word()).executes(c->{boolean ok=ItemPeekConfigService.reset(StringArgumentType.getString(c,"option"));if(!ok)throw error("Unknown option");return 1;})))
                .then(Commands.literal("reset-all").executes(c->{ItemPeekConfigService.resetAll();return 1;}))
                .then(Commands.literal("reload").executes(c->{boolean ok=ItemPeekConfigService.reload();c.getSource().sendSuccess(()->Component.literal(ok?"Configuration reloaded":"Reload failed; previous snapshot kept"),true);return ok?1:0;}))
                .then(Commands.literal("save").executes(c->{boolean ok=ItemPeekConfigService.save();c.getSource().sendSuccess(()->Component.literal(ok?"Configuration saved":"Save failed"),true);return ok?1:0;}));
        var anti=Commands.literal("antispam").then(Commands.literal("status").then(Commands.argument("player",EntityArgument.player()).executes(c->{ServerPlayer p=EntityArgument.getPlayer(c,"player");c.getSource().sendSuccess(()->Component.literal(p.getName().getString()+" active messages: "+Network.stateCount(p.getUUID())),false);return 1;}))).then(Commands.literal("reset").then(Commands.argument("player",EntityArgument.player()).executes(c->{Network.reset(EntityArgument.getPlayer(c,"player").getUUID());return 1;}))).then(Commands.literal("reset-all").executes(c->{Network.resetAll();return 1;}));
        var priv=Commands.literal("private-commands").then(Commands.literal("list").executes(c->{c.getSource().sendSuccess(()->Component.literal(ItemPeekConfigService.snapshot().privateAliases()),false);return 1;})).then(Commands.literal("add").then(Commands.argument("command",StringArgumentType.word()).executes(c->changeAlias(c.getSource(),StringArgumentType.getString(c,"command"),true)))).then(Commands.literal("remove").then(Commands.argument("command",StringArgumentType.word()).executes(c->changeAlias(c.getSource(),StringArgumentType.getString(c,"command"),false)))).then(Commands.literal("reset").executes(c->{ItemPeekConfigService.set("privateAliases",ItemPeekConfigSnapshot.DEFAULTS.privateAliases());return 1;}));
        event.getDispatcher().register(root.then(config).then(anti).then(priv));
    }
    private static int changeAlias(net.minecraft.commands.CommandSourceStack source,String raw,boolean add) throws CommandSyntaxException {String alias=PrivateMessageCommandParser.normalizeAlias(raw);if(alias==null)throw error("Invalid command alias");var list=new LinkedHashSet<>(Arrays.asList(ItemPeekConfigService.snapshot().privateAliases().split(",")));if(add)list.add(alias);else list.remove(alias);ItemPeekConfigService.set("privateAliases",String.join(",",list));source.sendSuccess(()->Component.literal("Private command aliases updated"),true);return 1;}
    private static CommandSyntaxException error(String message){return new SimpleCommandExceptionType(Component.literal(message)).create();}
}
