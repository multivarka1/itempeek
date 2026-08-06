package ru.multivarka.itempeek.spam;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemPeekRateLimiter {
    public enum Result { ALLOWED, COOLDOWN, WINDOW, DUPLICATE }
    private record State(Deque<Long> times, long last, String fingerprint) {}
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    public synchronized Result checkAndRecord(UUID id, long now, boolean bypass, boolean cooldown, long cooldownMs,
                                                int limit, long windowMs, boolean duplicate, long duplicateMs, String fingerprint) {
        if (bypass) return Result.ALLOWED;
        State s=states.computeIfAbsent(id,k->new State(new ArrayDeque<>(),0,null));
        while(!s.times.isEmpty() && s.times.peekFirst() <= now-windowMs) s.times.removeFirst();
        if(cooldown && now-s.last < cooldownMs) return Result.COOLDOWN;
        if(s.times.size() >= limit) return Result.WINDOW;
        if(duplicate && fingerprint != null && fingerprint.equals(s.fingerprint) && now-s.last < duplicateMs) return Result.DUPLICATE;
        s.times.addLast(now); states.put(id,new State(s.times,now,fingerprint)); return Result.ALLOWED;
    }
    public synchronized void clear(UUID id){states.remove(id);} public synchronized void clearAll(){states.clear();}
    public synchronized int count(UUID id){State s=states.get(id);return s==null?0:s.times.size();}
}
