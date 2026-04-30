# AkifsCustomCommands

Spigot/Paper plugin that lets server owners create their own reward commands such as `/freevip`. When a player runs the command, the plugin executes a list of console commands defined in `config.yml`, applies usage limits / cooldowns, and tracks every action in `data.yml`.

Compatible with **Minecraft 1.18.2 and newer** on Spigot, Paper and forks.

## Highlights

- Create commands at runtime with `/cac create <name>` (no plugin.yml edit required)
- Multiple console commands per custom command
- Placeholders: `%player_name%`, `%player_uuid%`, `%player_displayname%`
- Limits & timers:
  - Total usage limit
  - Per-player usage limit
  - Cooldown (seconds)
  - Active lifetime (seconds, after which the command auto-expires)
- Per-command permission (empty = everyone)
- Tab completion for the entire `/cac` admin tree
- Full localization through `languages/<lang>.yml`
  - English (`en.yml`), Azerbaijani (`az.yml`) and Turkish (`tr.yml`) bundled
  - Drop in any other `xx.yml`, set `language: xx`, reload
- Configurable chat prefix **and** console prefix
- Reload-safe: commands are dynamically re-registered into the Bukkit CommandMap
- All data survives server restarts

## Project layout

```
AkifsCustomCommands/
├── pom.xml
├── README.md
├── data.yml.example
└── src/main/
    ├── java/me/akif/customcommands/
    │   ├── AkifsCustomCommands.java          (main entry point)
    │   ├── command/
    │   │   ├── AdminCommand.java             (/cac + tab completion)
    │   │   └── DynamicCommand.java           (per-custom-command Bukkit Command)
    │   ├── manager/
    │   │   ├── CommandManager.java           (registration & execution)
    │   │   ├── ConfigManager.java            (config.yml)
    │   │   ├── DataManager.java              (data.yml)
    │   │   └── LanguageManager.java          (languages/<lang>.yml)
    │   ├── model/
    │   │   └── CustomCommand.java            (POJO model)
    │   └── util/
    │       ├── MessageUtil.java              (color / prefix helper)
    │       ├── PlaceholderUtil.java          (%player_*% replacement)
    │       ├── PluginLogger.java             (configurable console prefix)
    │       └── TimeUtil.java                 (cooldown formatting)
    └── resources/
        ├── plugin.yml
        ├── config.yml
        └── languages/
            ├── en.yml
            ├── az.yml
            └── tr.yml
```

## Build instructions (Maven)

### Requirements
- **JDK 17+** (the bundled Spigot 1.18.2 API requires Java 17)
- Maven **3.8+**
- Internet access for the Spigot/Paper repositories

### Build

From the project root:

```bash
mvn clean package
```

The artifact is produced at:

```
target/AkifsCustomCommands-1.0.0.jar
```

> If `mvn clean package` cannot resolve `org.spigotmc:spigot-api`, either run BuildTools once (recommended on Windows: download `BuildTools.jar` from the SpigotMC site, then `java -jar BuildTools.jar --rev 1.18.2`) or replace the dependency in `pom.xml` with Paper:
>
> ```xml
> <dependency>
>     <groupId>io.papermc.paper</groupId>
>     <artifactId>paper-api</artifactId>
>     <version>1.20.4-R0.1-SNAPSHOT</version>
>     <scope>provided</scope>
> </dependency>
> ```
>
> The `papermc-repo` repository is already declared in `pom.xml`.

### Install

1. Copy `target/AkifsCustomCommands-1.0.0.jar` into your server's `plugins/` folder.
2. Start the server. The plugin auto-creates:
   - `plugins/AkifsCustomCommands/config.yml`
   - `plugins/AkifsCustomCommands/data.yml`
   - `plugins/AkifsCustomCommands/languages/en.yml`
   - `plugins/AkifsCustomCommands/languages/az.yml`
   - `plugins/AkifsCustomCommands/languages/tr.yml`
