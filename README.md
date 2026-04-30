# AkifsCustomCommands

Spigot/Paper plugin that lets server owners create their own reward commands such as `/freevip`. When a player runs the command, the plugin executes a list of console commands defined in `commands.yml`, applies usage limits / cooldowns, broadcasts a public message and tracks every action in `data.yml`.

Compatible with **Minecraft 1.18.2 → 1.21.x** on Spigot, Paper and forks.

## Highlights

- **Dynamic commands** — create any custom command at runtime with `/cac create <name>`. No `plugin.yml` edits, no restart, no precondition.
- **Multi-step rewards** — every custom command runs an arbitrary list of console commands as the server console.
- **Public broadcasts** — define a `broadcast:` list per command and every online player sees a message when someone uses it.
- **Discord webhook logging** — optionally post a localized embed to Discord every time a player uses a command. Includes the player's skin head as the thumbnail, the broadcast text (color-stripped), and the executed console commands.
- **Limits & timers**
  - Total usage limit
  - Per-player usage limit
  - Cooldown per player
  - Active lifetime (auto-expires after the configured period)
- **Per-command permission** (empty = everyone)
- **Modern colors**
  - Legacy codes: `&a`, `&c`, `&l`, `&n` …
  - Hex codes: `&#RRGGBB` (1.16+)
  - **Gradients**: `<gradient:#A:#B>text</gradient>` with full format-code support inside
- **Human-readable durations** — `30s`, `5m`, `12h`, `1d`, `7d`, `1w`, `1d12h` … plus plain seconds for backwards compatibility
- **Full localization** — drop a YAML into `languages/`. Bundled defaults: English (`en.yml`), Azerbaijani (`az.yml`), Turkish (`tr.yml`).
- **Configurable chat AND console prefix** — both honor the same color/gradient syntax
- **Reload-safe** — commands are dynamically (un)registered into the Bukkit CommandMap
- **Persistent data** — usage stats survive restarts via `data.yml`
- **Tab completion everywhere**

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
    │   │   ├── ConfigManager.java            (config.yml + commands.yml)
    │   │   ├── DataManager.java              (data.yml)
    │   │   └── LanguageManager.java          (languages/<lang>.yml)
    │   ├── model/
    │   │   └── CustomCommand.java            (POJO model)
    │   └── util/
    │       ├── DiscordWebhook.java           (async embed dispatch)
    │       ├── MessageUtil.java              (gradient + hex + legacy color)
    │       ├── PlaceholderUtil.java          (%player_*% replacement)
    │       ├── PluginLogger.java             (configurable console prefix)
    │       └── TimeUtil.java                 (durations + cooldown formatting)
    └── resources/
        ├── plugin.yml
        ├── config.yml          (global settings)
        ├── commands.yml        (custom command definitions)
        └── languages/
            ├── en.yml
            ├── az.yml
            └── tr.yml
```

## Build instructions (Maven)

### Requirements
- **JDK 17+** (the bundled Spigot 1.18.2 API requires Java 17 to compile; the resulting JAR runs on Java 17/21)
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

> If `mvn clean package` cannot resolve `org.spigotmc:spigot-api`, either run BuildTools once (download `BuildTools.jar` from SpigotMC, then `java -jar BuildTools.jar --rev 1.18.2`) or replace the dependency in `pom.xml` with Paper:
>
> ```xml
> <dependency>
>     <groupId>io.papermc.paper</groupId>
>     <artifactId>paper-api</artifactId>
>     <version>1.21.4-R0.1-SNAPSHOT</version>
>     <scope>provided</scope>
> </dependency>
> ```
>
> The `papermc-repo` repository is already declared in `pom.xml`.

### Install

1. Copy `target/AkifsCustomCommands-1.0.0.jar` into your server's `plugins/` folder.
2. Start the server. The plugin auto-creates:
   - `plugins/AkifsCustomCommands/config.yml`         (global settings)
   - `plugins/AkifsCustomCommands/commands.yml`       (custom command definitions)
   - `plugins/AkifsCustomCommands/data.yml`           (usage stats)
   - `plugins/AkifsCustomCommands/languages/en.yml`
   - `plugins/AkifsCustomCommands/languages/az.yml`
   - `plugins/AkifsCustomCommands/languages/tr.yml`
3. Edit `config.yml` (prefix, language, console toggle, Discord webhook) and `commands.yml` (your custom commands), then run `/cac reload`.

> **Upgrading from an older version?** If your existing `config.yml` still has a `commands:` section, the plugin migrates every entry into `commands.yml` automatically the first time it loads — no manual copy needed.

## Quick start

```
/cac create freevip
/cac addcmd freevip lp user %player_name% parent addtemp vip 7d
/cac addcmd freevip eco give %player_name% 1000
/cac addbcast freevip <gradient:#3FFF5C:#5CC8FF>&l%player_name%</gradient> &#A0A0A0earned &#FFE45C7 days of VIP&#A0A0A0!
/cac setlimit freevip 10
/cac setplayerlimit freevip 1
/cac setcooldown freevip 1d
/cac setduration freevip 7d
/cac setperm freevip myserver.freevip
```

Players run `/freevip` and, if every check passes, the two console commands are executed, the broadcast message goes out and `data.yml` is updated.

## Colors, hex and gradients

Three color systems are supported and processed in this order:

| System | Syntax | Example |
| --- | --- | --- |
| Gradient | `<gradient:#AAAAAA:#BBBBBB>text</gradient>` | `<gradient:#3FFF5C:#5CC8FF>&lAkifsCustomCommands</gradient>` |
| Hex | `&#RRGGBB` | `&#FFE45CGold text` |
| Legacy | `&` codes | `&a&lGreen bold` |

