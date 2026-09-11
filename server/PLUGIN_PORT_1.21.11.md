# Minecraft 1.21.11 migration

Tested server runtime: Purpur 1.21.11 on Java 21.

Updated/verified:
- Citizens
- ProtocolLib
- TAB
- Simple Voice Chat
- SkinsRestorer
- ViaVersion
- ViaBackwards
- Skript
- skript-reflect
- GrimAC when a newer stable 1.21.11 build is available

Intentionally pinned/retained:
- BlueMap 5.16, because it loads on 1.21.11 and remains API-compatible with PolitariaMapBridge
- CoreProtect 24.0
- Orebfuscator 5.6.1
- FastLeafDecay 2.0.1
- AutoBackup 1.1
- DisableJoinMessage 1.1

Disabled on 1.21.11:
- ProAntiTab 2.4.3: the latest available build throws a `NullPointerException` from `BukkitLoader.updateCommands` when a player joins on Purpur 1.21.11. Its config/data folder is intentionally kept so it can be restored later without losing settings.

Removed / not managed by this workflow:
- WorldEdit

Compatibility fixes:
- `justice.sk`: old `chain` GUI item alias replaced with `iron bars`
- TAB configuration migrated to config-version 7
- obsolete TAB example-group header/footer removed
- LuckPerms-only TAB placeholders removed because Politaria does not use LuckPerms
- duplicate `politaria.tags.manage` permission declaration removed from PolitariaTags metadata; PolitariaIdentity remains the owner of that permission

Expected warnings that are intentional configuration choices, not startup failures:
- offline-mode / voice-chat encryption warning: Politaria uses its own authentication flow
- GrimAC + ViaBackwards vehicle warning: applies to old translated clients using vehicles
- old `minecraft:chain` recipe/advancement entries may be removed from existing player data during the first 1.21.11 load
- Orebfuscator may fall back from `L64X128StarStarRandom` to `SplittableRandom`; this is a fallback warning, not a startup failure
