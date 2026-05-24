# BryceModerating

A Discord moderation and server-management bot built with [JDA](https://github.com/DV8FromTheWorld/JDA) (Java 21). It combines slash-command moderation, automated channel rules, audit logging, and a user-managed voice channel system, with SQLite persistence and periodic backups.

## Features

### Moderation
- Slash commands: warn, mute, unmute, timeout, untimeout, ban, unban, kick, purge
- Configurable moderator and admin roles per server
- Role-based mutes with automatic unmute when timed mutes expire
- Ban reason lookup and moderation analytics
- Role management (`/role add`, `/role remove`)

### Channel restrictions
Enforce content rules in text channels (mods and admins are exempt):
- Media with text, media only, screenshot only, text only
- No media, no content, no message

Use `/restrict`, `/unrestrict`, or `/restrict-setup` to configure channels.

### Server logs
Audit-style logging for messages, members, channels, roles, and server events, with member and message caching for lookups.

### Voice channel manager
Users can create and manage temporary voice channels:
- `/setup` — enable the voice channel manager for a server
- `/createvoice`, `/deletevoice`, `/mychannels`, `/activechannels`, `/vcstats`
- `/vchelp` — command help

### Data and reliability
- SQLite database (`modbot.db`) for warnings, settings, voice channels, and analytics
- Auto-save every 15 minutes when data changes
- Backup and restore on startup and shutdown

## Requirements

- **Java 21**
- **Maven 3.6+**
- A [Discord application](https://discord.com/developers/applications) with a bot token

The bot requests all gateway intents and member chunking for logging and moderation. Enable the intents your application needs in the Discord Developer Portal before inviting the bot.

## Setup

### 1. Clone and open the project

```bash
git clone <your-repo-url>
cd BryceModerating
```

The Maven module lives at `BryceModerating/BryceModerating/BryceModerating/`. Open that folder (or the repo root) in your IDE.

### 2. Configure environment variables

Create a `.env` file in the repository root (or next to the Maven module). The bot searches several locations, including the working directory and parent folders.

| Variable | Required | Description |
|----------|----------|-------------|
| `BOT_TOKEN` | Yes | Discord bot token |
| `BOT_STATUS` | No | Activity text (default: `Moderating your favorite servers!`) |
| `BOT_ONLINE_STATUS` | No | One of `ONLINE`, `IDLE`, `DO_NOT_DISTURB`, `INVISIBLE` (default: `IDLE`) |

Example:

```env
BOT_TOKEN=your_bot_token_here
BOT_STATUS=Moderating your favorite servers!
BOT_ONLINE_STATUS=IDLE
```

Do not commit `.env` or your bot token.

### 3. Build

From the repository root:

```bash
mvn -f BryceModerating/BryceModerating/BryceModerating/pom.xml compile
```

### 4. Run

Run the main class from your IDE:

**Main class:** `com.bryce.discord.BryceModeratingBot`

Or from the command line after compiling (classpath must include dependencies from your local Maven repository):

```bash
cd BryceModerating/BryceModerating/BryceModerating
mvn compile
# Then run BryceModeratingBot with compile/runtime classpath in your IDE or a run configuration
```

On first run, the bot initializes the database, loads saved data, registers global slash commands, and starts background tasks (auto-save, backups, unmute checker).

## Initial server configuration

1. Invite the bot with permissions for moderation, roles, channels, and messages as needed.
2. Set moderator and admin roles: `/setmodroles`, `/setadminroles`
3. Set a mute role: `/setmuterole`
4. For voice channels: `/setup` in the target server
5. Use `/help` in Discord for in-bot guidance

Some commands are restricted to server administrators or bot owners (for example `/exportdb` and `/savemoderationsystem`).

## Project structure

```
BryceModerating/                          # Repository root
├── README.md
├── .env                                  # Local config (not committed)
└── BryceModerating/BryceModerating/BryceModerating/
    ├── pom.xml
    └── src/main/java/com/bryce/discord/
        ├── BryceModeratingBot.java       # Entry point
        ├── commands/                     # Slash command handlers
        ├── listeners/                    # Events (moderation, logs, voice)
        ├── services/                     # Data, config, backup, database
        ├── models/                       # Domain records
        ├── cache/                        # User and message caches
        └── utils/
```

## Tech stack

- Java 21, Maven
- JDA 5.1.0
- SQLite (JDBC)
- Jackson, Logback, dotenv-java

## License

Private project — add a license file here if you plan to distribute the bot.
