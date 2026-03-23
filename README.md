# Masqueroll

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

- Java 25
- Maven
- JDA

## Setup

1. Install Java 25 and Maven.
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

Use `!help` or `/help` for a quick in-Discord summary.

### Rolling

- `!v <pool>`
- `!v <pool> <hunger>`
- `!v <pool> <hunger> <difficulty>`
- `!v <stat> + <stat>`
- `!v <macro>`
- `!v <macro> +2`
- `/v pool:<number> hunger:<number> difficulty:<number>`

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
- `/character`
- `/character name:Michael image:<attachment>`

If you attach an image to `!character Michael`, the bot stores it on the sheet and uses it later as the roll thumbnail.

After creating a character, the bot also sends you a full sheet template. Reply to that bot message with your filled-out sheet and it will update the entire character in one go.

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
!set willpower 5
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

### Health and willpower

- `!damage superficial`
- `!damage aggravated`
- `!damage superficial 2`
- `/damage type:superficial amount:2`
- `/damage type:aggravated amount:1`
- `!heal`
- `/heal`
- `!restore`
- `!restore health`
- `!restore health 2`
- `!restore willpower`
- `!restore willpower 3`
- `/restore target:all`

Current behavior:

- `willpower` is tracked on the sheet and each reroll spends 1 willpower
- `health_superficial` and `health_aggravated` are tracked on the sheet
- `!damage` adds the requested amount of health damage
- `!heal` heals 1 superficial health damage and performs a rouse check
- if the heal rouse check fails, hunger is increased by 1
- `!mystats` shows emoji trackers for hunger, health, and willpower
- `!restore` restores health and willpower
- `!restore health <number>` restores that many health boxes
- `!restore willpower <number>` restores that many willpower

## Character sheet format

The bot creates a default sheet automatically, but the stored data looks like this:

```text
user = @stayfrosty2663
name = Michael
image = https://...
hunger = 2
health_superficial = 0
health_aggravated = 0
willpower = 5

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

You can also paste the whole sheet at once by replying to the template message the bot sends after `!character`.

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

This produces a runnable fat jar at:

```text
target/masqueroll.jar
```

## Docker Deploy

You can deploy automatically to your Oracle VM with the workflow in:

- `.github/workflows/deploy-oracle.yml`

It will:

- build a Docker image
- push it to GitHub Container Registry (`ghcr.io`)
- SSH into your Oracle VM
- pull the latest image
- restart the `masqueroll` container with your bot token

### GitHub secrets

Add these repository secrets:

- `ORACLE_HOST`
  - your VM public IP, for example `145.241.216.215`
- `ORACLE_USER`
  - usually `opc`
- `ORACLE_SSH_KEY`
  - the full private SSH key contents
- `DISCORD_BOT_TOKEN`
  - your Discord bot token
- `GHCR_USERNAME`
  - the GitHub username that can read the package
- `GHCR_TOKEN`
  - a GitHub token or PAT with package read access on the VM deploy side

### Oracle VM setup

1. Install Docker on the VM.
2. Make sure your SSH user can run Docker commands.

```bash
sudo dnf install -y dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker opc
```

3. Log out and back in once so the `docker` group applies to `opc`.
4. After that, every push to `main` will deploy automatically.

## Notes

- Prefix commands require `MESSAGE CONTENT INTENT`.
- Slash commands may take a short moment to appear after startup.
- This bot is a good fit for a free Oracle Cloud VM if you want 24/7 hosting.
