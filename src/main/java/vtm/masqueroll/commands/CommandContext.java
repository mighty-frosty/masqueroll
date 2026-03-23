package vtm.masqueroll.commands;

import net.dv8tion.jda.api.entities.User;
import vtm.masqueroll.CharacterSheetService;
import vtm.masqueroll.dice.DiceDisplayConfig;
import vtm.masqueroll.dice.RollImageRenderer;
import vtm.masqueroll.dice.StatusBannerRenderer;

public record CommandContext(
    CharacterSheetService characterSheetService,
    DiceDisplayConfig displayConfig,
    RollImageRenderer imageRenderer,
    StatusBannerRenderer statusBannerRenderer
) {

    private static final String SHEET_TEMPLATE_MARKER = "[sheet-template]";

    public String sheetTemplateMarker() {
        return SHEET_TEMPLATE_MARKER;
    }

    public String buildSheetTemplatePrompt(User user, String displayName, String imageUrl) {
        return "Created your character sheet in #character-sheets.\n"
            + "Reply to this message with your filled sheet template and I'll update it in one go.\n"
            + SHEET_TEMPLATE_MARKER + "\n```text\n"
            + characterSheetService.buildTemplate(user, displayName, imageUrl)
            + "\n```";
    }

    public String buildHelpMessage() {
        return """
            **Masqueroll Commands**
            `!help` Show this help message
            `!character Michael` Create your sheet
            `!set <stat> <value>` Update one stat
            `!macro <name> = <formula>` Save a macro
            `!removemacro <name>` Remove a macro
            `!mystats` Show your current sheet values
            
            `!v 5` Roll a pool using sheet hunger
            `!v 6 2 3` Roll pool 6, hunger 2, difficulty 3
            `!v wits + awareness` Roll from sheet stats
            `!v auspex` Roll a saved macro
            `!v auspex +2` Roll a macro with bonus/malus
            
            `!rouse` Make a rouse check
            `!damage superficial 2` Add health damage
            `!damage aggravated 1` Add aggravated damage
            `!heal` Heal 1 superficial damage with a rouse check
            `!restore` Restore health and willpower
            `!restore health` Restore health only
            `!restore health 2` Restore 2 health boxes
            `!restore willpower` Restore willpower only
            `!restore willpower 3` Restore 3 willpower
            
            Rerolls spend `1` willpower each.
            Reply to the sheet template message to paste a full sheet in one go.
            """;
    }
}