Format codes (`&l`, `&n`, `&o`, `&m`, `&k`, `&r`) are preserved per character inside gradient blocks, so things like `<gradient:#A:#B>&lBold gradient</gradient>` work as expected.

A few drop-in prefix examples:

```yaml
prefix: "<gradient:#3FFF5C:#5CC8FF>&lAkifsCustomCommands</gradient> &8» &r"   # default
prefix: "<gradient:#FFE45C:#FF6B6B>&lShop</gradient> &8• &r"                  # coral
prefix: "<gradient:#7C4DFF:#FF6BFF>&lVIP</gradient> &8› &r"                   # purple
prefix: "<gradient:#00E5FF:#FF00C8>&lNeon</gradient> &8» &r"                  # cyberpunk
prefix: "&#3FFF5C&lAkifsCustomCommands &8» &r"                                # plain hex
```

## Human-readable durations

`/cac setcooldown` and `/cac setduration` accept tokens like `30s`, `5m`, `12h`, `1d`, `7d`, `1w`, plus combined forms (`1d12h`, `2h30m`, `1w2d3h`). Plain numbers are still treated as seconds.

```
/cac setcooldown freevip 24h           # = 86400 seconds
/cac setcooldown freevip 1d            # = 86400 seconds
/cac setcooldown freevip 1d12h         # = 129600 seconds
/cac setduration  freevip 7d           # one week
/cac setcooldown freevip 0             # disable cooldown
```

Tab completion suggests `30s`, `1m`, `5m`, `30m`, `1h`, `12h`, `1d`, `7d`, `30d`.

| Unit | Multiplier |
| --- | --- |
| `s` | 1 second |
| `m` | 60 seconds |
| `h` | 3600 seconds |
| `d` | 86400 seconds |
| `w` | 604800 seconds |

## Public broadcast on success

Every custom command can have a `broadcast:` list. Each line is sent to every online player (and mirrored to the console) the moment a player successfully runs the command. Color codes, hex codes, gradients and placeholders all work, and an empty list keeps the command silent.

```yaml
freevip:
  broadcast:
    - "&8&m                                                 "
    - "&r <gradient:#3FFF5C:#5CC8FF>&l%player_name%</gradient> &#A0A0A0used &#FFE45C/%command% &#A0A0A0and earned <gradient:#FFE45C:#FF6B6B>&l7 days of VIP</gradient>&#A0A0A0!"
    - "&r &#A0A0A0Type &#FFE45C/%command% &#A0A0A0to grab one too while it lasts."
    - "&8&m                                                 "
```

Manage broadcast lines without editing YAML:

```
/cac addbcast freevip <gradient:#FFE45C:#FF6B6B>&l%player_name%</gradient> &#A0A0A0just got 7-day VIP!
/cac removebcast freevip 0
/cac clearbcast freevip
```

`/cac info <command>` lists every configured broadcast line with its index.

## Discord webhook logging

