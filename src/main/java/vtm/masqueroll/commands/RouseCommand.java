package vtm.masqueroll.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
                        updatedSheet -> event.getChannel().sendMessageEmbeds(buildEmbed(dieValue, updatedSheet.imageUrl(), updatedSheet.name(), updatedSheet.hunger())).queue(),
                        error -> event.getChannel().sendMessageEmbeds(buildEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())).queue()
                    );
                } else {
                    event.getChannel().sendMessageEmbeds(buildEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())).queue();
                }
            },
            error -> event.getChannel().sendMessageEmbeds(buildEmbed(rollRouse(), null, null, null)).queue()
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
                        updatedSheet -> event.replyEmbeds(buildEmbed(dieValue, updatedSheet.imageUrl(), updatedSheet.name(), updatedSheet.hunger())).queue(),
                        error -> event.replyEmbeds(buildEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())).queue()
                    );
                } else {
                    event.replyEmbeds(buildEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())).queue();
                }
            },
            error -> event.replyEmbeds(buildEmbed(rollRouse(), null, null, null)).queue()
        );
    }

    private int rollRouse() {
        return ThreadLocalRandom.current().nextInt(1, 11);
    }

    private MessageEmbed buildEmbed(int dieValue, String sheetImageUrl, String characterName, Integer hunger) {
        boolean success = dieValue >= 6;
        EmbedBuilder builder = new EmbedBuilder()
            .setColor(success ? SUCCESS_COLOR : FAILURE_COLOR)
            .setDescription(success ? "```diff\n+ ROUSE SUCCESS\n```" : "```diff\n- ROUSE FAILURE\n```");

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
}
