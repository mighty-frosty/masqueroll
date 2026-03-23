# Abbioccatore

A Java Discord bot for Vampire: The Masquerade V5 dice rolls, rerolls, rouse checks, and bot-managed character sheets.

## Features

- V5 roll support with normal dice and hunger dice
- Success, critical, messy critical, and bestial failure detection
- Visual roll output using the dice images in `src/images`
- Optional custom server emojis for reroll buttons
- Player-specific character sheets stored directly in Discord
- Stats and macros resolved from a character sheet
- Automatic hunger tracking on failed rouse checks
- Prefix commands and slash commands

## Tech stack

- Java 21
- Maven
- JDA

## Setup

1. Install Java 21 and Maven.
2. Create `.env` from the example:

```powershell
copy .env.example .env
```

3. Put your Discord bot token in `.env`:

```text
DISCORD_BOT_TOKEN=your-token-here
```

4. Run the bot:

```powershell
mvn exec:java
```

## Discord setup

### Bot permissions

Make sure the bot has at least:

- `View Channels`
- `Send Messages`
- `Embed Links`
- `Attach Files`
- `Read Message History`
- `Use Slash Commands`

Enable `MESSAGE CONTENT INTENT` in the Discord Developer Portal if you want prefix commands like `!v`.

### Character sheets forum

Create a forum channel named:

```text
character-sheets
```

The bot uses that forum as the source of truth for player sheets.

When a player creates a sheet, the bot creates a forum post for that character and keeps the sheet data inside the post message.

## Commands

### Rolling

- `!v <pool>`
- `!v <pool> <hunger>`
- `!v <pool> <hunger> <difficulty>`
- `!v <stat> + <stat>`
- `!v <macro>`
- `!v <macro> +2`
- `/roll pool:<number> hunger:<number> difficulty:<number>`

Examples:

```text
!v 5
!v 6 2
!v 6 2 3
!v wits + awareness
!v auspex
!v auspex +2
```

### Character sheets

- `!character`
- `!character Michael`
- `!characters Michael`
- `/character`
- `/character name:Michael image:<attachment>`

If you attach an image to `!character Michael`, the bot stores it on the sheet and uses it later as the roll thumbnail.

### Stats

- `!set <stat> <value>`
- `/set stat:<name> value:<number>`
- `!mystats`
- `/mystats`

Examples:

```text
!set wits 3
!set awareness 3
!set hunger 2
```

### Macros

- `!macro <name> = <formula>`
- `/macro name:<name> formula:<formula>`
- `!removemacro <name>`
- `/removemacro name:<name>`

Examples:

```text
!macro auspex = wits + awareness + 3
!removemacro auspex
```

### Rouse checks

- `!rouse`
- `/rouse`

Rouse behavior:

- `6-10` = success
- `1-5` = failure
- on failure, hunger is increased automatically on the bot-owned sheet

## Character sheet format

The bot creates a default sheet automatically, but the stored data looks like this:

```text
user = @stayfrosty2663
name = Michael
image = https://...
hunger = 2

strength = 1
dexterity = 2
stamina = 3
charisma = 4
manipulation = 2
composure = 2
intelligence = 4
wits = 3
resolve = 3

athletics = 3
brawl = 0
craft = 1
drive = 1
firearms = 2
larceny = 0
melee = 0
stealth = 2
survival = 1

animalken = 1
etiquette = 0
insight = 3
intimidation = 0
leadership = 0
performance = 2
persuasion = 3
streetwise = 1
subterfuge = 1

academics = 0
awareness = 3
finance = 0
investigation = 1
medicine = 0
occult = 3
politics = 0
science = 0
technology = 0

---- MACRO ----
auspex = wits + awareness + 3
```

Spaces around `=` are fine.

## Rerolls

The bot can add reroll buttons to a roll:

- reroll up to 3 failed normal dice
- reroll up to 3 normal successes to fish for a crit
- reroll matching normal critical dice to break a messy critical

Hunger dice are never rerolled by these mechanics.

## Visual output

- If `src/images` contains the supplied dice PNG assets, the bot renders roll images automatically.
- Hunger `1`s use the bestial-failure art.
- Normal dice and hunger dice are shown on separate rows.
- Messy criticals and bestial failures are highlighted clearly.
- The character image from the sheet is shown as the embed thumbnail.

If you add fonts in `src/fonts`, the renderer will use these when present:

- `CinzelDecorative-Bold.ttf`
- `CormorantSC-Bold.ttf`
- `Cinzel-Bold.ttf`

## Build

```powershell
mvn package
```

## Notes

- Prefix commands require `MESSAGE CONTENT INTENT`.
- Slash commands may take a short moment to appear after startup.
- This bot is a good fit for a free Oracle Cloud VM if you want 24/7 hosting.
