package ru.multivarka.itempeek.config;

public record ItemPeekConfigSnapshot(
        boolean cooldownEnabled, long cooldownMillis, int windowLimit, long windowMillis,
        boolean duplicateProtection, long duplicateWindowMillis, int bypassPermissionLevel,
        boolean globalEnabled, boolean chatEnabled, boolean privateEnabled, long pendingTtlMillis,
        int maxMessageLength, int maxTargetLength, int maxPendingPerPlayer, String privateAliases) {
    public static final ItemPeekConfigSnapshot DEFAULTS = new ItemPeekConfigSnapshot(
            true, 3000, 5, 10000, true, 5000, 2, true, true, true, 15000, 256, 256, 1,
            "msg,tell,w,minecraft:msg,minecraft:tell,minecraft:w");

    public ItemPeekConfigSnapshot {
        if (cooldownMillis < 0 || windowLimit < 1 || windowMillis < 1 || duplicateWindowMillis < 0
                || bypassPermissionLevel < 0 || pendingTtlMillis < 1 || maxMessageLength < 1
                || maxTargetLength < 1 || maxPendingPerPlayer < 1) {
            throw new IllegalArgumentException("Invalid Item Peek configuration range");
        }
    }
}
