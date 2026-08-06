package ru.multivarka.itempeek;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import ru.multivarka.itempeek.compat.BeautifiedChatCompat;
import ru.multivarka.itempeek.config.ItemPeekConfigService;
import ru.multivarka.itempeek.config.ItemPeekConfigSnapshot;
import ru.multivarka.itempeek.spam.ItemPeekRateLimiter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Network {
    private static final int MAX_SHOW_ITEM_HOVER_COUNT=99;
    private static final Map<UUID,PendingItem> PENDING=new ConcurrentHashMap<>();
    private static final ItemPeekRateLimiter LIMITER=new ItemPeekRateLimiter();
    private static final BeautifiedChatCompat BEAUTIFIED=new BeautifiedChatCompat();
    private Network(){}
    public static void init(){}
    @SubscribeEvent public static void registerPayloads(RegisterPayloadHandlersEvent event){var r=event.registrar(ItemPeek.MODID);
        r.playToServer(ItemPeekPayload.TYPE,ItemPeekPayload.CODEC,(m,c)->c.enqueueWork(()->handleShowItem((ServerPlayer)c.player(),m.slotIndex())));
        r.playToServer(InsertItemPayload.TYPE,InsertItemPayload.CODEC,(m,c)->c.enqueueWork(()->prepare((ServerPlayer)c.player(),m.slotIndex(),m.marker())));
        r.playToServer(PrivateItemMessagePayload.TYPE,PrivateItemMessagePayload.CODEC,(m,c)->c.enqueueWork(()->privateMessage((ServerPlayer)c.player(),m.targets(),m.message())));
        r.playToServer(ClearPendingItemPayload.TYPE,ClearPendingItemPayload.CODEC,(m,c)->c.enqueueWork(()->PENDING.remove(c.player().getUUID())));
    }
    private static ItemPeekConfigSnapshot cfg(){return ItemPeekConfigService.snapshot();}
    private static boolean validText(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max&&s.codePoints().noneMatch(Character::isISOControl);}
    private static boolean bypass(ServerPlayer p){return p.permissions() instanceof net.minecraft.server.permissions.LevelBasedPermissionSet permissions && permissions.level().isEqualOrHigherThan(net.minecraft.server.permissions.PermissionLevel.byId(cfg().bypassPermissionLevel()));}
    private static void ensureConfig(ServerPlayer p){if(ItemPeekConfigService.path()==null)ItemPeekConfigService.initialize(p.level().getServer());}
    private static void handleShowItem(ServerPlayer p,int slot){ensureConfig(p);ItemPeekConfigSnapshot c=cfg();if(!c.globalEnabled()){blocked(p,"message.itempeek.disabled");return;}if(slot<0||slot>=p.containerMenu.slots.size())return;ItemStack stack=p.containerMenu.getSlot(slot).getItem();if(stack.isEmpty()){blocked(p,"message.itempeek.no_item");return;}if(!allow(p,stack,"global"))return;Component shown=createShownItem(stack,true,true);Component msg=Component.translatable(stack.getCount()>1?"message.itempeek.shows_item_count":"message.itempeek.shows_item",p.getName(),shown,stack.getCount()).withStyle(ChatFormatting.GRAY);for(ServerPlayer target:p.level().getServer().getPlayerList().getPlayers())target.sendSystemMessage(msg);}
    private static boolean allow(ServerPlayer p,ItemStack stack,String kind){ItemPeekConfigSnapshot c=cfg();if(bypass(p))return true;long now=System.currentTimeMillis();var result=LIMITER.checkAndRecord(p.getUUID(),now,false,c.cooldownEnabled(),c.cooldownMillis(),c.windowLimit(),c.windowMillis(),c.duplicateProtection(),c.duplicateWindowMillis(),kind+":"+stack.getHoverName().getString());if(result==ItemPeekRateLimiter.Result.ALLOWED)return true;blocked(p,result==ItemPeekRateLimiter.Result.COOLDOWN?"message.itempeek.cooldown":result==ItemPeekRateLimiter.Result.WINDOW?"message.itempeek.rate_limit":"message.itempeek.duplicate");return false;}
    private static void prepare(ServerPlayer p,int slot,String marker){ensureConfig(p);ItemPeekConfigSnapshot c=cfg();if(!c.chatEnabled()||slot<0||slot>=p.containerMenu.slots.size()||!validText(marker,256))return;ItemStack stack=p.containerMenu.getSlot(slot).getItem();if(stack.isEmpty())return;PENDING.put(p.getUUID(),new PendingItem(stack.copy(),marker,System.currentTimeMillis()));}
    public static void onServerChat(ServerChatEvent event){ServerPlayer p=event.getPlayer();ensureConfig(p);PendingItem pending=PENDING.get(p.getUUID());if(pending==null)return;ItemPeekConfigSnapshot c=cfg();if(System.currentTimeMillis()-pending.created()>c.pendingTtlMillis()){PENDING.remove(p.getUUID(),pending);blocked(p,"message.itempeek.pending_expired");return;}String raw=event.getRawText();if(!validText(raw,c.maxMessageLength())){PENDING.remove(p.getUUID(),pending);return;}int at=raw.indexOf(pending.marker());if(at<0)return; if(!allow(p,pending.stack(),"chat"))return;Component message=Component.literal(raw.substring(0,at)).append(createShownItem(pending.stack(),false,true)).append(raw.substring(at+pending.marker().length()));if(BEAUTIFIED.broadcast(p,message))event.setCanceled(true);else event.setMessage(message);PENDING.remove(p.getUUID(),pending);}
    private static void privateMessage(ServerPlayer sender,String targets,String message){ensureConfig(sender);ItemPeekConfigSnapshot c=cfg();PendingItem pending=PENDING.get(sender.getUUID());if(!c.privateEnabled()||pending==null||!validText(targets,c.maxTargetLength())||!validText(message,c.maxMessageLength()))return;if(System.currentTimeMillis()-pending.created()>c.pendingTtlMillis()){PENDING.remove(sender.getUUID(),pending);blocked(sender,"message.itempeek.pending_expired");return;}int at=message.indexOf(pending.marker());if(at<0)return;Collection<ServerPlayer> targetsFound;try{var source=sender.createCommandSourceStack();targetsFound=EntityArgument.players().parse(new StringReader(targets),source).findPlayers(source);if(targetsFound.isEmpty())throw EntityArgument.NO_PLAYERS_FOUND.create();}catch(CommandSyntaxException e){sender.sendSystemMessage(Component.translatable("message.itempeek.invalid_target").withStyle(ChatFormatting.RED));return;}if(!allow(sender,pending.stack(),"private"))return;Component content=Component.literal(message.substring(0,at)).append(createShownItem(pending.stack(),false,true)).append(message.substring(at+pending.marker().length()));var source=sender.createCommandSourceStack();for(ServerPlayer target:targetsFound){ChatType.Bound out=ChatType.bind(ChatType.MSG_COMMAND_OUTGOING,source).withTargetName(target.getDisplayName());sender.sendSystemMessage(out.decorate(content));target.sendSystemMessage(ChatType.bind(ChatType.MSG_COMMAND_INCOMING,source).decorate(content));}PENDING.remove(sender.getUUID(),pending);}
    private static void blocked(ServerPlayer p,String key){p.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));}
    @SubscribeEvent public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e){PENDING.remove(e.getEntity().getUUID());LIMITER.clear(e.getEntity().getUUID());}
    public static void reset(UUID id){LIMITER.clear(id);}
    public static void resetAll(){LIMITER.clearAll();}
    public static int stateCount(UUID id){return LIMITER.count(id);}
    private static Component createShownItem(ItemStack stack,boolean leading,boolean count){Component name=stack.getHoverName().copy();ChatFormatting color=switch(stack.getRarity()){case UNCOMMON->ChatFormatting.YELLOW;case RARE->ChatFormatting.AQUA;case EPIC->ChatFormatting.LIGHT_PURPLE;default->ChatFormatting.WHITE;};MutableComponent shown=Component.literal(leading?" ":"").append(Component.literal("[")).append(name.copy().withStyle(s->s.withColor(color).withItalic(false))).append(Component.literal("]"));ItemStack hover=stack.copy();hover.setCount(Math.min(stack.getCount(),Math.min(stack.getMaxStackSize(),MAX_SHOW_ITEM_HOVER_COUNT)));shown.withStyle(s->s.withHoverEvent(new HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(hover))));if(count&&stack.getCount()>1)shown.append(Component.literal(" x"+stack.getCount()).withStyle(ChatFormatting.GRAY));return shown;}
    private record PendingItem(ItemStack stack,String marker,long created){}
}
