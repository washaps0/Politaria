# PolitariaLocator

Paper/Purpur 1.21.11 plugin for Politaria's vanilla Locator Bar.

The plugin intercepts `TRACKED_WAYPOINT` packets through ProtocolLib and only allows a player's marker to be sent when the viewer and target belong to the same Politaria country.

Country membership is read directly from Skript's existing variable:

`{country.player::<uuid>} = country id`

Behavior:
- same country: visible
- different country: hidden
- viewer without a country: sees no player markers
- target without a country: hidden

Requirements:
- Purpur/Paper 1.21.11
- Java 21
- ProtocolLib
- Skript

Build:

```bash
gradle build
```

Output:

`build/libs/PolitariaLocator-1.0.0.jar`

The source intentionally avoids scoreboard teams, so it does not conflict with TAB's team/name-tag management.
