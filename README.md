# Item Peek

NeoForge 1.21.1 mod for showing inventory items in chat. Shift+T broadcasts an item and Shift+Y prepares an item for normal or private chat. Vanilla `/msg`, `/tell`, `/w` and namespaced forms are supported.

## Server protection

Item Peek uses a server-side shared sliding-window limiter for quick broadcasts, public item messages and private item messages. Defaults are a 3-second cooldown, 5 messages per 10 seconds, duplicate protection for 5 seconds, and a 15-second pending-item TTL. Ordinary chat without an item is unaffected. State is keyed by player UUID and cleared on logout.

The runtime file is `serverconfig/itempeek-server.properties`. It is loaded when the server starts and can be safely reloaded/saved without restarting. Main options include `cooldownEnabled`, `cooldownMillis`, `windowLimit`, `windowMillis`, `duplicateProtection`, `duplicateWindowMillis`, `bypassPermissionLevel`, `globalEnabled`, `chatEnabled`, `privateEnabled`, `pendingTtlMillis`, `maxMessageLength`, `maxTargetLength`, `maxPendingPerPlayer`, and `privateAliases`.

Operators at `bypassPermissionLevel` (default 2) bypass anti-spam. The same level protects administration commands. No permission mod is required.

## Administration

Commands apply changes immediately unless stated otherwise:

```
/itempeek config list
/itempeek config get cooldownMillis
/itempeek config set cooldownMillis 5000
/itempeek config reset-all
/itempeek config reload
/itempeek config save
/itempeek antispam status PlayerName
/itempeek antispam reset PlayerName
/itempeek antispam reset-all
/itempeek private-commands list
/itempeek private-commands add whisper
/itempeek private-commands remove whisper
/itempeek private-commands reset
```

Private aliases are normalized to lowercase identifiers, deduplicated and validated. Custom aliases must describe a command with the normal target-then-message shape; aliases do not change the server's command semantics. The client-side chat screen currently recognizes the vanilla aliases until the client is restarted after an alias change.

## Optional integrations and Catalogue

Beautified Chat Server is optional. Its reflection API is discovered once, cached, and disabled after a runtime failure; standard Minecraft formatting remains available. Catalogue is also optional and is not a dependency. Branding metadata points to `assets/itempeek/icon.png`.

## License

MIT.
