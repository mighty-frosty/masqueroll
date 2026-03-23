package vtm.masqueroll.dice;

public enum RerollType {
    FAILED_NORMAL("reroll:fail:", "Reroll up to 3", "v5fail", "\uD83D\uDD04"),
    SEARCH_CRIT("reroll:critfish:", "Fish for crit", "v5success", "\u2728"),
    BREAK_MESSY_CRIT("reroll:redcrit:", "Break messy crit", "v5redcrit", "\uD83D\uDD34");

    private final String componentPrefix;
    private final String buttonLabel;
    private final String guildEmojiName;
    private final String fallbackEmoji;

    RerollType(String componentPrefix, String buttonLabel, String guildEmojiName, String fallbackEmoji) {
        this.componentPrefix = componentPrefix;
        this.buttonLabel = buttonLabel;
        this.guildEmojiName = guildEmojiName;
        this.fallbackEmoji = fallbackEmoji;
    }

    public String componentPrefix() {
        return componentPrefix;
    }

    public String buttonLabel() {
        return buttonLabel;
    }

    public String guildEmojiName() {
        return guildEmojiName;
    }

    public String fallbackEmoji() {
        return fallbackEmoji;
    }

    public static RerollType fromComponentId(String componentId) {
        for (RerollType type : values()) {
            if (componentId.startsWith(type.componentPrefix)) {
                return type;
            }
        }
        return null;
    }
}
