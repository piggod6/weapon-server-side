# Emperor Time Server — Fabric 1.21.11

This is a true server-side Fabric mod. Players can join with vanilla clients; they do not need this mod installed. It uses tagged vanilla items rather than registering new items.

- **Caboom:** right-click the specially tagged echo shard while aiming. It creates an approximately 200-block-wide bowl crater with sculk, deepslate, soul sand, soul fire, particles and Warden sonic-boom effects. Block edits are spread over server ticks.
- **Flight:** right-click the specially tagged feather to toggle survival flight on or off.

## Give the powers

As an operator, run these commands in game:

```mcfunction
/give @s minecraft:echo_shard[minecraft:custom_name='{"text":"Caboom","italic":false}',minecraft:custom_data={godcore_caboom:1b}] 1
/give @s minecraft:feather[minecraft:custom_name='{"text":"Flight","italic":false}',minecraft:custom_data={godcore_flight:1b}] 1
```

These are regular vanilla echo shard and feather items with custom names and data, so unmodded clients can hold and use them. The mod recognizes the data on the server.

## Install on a server

1. Use Fabric Loader **0.19.5 or newer** for Minecraft 1.21.11.
2. Put this mod JAR and the matching Fabric API JAR in the server's `mods` folder.
3. Start the server. Players do not need the mod in their client `mods` folder.

Back up the world before using Caboom; it alters a huge area of terrain and may cause lag.

## Build on GitHub

The existing GitHub Actions build workflow builds `emperor-time-1.0.0.jar` after the updated Java source and `fabric.mod.json` are committed to the repository.
