package vtm.masqueroll;

public enum BotCommand {
    ROLL("roll", "!v", "Roll Vampire: The Masquerade dice.", "Usage: `!v <pool> [hunger] [difficulty]`"),
    ROUSE("rouse", "!rouse", "Make a rouse check.", null),
    CHARACTER("character", "!character", "Create your bot-managed character sheet.", null),
    SET("set", "!set", "Set a stat on your bot-managed character sheet.", "Usage: `!set <stat> <value>`"),
    MACRO("macro", "!macro", "Set a macro on your bot-managed character sheet.", "Usage: `!macro <name> = <formula>`"),
    REMOVE_MACRO("removemacro", "!removemacro", "Remove a macro from your bot-managed character sheet.", "Usage: `!removemacro <name>`"),
    MY_STATS("mystats", "!mystats", "Show your saved roll settings.", null);

    private final String slashName;
    private final String prefixCommand;
    private final String description;
    private final String usage;

    BotCommand(String slashName, String prefixCommand, String description, String usage) {
        this.slashName = slashName;
        this.prefixCommand = prefixCommand;
        this.description = description;
        this.usage = usage;
    }

    public String slashName() {
        return slashName;
    }

    public String prefixCommand() {
        return prefixCommand;
    }

    public String description() {
        return description;
    }

    public String usage() {
        return usage;
    }
}
