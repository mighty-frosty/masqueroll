# Abbioccatore

A lightweight Java Discord bot for Vampire: The Masquerade V5 dice rolls.

## What it does

The bot supports both:

- `!roll <pool> [hunger] [difficulty]`
- `/roll pool:<number> hunger:<number> difficulty:<number>`

It uses the common V5 dice rules:

- `6-9` counts as 1 success
- `10` counts as 1 success, and each pair of `10s` adds 2 extra successes
- hunger dice can trigger a messy critical
- a failed roll with any hunger `1s` becomes a bestial failure

## Tech stack

- Java 21
- Maven
- JDA

## Quick start

1. Install Java 21 and Maven.
2. Create your env file:

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

## Build

```powershell
mvn package
```

## Example

```text
!roll 7 2 4
```

That means:

- `7` total dice
- `2` hunger dice
- `4` difficulty

## Notes

- Enable the `MESSAGE CONTENT INTENT` in the Discord developer portal if you want the `!roll` command.
- Slash commands may take a short moment to appear the first time the bot connects.
- Hunger dice are shown in bold in the response embed.
- For free hosting, this Java bot can run fine on an Oracle Cloud Always Free VM.
