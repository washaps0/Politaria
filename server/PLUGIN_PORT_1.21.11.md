# Minecraft 1.21.11 migration

Updated automatically from upstream sources:
- BlueMap
- Citizens
- ProtocolLib (official dev build with explicit 1.21.11 support)
- TAB
- WorldEdit
- Simple Voice Chat
- SkinsRestorer
- ViaVersion
- ViaBackwards
- Skript
- skript-reflect
- GrimAC when a newer stable Modrinth 1.21.11 build is available

Script fixes:
- `justice.sk`: replaced the no-longer-parsing `chain` GUI item alias with `iron bars` for the cuffs controls.

Already current / working in the 1.21.11 boot log and therefore retained:
- CoreProtect 24.0
- Orebfuscator 5.6.1 (found the v1_21_R7_mojang adapter)
- FastLeafDecay 2.0.1
- ProAntiTab 2.4.3
- AutoBackup 1.1
- DisableJoinMessage 1.1

Custom Politaria jars are intentionally retained because they enabled successfully on Purpur 1.21.11:
- PolitariaApartments
- PolitariaIdentity
- PolitariaMapBridge
- PolitariaSpawnProtection
- PolitariaTags

Remaining non-fatal warnings to test in-game:
- `PolitariaTags` and another plugin both register `politaria.tags.manage`; functionality works but the duplicate permission should eventually be consolidated in source.
- GrimAC warns that ViaBackwards vehicle behavior on old clients is unsupported. This is an upstream compatibility limitation, not a server-start failure.
- Offline mode / voice-chat encryption warnings are expected while the server uses offline-mode authentication.
