package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;

public record SetCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.SET.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.SET.prefixCommand().length()).trim();
        String[] parts = args.split("\\s+", 3);
        if (parts.length < 1 || args.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.SET.usage()).queue();
            return;
        }

        if (parts[0].equalsIgnoreCase("image")) {
            String imageUrl = args.length() > parts[0].length()
                ? args.substring(parts[0].length()).trim()
                : "";
            if (imageUrl.startsWith("=")) {
                imageUrl = imageUrl.substring(1).trim();
            }
            if (imageUrl.isEmpty() && !event.getMessage().getAttachments().isEmpty()) {
                imageUrl = event.getMessage().getAttachments().getFirst().getUrl();
            }
            if (imageUrl.isEmpty()) {
                event.getChannel().sendMessage("Usage: `!set image <url>` or attach an image to `!set image`.").queue();
                return;
            }

            context.characterSheetService().updateImage(
                event.getGuild(),
                event.getAuthor().getId(),
                imageUrl,
                sheet -> event.getChannel().sendMessage("Updated `image`.").queue(),
                error -> event.getChannel().sendMessage(error).queue()
            );
            return;
        }

        if (parts.length != 2) {
            event.getChannel().sendMessage(BotCommand.SET.usage()).queue();
            return;
        }

        try {
            int value = Integer.parseInt(parts[1]);
            context.characterSheetService().updateStat(
                event.getGuild(),
                event.getAuthor().getId(),
                parts[0],
                value,
                sheet -> event.getChannel().sendMessage("Updated `" + parts[0] + "` to `" + value + "`.").queue(),
                error -> event.getChannel().sendMessage(error).queue()
            );
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessage("Stat values must be whole numbers.").queue();
        }
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.SET.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        String stat = event.getOption("stat").getAsString();
        int value = event.getOption("value").getAsInt();
        context.characterSheetService().updateStat(
            event.getGuild(),
            event.getUser().getId(),
            stat,
            value,
            sheet -> event.reply("Updated `" + stat + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }
}
