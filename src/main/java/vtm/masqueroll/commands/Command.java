package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public sealed interface Command permits
    HelpCommand,
    RollCommand,
    CharacterCommand,
    DeleteCharacterCommand,
    SetCommand,
    MacroCommand,
    RemoveMacroCommand,
    DamageCommand,
    HealCommand,
    RestoreCommand,
    RouseCommand,
    MystatsCommand {

    default boolean matchesMessage(String content) {
        return false;
    }

    default void handleMessage(MessageReceivedEvent event, String content) {
    }

    default boolean matchesSlash(String name) {
        return false;
    }

    default void handleSlash(SlashCommandInteractionEvent event) {
    }

    default boolean handleButton(ButtonInteractionEvent event) {
        return false;
    }
}
