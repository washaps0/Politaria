# PolitariaTags

Separate clean source plugin for player tags. Keep your original `PolitariaIdentity.jar` installed.

Tags:
- Creator
- Dev
- Admin (automatic for OP players)
- Media

A player may have multiple tags.

## Commands

- `/tag add <player> Creator`
- `/tag add <player> Dev`
- `/tag add <player> Media`
- `/tag remove <player> <tag>`
- `/tag list <player>`

`Admin` is never stored manually. It appears while `player.isOp()` is true.

Media permissions are configured in `config.yml`.

## Build

Requires Java 21 and Maven 3.9+.

Windows PowerShell / CMD:

```text
mvn clean package
```

Output:

```text
target/politaria-tags-1.0.0.jar
```

Put that JAR into `plugins/` next to the original `PolitariaIdentity.jar`, then fully restart the server.
