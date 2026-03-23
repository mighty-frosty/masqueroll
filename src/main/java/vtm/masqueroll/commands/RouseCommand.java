package vtm.masqueroll.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import vtm.masqueroll.BotCommand;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

public record RouseCommand(CommandContext context) implements Command {

    private static final Color SUCCESS_COLOR = new Color(41, 128, 72);
    private static final Color FAILURE_COLOR = new Color(170, 32, 32);

    @Override
    public boolean matchesMessage(String content) {
        return content.equalsIgnoreCase(BotCommand.ROUSE.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            sheet -> {
                int dieValue = rollRouse();
                if (dieValue < 6) {
                    context.characterSheetService().incrementHunger(
                        event.getGuild(),
                        event.getAuthor().getId(),
                        updatedSheet -> sendMessage(event, dieValue, updatedSheet.imageUrl(), updatedSheet.name(), updatedSheet.hunger()),
                        error -> sendMessage(event, dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())
                    );
                } else {
                    sendMessage(event, dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger());
                }
            },
            error -> sendMessage(event, rollRouse(), null, null, null)
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.ROUSE.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getUser().getId(),
            sheet -> {
                int dieValue = rollRouse();
                if (dieValue < 6) {
                    context.characterSheetService().incrementHunger(
                        event.getGuild(),
                        event.getUser().getId(),
                        updatedSheet -> reply(event, dieValue, updatedSheet.imageUrl(), updatedSheet.name(), updatedSheet.hunger()),
                        error -> reply(event, dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())
                    );
                } else {
                    reply(event, dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger());
                }
            },
            error -> reply(event, rollRouse(), null, null, null)
        );
    }

    private int rollRouse() {
        return ThreadLocalRandom.current().nextInt(1, 11);
    }

    private MessageEmbed buildEmbed(int dieValue, String sheetImageUrl, String characterName, Integer hunger) {
        boolean success = dieValue >= 6;
        EmbedBuilder builder = new EmbedBuilder()
            .setColor(success ? SUCCESS_COLOR : FAILURE_COLOR)
            .setImage("attachment://status-banner.png");

        if (sheetImageUrl != null && !sheetImageUrl.isEmpty()) {
            builder.setThumbnail(sheetImageUrl);
        }
        if (characterName != null && !characterName.isEmpty()) {
            builder.setAuthor(characterName);
        }
        if (hunger != null) {
            builder.setFooter("Hunger: " + hunger);
        }
        return builder.build();
    }

    private void sendMessage(MessageReceivedEvent event, int dieValue, String sheetImageUrl, String characterName, Integer hunger) {
        boolean success = dieValue >= 6;
        byte[] banner = context.statusBannerRenderer().render(success ? "ROUSE SUCCESS" : "ROUSE FAILURE", success);
        event.getChannel()
            .sendFiles(FileUpload.fromData(banner, "status-banner.png"))
            .setEmbeds(buildEmbed(dieValue, sheetImageUrl, characterName, hunger))
            .queue();
    }

    private void reply(SlashCommandInteractionEvent event, int dieValue, String sheetImageUrl, String characterName, Integer hunger) {
        boolean success = dieValue >= 6;
        byte[] banner = context.statusBannerRenderer().render(success ? "ROUSE SUCCESS" : "ROUSE FAILURE", success);
        event.replyFiles(FileUpload.fromData(banner, "status-banner.png"))
            .addEmbeds(buildEmbed(dieValue, sheetImageUrl, characterName, hunger))
            .queue();
    }
}
