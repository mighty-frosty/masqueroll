package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record MacroCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.MACRO.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.MACRO.prefixCommand().length()).trim();
        String[] parts = args.split("=", 2);
        if (parts.length != 2) {
            event.getChannel().sendMessage(BotCommand.MACRO.usage()).queue();
            return;
        }

        String macroName = parts[0].trim();
        String formula = parts[1].trim();
        if (macroName.isEmpty() || formula.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.MACRO.usage()).queue();
            return;
        }

        context.characterSheetService().updateMacro(
            event.getGuild(),
            event.getAuthor().getId(),
            macroName,
            formula,
            sheet -> event.getChannel().sendMessage("Updated macro `" + macroName + "`.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.MACRO.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        String macroName = event.getOption("name").getAsString();
        String formula = event.getOption("formula").getAsString();
        context.characterSheetService().updateMacro(
            event.getGuild(),
            event.getUser().getId(),
            macroName,
            formula,
            sheet -> event.reply("Updated macro `" + macroName + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }
}
