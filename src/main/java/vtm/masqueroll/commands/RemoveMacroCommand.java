package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record RemoveMacroCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.REMOVE_MACRO.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String macroName = content.substring(BotCommand.REMOVE_MACRO.prefixCommand().length()).trim();
        if (macroName.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.REMOVE_MACRO.usage()).queue();
            return;
        }

        context.characterSheetService().removeMacro(
            event.getGuild(),
            event.getAuthor().getId(),
            macroName,
            sheet -> event.getChannel().sendMessage("Removed macro `" + macroName + "`.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.REMOVE_MACRO.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        String macroName = event.getOption("name").getAsString();
        context.characterSheetService().removeMacro(
            event.getGuild(),
            event.getUser().getId(),
            macroName,
            sheet -> event.reply("Removed macro `" + macroName + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }
}
