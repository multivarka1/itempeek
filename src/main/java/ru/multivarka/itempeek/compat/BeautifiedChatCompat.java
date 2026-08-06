package ru.multivarka.itempeek.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import ru.multivarka.itempeek.ItemPeek;
import java.lang.reflect.*; import java.text.SimpleDateFormat; import java.util.*;

public final class BeautifiedChatCompat {
    private boolean initialized; private boolean available; private Field timestampFormat, chatMessageFormat, showRankTitles, rankTitleFormat; private Method colour, rank;
    public synchronized boolean broadcast(ServerPlayer player, Component message){ if(!init())return false; try{MutableComponent out=Component.literal("");String ts=new SimpleDateFormat((String)timestampFormat.get(null)).format(new Date());String user=player.getName().getString();for(String segment:((String)chatMessageFormat.get(null)).split("%",-1)){ChatFormatting c=(ChatFormatting)colour.invoke(null,segment,user);Component p=segment.equalsIgnoreCase("timestamp")?Component.literal(ts):segment.equalsIgnoreCase("username")?Component.literal(username(user)):segment.equalsIgnoreCase("chatmessage")?message.copy():Component.literal(segment);out.append(p.copy().withStyle(c));}player.server.getPlayerList().broadcastSystemMessage(out,false);return true;}catch(Throwable e){available=false;ItemPeek.LOGGER.warn("Beautified Chat compatibility disabled after a runtime error");return false;}}
    private String username(String user)throws Exception{if(!showRankTitles.getBoolean(null))return user;Optional<?> r=(Optional<?>)rank.invoke(null,user);if(r.isEmpty())return user;return ((String)rankTitleFormat.get(null)).replace("%rank",r.get().toString())+user;}
    private boolean init(){if(initialized)return available;initialized=true;if(!ModList.get().isLoaded("beautifiedchatserver"))return false;try{Class<?> c=Class.forName("com.natamus.beautifiedchatserver_common_forge.config.ConfigHandler");timestampFormat=c.getField("timestampFormat");chatMessageFormat=c.getField("chatMessageFormat");showRankTitles=c.getField("showRankTitles");rankTitleFormat=c.getField("rankTitleFormat");Class<?> u=Class.forName("com.natamus.beautifiedchatserver_common_forge.util.Util");colour=u.getMethod("getColour",String.class,String.class);rank=u.getMethod("getRankOfPlayer",String.class);available=true;return true;}catch(Throwable e){return false;}}
}
