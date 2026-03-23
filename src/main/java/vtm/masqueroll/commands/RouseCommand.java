package vtm.masqueroll.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import vtm.masqueroll.BotCommand;
import vtm.masqueroll.CharacterSheet;

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
                        updatedSheet -> sendMessage(event, dieValue, updatedSheet),
                        error -> sendMessage(event, dieValue, sheet)
                    );
                } else {
                    sendMessage(event, dieValue, sheet);
                }
            },
            error -> sendMessage(event, rollRouse(), null)
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
                        updatedSheet -> reply(event, dieValue, updatedSheet),
                        error -> reply(event, dieValue, sheet)
                    );
                } else {
                    reply(event, dieValue, sheet);
                }
            },
            error -> reply(event, rollRouse(), null)
        );
    }

    private int rollRouse() {
        return ThreadLocalRandom.current().nextInt(1, 11);
    }

    private MessageEmbed buildEmbed(int dieValue, CharacterSheet sheet) {
        boolean success = dieValue >= 6;
        EmbedBuilder builder = new EmbedBuilder()
            .setColor(success ? SUCCESS_COLOR : FAILURE_COLOR)
            .setImage("attachment://status-banner.png");

        if (sheet != null) {
            if (sheet.imageUrl() != null && !sheet.imageUrl().isEmpty()) {
                builder.setThumbnail(sheet.imageUrl());
            }
            if (sheet.name() != null && !sheet.name().isEmpty()) {
                builder.setAuthor(sheet.name());
            }
            builder.addField("Hunger", sheet.hungerSummary().replaceFirst("^Hunger: ", ""), false);
            builder.addField("Health", sheet.healthSummary().replaceFirst("^Health: ", ""), false);
            builder.addField("Willpower", sheet.willpowerSummary().replaceFirst("^Willpower: ", ""), false);
        }
        return builder.build();
    }

    private void sendMessage(MessageReceivedEvent event, int dieValue, CharacterSheet sheet) {
        boolean success = dieValue >= 6;
        byte[] banner = context.statusBannerRenderer().render(success ? "ROUSE SUCCESS" : "ROUSE FAILURE", success);
        event.getChannel()
            .sendFiles(FileUpload.fromData(banner, "status-banner.png"))
            .setEmbeds(buildEmbed(dieValue, sheet))
            .queue();
    }

    private void reply(SlashCommandInteractionEvent event, int dieValue, CharacterSheet sheet) {
        boolean success = dieValue >= 6;
        byte[] banner = context.statusBannerRenderer().render(success ? "ROUSE SUCCESS" : "ROUSE FAILURE", success);
        event.replyFiles(FileUpload.fromData(banner, "status-banner.png"))
            .addEmbeds(buildEmbed(dieValue, sheet))
            .queue();
    }
}