The plugin can post a rich embed to a Discord webhook every time a player successfully uses a custom command. The embed includes:

- Thumbnail with the player's skin head (mc-heads.net by default)
- Player name, command name, timestamp
- **Remaining uses** (only when a limit is set) — shows server-wide and per-player remaining counts, e.g. `Server-wide: 7 of 10 left (3 used)` and `Akif: 1 of 1 left`
- The broadcast text **with all color codes / hex / gradients stripped** (plain text)
- The console commands that were executed (formatted as a code block, e.g. `/lp user Akif parent addtemp vip 7d`)

All embed labels (title, field names, footer) come from the active language file, so the message is delivered in the same language you configured in `config.yml`. Bundled translations cover **English**, **Azerbaijani** and **Turkish** out of the box.

### Setup

1. In Discord: **Server Settings → Integrations → Webhooks → New Webhook → Copy URL**.
2. Paste it into `config.yml`:

   ```yaml
   discord:
     enabled: true
     webhook-url: "https://discord.com/api/webhooks/..."
     username: "AkifsCustomCommands"
     avatar-url: ""
     thumbnail-url: "https://mc-heads.net/avatar/%player_uuid%/128"
     embed-color: "#3FFF5C"
     timestamp: true
   ```

3. Run `/cac reload`.
4. Verify with `/cac testwebhook freevip [player]` — sends the same embed without actually executing any rewards.

### What appears in Discord

A typical embed body looks like:

```
Custom command used
**Akif** just used `/freevip`.

Player              Command            Used at
Akif                /freevip           2026-04-30 15:05:11 AZT

Remaining uses
Server-wide: **7** of 10 left (3 used)
Akif: **1** of 1 left

Broadcast
Akif used /freevip and earned 7 days of VIP!
Type /freevip to grab one too while it lasts.

Executed commands
/lp user Akif parent addtemp vip 7d
/eco give Akif 1000

— AkifsCustomCommands · 2026-04-30 11:05 UTC
```

The "Remaining uses" field is **only** added when at least one of the limits is greater than zero. With `usage-limit-total: -1` and `usage-limit-per-player: -1` (the default for unlimited commands) the field disappears entirely. The two lines are independent — set only the total limit and you get only the server-wide line, set only the per-player limit and you get only that.

### Translatable embed strings

Located in `languages/<lang>.yml` under the `discord.embed` key. Set any label to an empty string (`""`) to hide that field from the embed. Example (English):

```yaml
discord:
  embed:
    title: "Custom command used"
    description: "**%player_name%** just used `/%command%`."
    field-player: "Player"
    field-command: "Command"
    field-time: "Used at"
    field-broadcast: "Broadcast"
    field-commands: "Executed commands"
    field-remaining: "Remaining uses"
    remaining-total: "Server-wide: **%remaining%** of %limit% left (%used% used)"
    remaining-player: "%player_name%: **%remaining%** of %limit% left"
    footer: "AkifsCustomCommands"
    time-format: "yyyy-MM-dd HH:mm:ss z"
```

Available placeholders:

- General (work in title / description / footer / fields): `%player_name%`, `%player_uuid%`, `%player_displayname%`, `%command%`, `%time%`
- `remaining-total` and `remaining-player` lines also receive: `%limit%`, `%used%`, `%remaining%`

### Customising the head image

The default `thumbnail-url` uses **mc-heads.net** which works on cracked / offline-mode servers too because it accepts both UUID and player name. Some alternatives:

```yaml
thumbnail-url: "https://mc-heads.net/avatar/%player_uuid%/128"           # default
thumbnail-url: "https://mc-heads.net/avatar/%player_name%/128"           # offline-friendly
thumbnail-url: "https://crafatar.com/avatars/%player_uuid%?size=128&overlay=true"
thumbnail-url: "https://minotar.net/helm/%player_name%/128.png"
```

Network calls happen on a background thread so the main server thread is never blocked, even if Discord is slow or the webhook URL is unreachable.

## Customising the prefix

`config.yml` controls everything:

```yaml
prefix: "<gradient:#3FFF5C:#5CC8FF>&lAkifsCustomCommands</gradient> &8» &r"
use-prefix-in-console: true
```

- `prefix` is prepended to every chat message AND every console log line emitted by the plugin.
- `use-prefix-in-console: true` replaces Bukkit's default `[AkifsCustomCommands]` tag with your prefix. Set it to `false` if you'd rather keep the standard Java logger output.

