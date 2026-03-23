package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record CharacterCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.equalsIgnoreCase(BotCommand.CHARACTER.prefixCommand())
            || content.startsWith(BotCommand.CHARACTER.prefixCommand() + " ");
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.CHARACTER.prefixCommand().length()).trim();
        String displayName = args.isEmpty()
            ? event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName()
            : args;
        String imageUrl = event.getMessage().getAttachments().isEmpty()
            ? null
            : event.getMessage().getAttachments().getFirst().getUrl();

        context.characterSheetService().createSheet(
            event.getGuild(),
            event.getAuthor(),
            displayName,
            imageUrl,
            sheet -> event.getChannel().sendMessage(context.buildSheetTemplatePrompt(event.getAuthor(), displayName, imageUrl)).queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.CHARACTER.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        String displayName = event.getOption("name") != null
            ? event.getOption("name").getAsString()
            : event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        String imageUrl = event.getOption("image") != null
            ? event.getOption("image").getAsAttachment().getUrl()
            : null;

        context.characterSheetService().createSheet(
            event.getGuild(),
            event.getUser(),
            displayName,
            imageUrl,
            sheet -> event.reply(context.buildSheetTemplatePrompt(event.getUser(), displayName, imageUrl)).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }
}
