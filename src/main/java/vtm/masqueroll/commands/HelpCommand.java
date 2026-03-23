package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record HelpCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.equalsIgnoreCase(BotCommand.HELP.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        event.getChannel().sendMessage(context.buildHelpMessage()).queue();
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.HELP.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        event.reply(context.buildHelpMessage()).setEphemeral(true).queue();
    }
}