## Adding a translation

1. Copy `plugins/AkifsCustomCommands/languages/en.yml` to e.g. `de.yml`.
2. Translate every value (keys must stay the same).
3. Set `language: de` in `config.yml`.
4. Run `/cac reload`.

The `LanguageManager` falls back to `en.yml` for any key missing from the active translation, so partial translations are safe to ship. Hex and gradient syntax work in language files too.

## Placeholders

| Placeholder | Where it works | Replaced with |
| --- | --- | --- |
| `%player_name%` | console-commands, broadcast, messages | name of the player who ran the command |
| `%player_uuid%` | same | the player's UUID |
| `%player_displayname%` | same | the player's display name |
| `%command%` | broadcast, messages | the custom command name |
| `%time_left%` | the cooldown message only | formatted remaining time, e.g. `1h 23m` |

PlaceholderAPI is **not** required.

## `/cac` admin reference

`/cac` on its own prints a hint that points to `/cac help`. The full panel only appears when you ask for it.

| Command | Description |
| --- | --- |
| `/cac help` | Show the admin panel |
| `/cac reload` | Reload `config.yml`, `commands.yml`, the active language file and `data.yml` |
| `/cac list` | List every custom command and its status |
| `/cac info <command>` | Show details: enabled, permission, limits, cooldown, lifetime, console commands, broadcast lines |
| `/cac create <command>` | Create a new custom command |
| `/cac delete <command>` | Delete a custom command (also clears its data) |
| `/cac enable <command>` | Enable a command |
| `/cac disable <command>` | Disable a command |
| `/cac reset <command>` | Reset all usage data for the command |
| `/cac resetplayer <player> <command>` | Reset a single player's usage |
| `/cac addcmd <command> <console-cmd>` | Append a console command |
| `/cac removecmd <command> <index>` | Remove a console command (0-based index) |
| `/cac addbcast <command> <message>` | Append a public broadcast line |
| `/cac removebcast <command> <index>` | Remove a broadcast line (0-based index) |
| `/cac clearbcast <command>` | Remove every broadcast line (the command becomes silent) |
| `/cac setlimit <command> <amount>` | Total usage limit (`-1` = unlimited) |
| `/cac setplayerlimit <command> <amount>` | Per-player limit (`-1` = unlimited) |
| `/cac setcooldown <command> <time>` | Cooldown — accepts `30s`, `5m`, `1h`, `1d`, `1w`, `0` |
| `/cac setduration <command> <time>` | Active lifetime — same time syntax |
| `/cac setperm <command> [permission]` | Set or clear the required permission |
| `/cac testwebhook <command> [player]` | Send a test Discord embed for the command (uses the sender if `[player]` is omitted) |

Aliases: `/akifscustomcommands`, `/customcommands`, `/customakifscommands`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `akifscustomcommands.admin` | OP | Access to `/cac` and its sub-commands |
| `<your.permission>` *(optional, per-command)* | none | Required to use a specific custom command |

When a custom command's `permission` field is empty in `commands.yml`, every player can run it.

## Console log example

With the default gradient prefix:

```
[12:00:00] [Server thread/INFO]: AkifsCustomCommands » Akif used /freevip
[12:00:00] [Server thread/INFO]: AkifsCustomCommands » Executed console command: lp user Akif parent addtemp vip 7d
[12:00:00] [Server thread/INFO]: AkifsCustomCommands » Executed console command: eco give Akif 1000
```

(Gradient colors render as ANSI escape sequences in the console.)

## Troubleshooting

- **A custom command name collides with another plugin** — use the `akifscc:` prefix (e.g. `/akifscc:tp`) or change the name. The plugin logs a notice when this happens.
- **`updateCommands` warning on old forks** — safely ignored; modern Bukkit clients still get the new tab completion when reconnecting.
- **`mvn clean package` fails to resolve `spigot-api`** — see the Build section for the Paper alternative.
- **My gradient looks like raw `<gradient:...>` text** — make sure your server is on 1.16 or newer. On older servers the gradient block is left as plain text.
- **Hex colors look weird inside a gradient** — gradients are character-by-character interpolation; embedding extra `&#RRGGBB` codes inside a gradient block will be overwritten. Use one or the other for a given span.

## License

Free to modify and redistribute. Author: **Akif**.
