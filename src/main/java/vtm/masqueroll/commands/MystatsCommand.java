package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record MystatsCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.equalsIgnoreCase(BotCommand.MY_STATS.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            sheet -> event.getChannel().sendMessage(sheet.describe()).queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.MY_STATS.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getUser().getId(),
            sheet -> event.reply(sheet.describe()).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }
}
