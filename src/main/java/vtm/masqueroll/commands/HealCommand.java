package vtm.masqueroll.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;
import vtm.masqueroll.CharacterSheet;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public record HealCommand(CommandContext context) implements Command {

    private static final Color SUCCESS_COLOR = new Color(41, 128, 72);
    private static final Color FAILURE_COLOR = new Color(170, 32, 32);

    @Override
    public boolean matchesMessage(String content) {
        return content.equalsIgnoreCase(BotCommand.HEAL.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        healDamage(
            event.getGuild(),
            event.getAuthor().getId(),
            embed -> event.getChannel().sendMessageEmbeds(embed).queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.HEAL.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        healDamage(
            event.getGuild(),
            event.getUser().getId(),
            embed -> event.replyEmbeds(embed).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private void healDamage(
        net.dv8tion.jda.api.entities.Guild guild,
        String userId,
        Consumer<MessageEmbed> onSuccess,
        Consumer<String> onFailure
    ) {
        context.characterSheetService().findSheet(
            guild,
            userId,
            sheet -> {
                int superficial = sheet.getValue("health_superficial").orElse(0);
                if (superficial < 1) {
                    onFailure.accept("You have no superficial health damage to heal.");
                    return;
                }

                int dieValue = ThreadLocalRandom.current().nextInt(1, 11);
                context.characterSheetService().adjustStat(
                    guild,
                    userId,
                    "health_superficial",
                    -1,
                    0,
                    20,
                    updatedSheet -> {
                        if (dieValue < 6) {
                            context.characterSheetService().adjustStat(
                                guild,
                                userId,
                                "hunger",
                                1,
                                0,
                                5,
                                hungerUpdated -> onSuccess.accept(buildEmbed(dieValue, hungerUpdated)),
                                onFailure
                            );
                        } else {
                            onSuccess.accept(buildEmbed(dieValue, updatedSheet));
                        }
                    },
                    onFailure
                );
            },
            onFailure
        );
    }

    private MessageEmbed buildEmbed(int dieValue, CharacterSheet sheet) {
        boolean success = dieValue >= 6;
        EmbedBuilder builder = new EmbedBuilder()
            .setColor(success ? SUCCESS_COLOR : FAILURE_COLOR)
            .setDescription(success ? "```diff\n+ HEAL SUCCESS\n```" : "```diff\n- HEAL WITH ROUSE FAILURE\n```")
            .addField("Hunger", sheet.hungerSummary().replaceFirst("^Hunger: ", ""), false)
            .addField("Health", sheet.healthSummary().replaceFirst("^Health: ", ""), false)
            .addField("Willpower", sheet.willpowerSummary().replaceFirst("^Willpower: ", ""), false);

        if (sheet.imageUrl() != null && !sheet.imageUrl().isEmpty()) {
            builder.setThumbnail(sheet.imageUrl());
        }
        if (sheet.name() != null && !sheet.name().isEmpty()) {
            builder.setAuthor(sheet.name());
        }

        return builder.build();
    }
}
