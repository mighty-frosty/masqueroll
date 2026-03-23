package vtm.masqueroll;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@RequiredArgsConstructor
@Accessors(fluent = true)
public enum BotCommand {
    HELP("help", "!help", "Show a summary of bot commands.", null),
    ROLL("v", "!v", "Roll Vampire: The Masquerade dice.", "Usage: `!v <pool> [hunger] [difficulty]`"),
    ROUSE("rouse", "!rouse", "Make a rouse check.", null),
    CHARACTER("character", "!character", "Create your bot-managed character sheet.", null),
    SET("set", "!set", "Set a stat on your bot-managed character sheet.", "Usage: `!set <stat> <value>`"),
    MACRO("macro", "!macro", "Set a macro on your bot-managed character sheet.", "Usage: `!macro <name> = <formula>`"),
    REMOVE_MACRO("removemacro", "!removemacro", "Remove a macro from your bot-managed character sheet.", "Usage: `!removemacro <name>`"),
    DAMAGE("damage", "!damage", "Apply superficial or aggravated damage.", "Usage: `!damage <superficial|aggravated>`"),
    HEAL("heal", "!heal", "Heal one superficial health damage with a rouse check.", null),
    RESTORE("restore", "!restore", "Restore health and/or willpower.", "Usage: `!restore [all|health|willpower]`"),
    MY_STATS("mystats", "!mystats", "Show your saved roll settings.", null);

    private final String slashName;
    private final String prefixCommand;
    private final String description;
    private final String usage;
}
