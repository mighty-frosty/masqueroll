package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record DeleteCharacterCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.DELETE_CHARACTER.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String characterName = content.substring(BotCommand.DELETE_CHARACTER.prefixCommand().length()).trim();
        if (characterName.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.DELETE_CHARACTER.usage()).queue();
            return;
        }

        context.characterSheetService().deleteSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            characterName,
            success -> event.getChannel().sendMessage("Deleted character `" + characterName + "`.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.DELETE_CHARACTER.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        String characterName = event.getOption("name").getAsString();
        context.characterSheetService().deleteSheet(
            event.getGuild(),
            event.getUser().getId(),
            characterName,
            success -> event.reply("Deleted character `" + characterName + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }
}
