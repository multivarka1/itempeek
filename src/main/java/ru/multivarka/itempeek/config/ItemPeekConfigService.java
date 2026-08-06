package ru.multivarka.itempeek.config;

import net.minecraft.server.MinecraftServer;
import ru.multivarka.itempeek.ItemPeek;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;

public final class ItemPeekConfigService {
    private static volatile ItemPeekConfigSnapshot snapshot = ItemPeekConfigSnapshot.DEFAULTS;
    private static Path path;
    private ItemPeekConfigService() {}

    public static synchronized void initialize(MinecraftServer server) {
        path = server.getServerDirectory().toPath().resolve("serverconfig").resolve("itempeek-server.properties");
        reload();
    }
    public static ItemPeekConfigSnapshot snapshot() { return snapshot; }
    public static synchronized boolean reload() {
        if (path == null) return false;
        if (!Files.exists(path)) { snapshot = ItemPeekConfigSnapshot.DEFAULTS; return save(); }
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
            snapshot = parse(p, ItemPeekConfigSnapshot.DEFAULTS);
            return true;
        } catch (Exception e) {
            ItemPeek.LOGGER.warn("Unable to reload Item Peek configuration; keeping the previous snapshot", e);
            return false;
        }
    }
    public static synchronized boolean save() {
        if (path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, toProperties(snapshot), StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException e) { ItemPeek.LOGGER.warn("Unable to save Item Peek configuration", e); return false; }
    }
    public static synchronized void update(UnaryOperator<ItemPeekConfigSnapshot> updater) { snapshot = updater.apply(snapshot); save(); }
    public static synchronized boolean reset(String key) {
        if (!toProps(snapshot).containsKey(key)) return false;
        Properties p = toProps(ItemPeekConfigSnapshot.DEFAULTS);
        p.putAll(toProps(snapshot));
        p.setProperty(key, p.getProperty(key));
        if (toProps(ItemPeekConfigSnapshot.DEFAULTS).containsKey(key)) p.setProperty(key, toProps(ItemPeekConfigSnapshot.DEFAULTS).getProperty(key));
        try { snapshot = parse(p, ItemPeekConfigSnapshot.DEFAULTS); save(); return true; } catch (Exception e) { return false; }
    }
    public static synchronized void resetAll() { snapshot = ItemPeekConfigSnapshot.DEFAULTS; save(); }
    public static Path path() { return path; }

    private static Properties defaultsExcept(ItemPeekConfigSnapshot s, String ignored) {
        Properties p = toProps(s); p.remove(ignored); return p;
    }
    private static ItemPeekConfigSnapshot parse(Properties p, ItemPeekConfigSnapshot fallback) {
        return new ItemPeekConfigSnapshot(bool(p,"cooldownEnabled",fallback.cooldownEnabled()), lng(p,"cooldownMillis",fallback.cooldownMillis(),0,600000),
                integer(p,"windowLimit",fallback.windowLimit(),1,10000), lng(p,"windowMillis",fallback.windowMillis(),1,86400000),
                bool(p,"duplicateProtection",fallback.duplicateProtection()), lng(p,"duplicateWindowMillis",fallback.duplicateWindowMillis(),0,86400000),
                integer(p,"bypassPermissionLevel",fallback.bypassPermissionLevel(),0,4), bool(p,"globalEnabled",fallback.globalEnabled()),
                bool(p,"chatEnabled",fallback.chatEnabled()), bool(p,"privateEnabled",fallback.privateEnabled()), lng(p,"pendingTtlMillis",fallback.pendingTtlMillis(),1,600000),
                integer(p,"maxMessageLength",fallback.maxMessageLength(),1,32767), integer(p,"maxTargetLength",fallback.maxTargetLength(),1,256),
                integer(p,"maxPendingPerPlayer",fallback.maxPendingPerPlayer(),1,16), normalizeAliases(p.getProperty("privateAliases",fallback.privateAliases())));
    }
    private static boolean bool(Properties p,String k,boolean d){String v=p.getProperty(k); return v==null?d:Boolean.parseBoolean(v);}
    private static long lng(Properties p,String k,long d,long min,long max){String v=p.getProperty(k); if(v==null||v.isBlank())return d; long n=Long.parseLong(v); if(n<min||n>max)throw new IllegalArgumentException(k);return n;}
    private static int integer(Properties p,String k,int d,int min,int max){return (int)lng(p,k,d,min,max);}
    private static String normalizeAliases(String value) { return String.join(",", java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s->s.matches("[a-z0-9_:-]+" )).distinct().toList()); }
    private static Properties toProps(ItemPeekConfigSnapshot s){Properties p=new Properties(); p.setProperty("cooldownEnabled",Boolean.toString(s.cooldownEnabled()));p.setProperty("cooldownMillis",Long.toString(s.cooldownMillis()));p.setProperty("windowLimit",Integer.toString(s.windowLimit()));p.setProperty("windowMillis",Long.toString(s.windowMillis()));p.setProperty("duplicateProtection",Boolean.toString(s.duplicateProtection()));p.setProperty("duplicateWindowMillis",Long.toString(s.duplicateWindowMillis()));p.setProperty("bypassPermissionLevel",Integer.toString(s.bypassPermissionLevel()));p.setProperty("globalEnabled",Boolean.toString(s.globalEnabled()));p.setProperty("chatEnabled",Boolean.toString(s.chatEnabled()));p.setProperty("privateEnabled",Boolean.toString(s.privateEnabled()));p.setProperty("pendingTtlMillis",Long.toString(s.pendingTtlMillis()));p.setProperty("maxMessageLength",Integer.toString(s.maxMessageLength()));p.setProperty("maxTargetLength",Integer.toString(s.maxTargetLength()));p.setProperty("maxPendingPerPlayer",Integer.toString(s.maxPendingPerPlayer()));p.setProperty("privateAliases",s.privateAliases());return p;}
    private static String toProperties(ItemPeekConfigSnapshot s){StringBuilder b=new StringBuilder(); for(var e:toProps(s).entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n'); return b.toString();}
    public static Map<String,String> values(){ Map<String,String> m=new LinkedHashMap<>(); for(var e:toProps(snapshot).entrySet())m.put(String.valueOf(e.getKey()),String.valueOf(e.getValue()));return m; }
    public static boolean set(String key,String value){ Properties p=toProps(snapshot); p.setProperty(key,value); try { snapshot=parse(p, snapshot); save(); return true; } catch(Exception e){return false;} }
}