3. Edit `config.yml` (prefix, language, custom commands) and run `/cac reload`.

## Customising the prefix

`config.yml` controls everything:

```yaml
prefix: "&a&lAkifsCustomCommands &8» "
use-prefix-in-console: true
```

- `prefix` is prepended to every chat message AND every console log line emitted by the plugin.
- `use-prefix-in-console: true` replaces Bukkit's default `[AkifsCustomCommands]` tag with your prefix. Set it to `false` if you'd rather keep the standard Java logger output.

## Adding a translation

1. Copy `plugins/AkifsCustomCommands/languages/en.yml` to e.g. `de.yml`.
2. Translate every value (keys must stay the same).
3. Set `language: de` in `config.yml`.
4. Run `/cac reload`.

The `LanguageManager` falls back to `en.yml` for any key missing from the active translation, so partial translations are safe.

## Usage example

Create a free-VIP campaign:

```
/cac create freevip
/cac addcmd freevip lp user %player_name% parent addtemp vip 7d
/cac addcmd freevip eco give %player_name% 1000
/cac addcmd freevip broadcast &a%player_name% earned a free VIP rank!
/cac setlimit freevip 10
/cac setplayerlimit freevip 1
/cac setcooldown freevip 86400
/cac setduration freevip 86400
/cac setperm freevip myserver.freevip
```

Players run `/freevip` and, if every check passes, the three console commands are executed and `data.yml` is updated.

## `/cac` admin reference

| Command | Description |
| --- | --- |
| `/cac reload` | Reload `config.yml`, the active language file and `data.yml` |
| `/cac list` | List every custom command and its status |
| `/cac info <command>` | Detailed info: enabled, permission, limits, cooldown, lifetime, console commands |
| `/cac create <command>` | Create a new custom command |
| `/cac delete <command>` | Delete a custom command (also clears its data) |
| `/cac enable <command>` | Enable a command |
| `/cac disable <command>` | Disable a command |
| `/cac reset <command>` | Reset all usage data for the command |
| `/cac resetplayer <player> <command>` | Reset a single player's usage |
| `/cac addcmd <command> <console-cmd>` | Append a console command |
| `/cac removecmd <command> <index>` | Remove a console command (0-based index) |
| `/cac setlimit <command> <amount>` | Total usage limit (`-1` = unlimited) |
| `/cac setplayerlimit <command> <amount>` | Per-player limit (`-1` = unlimited) |
| `/cac setcooldown <command> <seconds>` | Cooldown in seconds (`0` disables it) |
| `/cac setduration <command> <seconds>` | Active lifetime in seconds (`0` = forever) |
| `/cac setperm <command> [permission]` | Set or clear the required permission |

Aliases: `/akifscustomcommands`, `/customcommands`, `/customakifscommands`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `akifscustomcommands.admin` | OP | Access to `/cac` and its sub-commands |
| `<your.permission>` *(optional, per-command)* | none | Required to use a specific custom command |

When a custom command's `permission` field is empty in `config.yml`, every player can run it.

## Console log example

With the default config:

```
[12:00:00] [Server thread/INFO]: [AkifsCustomCommands »] Akif used /freevip
[12:00:00] [Server thread/INFO]: [AkifsCustomCommands »] Executed console command: lp user Akif parent addtemp vip 7d
[12:00:00] [Server thread/INFO]: [AkifsCustomCommands »] Executed console command: eco give Akif 1000
```

## Troubleshooting

- **A custom command name collides with another plugin** — use the `akifscc:` prefix (e.g. `/akifscc:tp`) or change the name. The plugin logs a notice when this happens.
- **`updateCommands` warning on old forks** — safely ignored; modern Bukkit clients still get the new tab completion when reconnecting.
- **`mvn clean package` fails to resolve `spigot-api`** — see the Build section for the Paper alternative.

## License

Free to modify and redistribute. Author: **Akif**.
