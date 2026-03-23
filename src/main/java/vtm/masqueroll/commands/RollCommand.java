package vtm.masqueroll.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import vtm.masqueroll.BotCommand;
import vtm.masqueroll.CharacterSheet;
import vtm.masqueroll.dice.PendingRoll;
import vtm.masqueroll.dice.RerollType;
import vtm.masqueroll.dice.RollResultLabel;
import vtm.masqueroll.dice.RollSummary;
import vtm.masqueroll.dice.VtmDiceRoller;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RollCommand(CommandContext context, Map<String, PendingRoll> pendingRolls) implements Command {

    private static final Color SUCCESS_COLOR = new Color(41, 128, 72);
    private static final Color FAILURE_COLOR = new Color(170, 32, 32);
    private static final Color SPECIAL_FAILURE_COLOR = new Color(120, 0, 0);

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.ROLL.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.ROLL.prefixCommand().length()).trim();
        if (args.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.ROLL.usage()).queue();
            return;
        }

        RollRequest numericRequest = tryParseNumericRoll(args);
        if (numericRequest != null) {
            if (numericRequest.hunger() != null) {
                context.characterSheetService().findSheet(
                    event.getGuild(),
                    event.getAuthor().getId(),
                    sheet -> executeRoll(event, numericRequest.pool(), numericRequest.hunger(), numericRequest.difficulty(), sheet),
                    error -> executeRoll(event, numericRequest.pool(), numericRequest.hunger(), numericRequest.difficulty(), null)
                );
                return;
            }

            context.characterSheetService().findSheet(
                event.getGuild(),
                event.getAuthor().getId(),
                sheet -> executeRoll(event, numericRequest.pool(), sheet.hunger(), numericRequest.difficulty(), sheet),
                error -> executeRoll(event, numericRequest.pool(), 0, numericRequest.difficulty(), null)
            );
            return;
        }

        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            sheet -> {
                try {
                    RollRequest sheetRequest = parseSheetRoll(args, sheet);
                    executeRoll(event, sheetRequest.pool(), sheetRequest.hunger(), sheetRequest.difficulty(), sheet);
                } catch (IllegalArgumentException ex) {
                    event.getChannel().sendMessage(ex.getMessage()).queue();
                }
            },
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.ROLL.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        int pool = event.getOption("pool").getAsInt();
        Integer explicitHunger = event.getOption("hunger") != null ? event.getOption("hunger").getAsInt() : null;
        Integer difficulty = event.getOption("difficulty") != null ? event.getOption("difficulty").getAsInt() : null;

        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getUser().getId(),
            sheet -> {
                int hunger = explicitHunger != null ? explicitHunger : sheet.hunger();
                try {
                    RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
                    replyWithRoll(event, pool, hunger, difficulty, summary, sheet);
                } catch (IllegalArgumentException ex) {
                    event.reply(ex.getMessage()).setEphemeral(true).queue();
                }
            },
            error -> {
                try {
                    int hunger = explicitHunger != null ? explicitHunger : 0;
                    RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
                    replyWithRoll(event, pool, hunger, difficulty, summary, null);
                } catch (IllegalArgumentException ex) {
                    event.reply(ex.getMessage()).setEphemeral(true).queue();
                }
            }
        );
    }

    @Override
    public boolean handleButton(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        RerollType rerollType = RerollType.fromComponentId(componentId);
        if (rerollType == null) {
            return false;
        }

        PendingRoll pendingRoll = pendingRolls.remove(componentId);
        if (pendingRoll == null) {
            event.reply("That reroll is no longer available.").setEphemeral(true).queue();
            return true;
        }
        if (!pendingRoll.ownerId().equals(event.getUser().getId())) {
            pendingRolls.put(componentId, pendingRoll);
            event.reply("Only the original roller can use this reroll.").setEphemeral(true).queue();
            return true;
        }

        context.characterSheetService().findSheet(
            event.getGuild(),
            event.getUser().getId(),
            sheet -> {
                int willpower = sheet.currentWillpower();
                if (willpower < 1) {
                    pendingRolls.put(componentId, pendingRoll);
                    event.reply("You need at least 1 willpower to reroll.").setEphemeral(true).queue();
                    return;
                }

                RollSummary newSummary = applyReroll(rerollType, pendingRoll.summary(), pendingRoll.difficulty());
                if (newSummary == null) {
                    event.reply("That reroll is not available for this roll.").setEphemeral(true).queue();
                    return;
                }

                context.characterSheetService().adjustStat(
                        event.getGuild(),
                        event.getUser().getId(),
                        "willpower",
                    -1,
                    0,
                    20,
                    updatedSheet -> event.deferEdit().queue(
                        ignored -> {
                            disableRerollButtons(event);
                            sendRerollResult(event, pendingRoll, newSummary, updatedSheet);
                        },
                        failure -> event.getChannel().sendMessage("Could not apply that reroll. Please try a new roll.").queue()
                    ),
                    error -> {
                        pendingRolls.put(componentId, pendingRoll);
                        event.reply(error).setEphemeral(true).queue();
                    }
                );
            },
            error -> handleSheetlessReroll(event, componentId, pendingRoll, rerollType)
        );
        return true;
    }

    private void handleSheetlessReroll(
        ButtonInteractionEvent event,
        String componentId,
        PendingRoll pendingRoll,
        RerollType rerollType
    ) {
        RollSummary newSummary = applyReroll(rerollType, pendingRoll.summary(), pendingRoll.difficulty());
        if (newSummary == null) {
                pendingRolls.put(componentId, pendingRoll);
                event.reply("That reroll is not available for this roll.").setEphemeral(true).queue();
                return;
        }

        event.deferEdit().queue(
            ignored -> {
                disableRerollButtons(event);
                sendRerollResult(event, pendingRoll, newSummary, pendingRoll.sheet());
            },
            failure -> {
                pendingRolls.put(componentId, pendingRoll);
                event.getChannel().sendMessage("Could not apply that reroll. Please try a new roll.").queue();
            }
        );
    }

    private RollRequest tryParseNumericRoll(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 1 || parts.length > 3) {
            return null;
        }

        try {
            int pool = Integer.parseInt(parts[0]);
            Integer hunger = parts.length >= 2 ? Integer.parseInt(parts[1]) : null;
            Integer difficulty = parts.length == 3 ? Integer.parseInt(parts[2]) : null;
            return new RollRequest(pool, hunger, difficulty);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private RollRequest parseSheetRoll(String args, CharacterSheet sheet) {
        String[] parts = args.split("\\s+");
        Integer difficulty = null;
        int modifier = 0;
        String expression = args;

        if (parts.length > 1) {
            String last = parts[parts.length - 1];
            if (last.matches("[+-]\\d+")) {
                modifier = Integer.parseInt(last);
                expression = args.substring(0, args.length() - last.length()).trim();
                parts = expression.split("\\s+");
            }
        }

        if (parts.length > 1) {
            String last = parts[parts.length - 1];
            if (last.matches("\\d+")) {
                difficulty = Integer.parseInt(last);
                expression = expression.substring(0, expression.length() - last.length()).trim();
            }
        }

        if (expression.isEmpty()) {
            throw new IllegalArgumentException(BotCommand.ROLL.usage());
        }

        String finalExpression = expression;
        int pool = sheet.resolvePool(expression).orElseThrow(() ->
            new IllegalArgumentException("I couldn't find `" + finalExpression + "` on your sheet.")
        ) + modifier;

        if (pool < 1) {
            throw new IllegalArgumentException("That roll ends up below 1 die.");
        }

        return new RollRequest(pool, sheet.hunger(), difficulty);
    }

    private void executeRoll(MessageReceivedEvent event, int pool, int hunger, Integer difficulty, CharacterSheet sheet) {
        try {
            RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
            sendRollMessage(event, pool, hunger, difficulty, summary, sheet);
        } catch (IllegalArgumentException ex) {
            event.getChannel().sendMessage(ex.getMessage()).queue();
        }
    }

    private MessageEmbed buildEmbed(RollSummary summary, CharacterSheet sheet) {
        EmbedBuilder builder = new EmbedBuilder().setColor(resolveColor(summary));
        String banner = formatResultBanner(summary);
        if (banner != null) {
            builder.setDescription(banner);
        }
        if (context.imageRenderer().isEnabled()) {
            builder.setImage("attachment://roll-result.png");
        } else {
            builder.addField("Dice", summary.formatDice(context.displayConfig()), false);
        }
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

    private void sendRollMessage(MessageReceivedEvent event, int pool, int hunger, Integer difficulty, RollSummary summary, CharacterSheet sheet) {
        MessageEmbed embed = buildEmbed(summary, sheet);
        List<Button> buttons = buildRerollButtons(event.getAuthor().getId(), event.getGuild(), pool, hunger, difficulty, summary, sheet);
        if (context.imageRenderer().isEnabled()) {
            byte[] imageBytes = context.imageRenderer().render(summary);
            var action = event.getChannel().sendFiles(FileUpload.fromData(imageBytes, "roll-result.png")).setEmbeds(embed);
            if (!buttons.isEmpty()) {
                action.setComponents(ActionRow.of(buttons));
            }
            action.queue();
            return;
        }

        var action = event.getChannel().sendMessageEmbeds(embed);
        if (!buttons.isEmpty()) {
            action.setComponents(ActionRow.of(buttons));
        }
        action.queue();
    }

    private void replyWithRoll(
        SlashCommandInteractionEvent event,
        int pool,
        int hunger,
        Integer difficulty,
        RollSummary summary,
        CharacterSheet sheet
    ) {
        MessageEmbed embed = buildEmbed(summary, sheet);
        List<Button> buttons = buildRerollButtons(event.getUser().getId(), event.getGuild(), pool, hunger, difficulty, summary, sheet);
        if (context.imageRenderer().isEnabled()) {
            byte[] imageBytes = context.imageRenderer().render(summary);
            var action = event.replyFiles(FileUpload.fromData(imageBytes, "roll-result.png")).addEmbeds(embed);
            if (!buttons.isEmpty()) {
                action.addComponents(ActionRow.of(buttons));
            }
            action.queue();
            return;
        }

        var action = event.replyEmbeds(embed);
        if (!buttons.isEmpty()) {
            action.addComponents(ActionRow.of(buttons));
        }
        action.queue();
    }

    private List<Button> buildRerollButtons(
        String ownerId,
        net.dv8tion.jda.api.entities.Guild guild,
        int pool,
        int hunger,
        Integer difficulty,
        RollSummary summary,
        CharacterSheet sheet
    ) {
        List<Button> buttons = new ArrayList<>();

        if (VtmDiceRoller.countFailedNormalRerolls(summary) > 0) {
            String id = RerollType.FAILED_NORMAL.componentPrefix() + UUID.randomUUID();
            pendingRolls.put(id, new PendingRoll(ownerId, pool, hunger, difficulty, summary, sheet));
            buttons.add(Button.secondary(id, RerollType.FAILED_NORMAL.buttonLabel())
                .withEmoji(resolveGuildEmoji(guild, RerollType.FAILED_NORMAL.guildEmojiName(), RerollType.FAILED_NORMAL.fallbackEmoji())));
        }
        if (VtmDiceRoller.countNormalSuccessRerollsForCrit(summary) > 0) {
            String id = RerollType.SEARCH_CRIT.componentPrefix() + UUID.randomUUID();
            pendingRolls.put(id, new PendingRoll(ownerId, pool, hunger, difficulty, summary, sheet));
            buttons.add(Button.primary(id, RerollType.SEARCH_CRIT.buttonLabel())
                .withEmoji(resolveGuildEmoji(guild, RerollType.SEARCH_CRIT.guildEmojiName(), RerollType.SEARCH_CRIT.fallbackEmoji())));
        }
        if (VtmDiceRoller.countNormalCriticalRerollsForMessy(summary) > 0) {
            String id = RerollType.BREAK_MESSY_CRIT.componentPrefix() + UUID.randomUUID();
            pendingRolls.put(id, new PendingRoll(ownerId, pool, hunger, difficulty, summary, sheet));
            buttons.add(Button.danger(id, RerollType.BREAK_MESSY_CRIT.buttonLabel())
                .withEmoji(resolveGuildEmoji(guild, RerollType.BREAK_MESSY_CRIT.guildEmojiName(), RerollType.BREAK_MESSY_CRIT.fallbackEmoji())));
        }

        return buttons;
    }

    private void disableRerollButtons(ButtonInteractionEvent event) {
        List<ActionRow> rows = event.getMessage().getComponents().stream()
            .filter(ActionRow.class::isInstance)
            .map(ActionRow.class::cast)
            .map(ActionRow::asDisabled)
            .toList();
        event.getHook().editOriginalComponents(rows).queue();
    }

    private void sendRerollResult(ButtonInteractionEvent event, PendingRoll pendingRoll, RollSummary summary, CharacterSheet sheet) {
        MessageEmbed embed = buildEmbed(summary, sheet);
        List<Button> buttons = buildRerollButtons(
            pendingRoll.ownerId(),
            event.getGuild(),
            pendingRoll.pool(),
            pendingRoll.hunger(),
            pendingRoll.difficulty(),
            summary,
            sheet
        );

        if (context.imageRenderer().isEnabled()) {
            byte[] imageBytes = context.imageRenderer().render(summary);
            var action = event.getChannel().sendFiles(FileUpload.fromData(imageBytes, "roll-result.png")).setEmbeds(embed);
            if (!buttons.isEmpty()) {
                action.setComponents(ActionRow.of(buttons));
            }
            action.queue();
            return;
        }

        var action = event.getChannel().sendMessageEmbeds(embed);
        if (!buttons.isEmpty()) {
            action.setComponents(ActionRow.of(buttons));
        }
        action.queue();
    }

    private RollSummary applyReroll(RerollType rerollType, RollSummary summary, Integer difficulty) {
        return switch (rerollType) {
            case FAILED_NORMAL -> VtmDiceRoller.rerollFailedNormalDice(summary, difficulty, 3);
            case SEARCH_CRIT -> VtmDiceRoller.rerollNormalSuccessesForCrit(summary, difficulty, 3);
            case BREAK_MESSY_CRIT -> VtmDiceRoller.rerollNormalCriticalsForMessy(summary, difficulty);
        };
    }

    private Color resolveColor(RollSummary summary) {
        if (summary.bestialFailure()) {
            return SPECIAL_FAILURE_COLOR;
        }
        if (summary.success()) {
            return SUCCESS_COLOR;
        }
        return FAILURE_COLOR;
    }

    private String formatResultBanner(RollSummary summary) {
        if (summary.bestialFailure()) {
            return "```diff\n- " + RollResultLabel.BESTIAL_FAILURE.text() + "\n```";
        }
        if (summary.messyCritical()) {
            return "```diff\n- " + RollResultLabel.MESSY_CRITICAL.text() + "\n```";
        }
        return null;
    }

    private Emoji resolveGuildEmoji(net.dv8tion.jda.api.entities.Guild guild, String name, String fallbackUnicode) {
        if (guild != null) {
            var matches = guild.getEmojisByName(name, true);
            if (!matches.isEmpty()) {
                var emoji = matches.getFirst();
                return Emoji.fromCustom(emoji.getName(), emoji.getIdLong(), emoji.isAnimated());
            }
        }
        return Emoji.fromUnicode(fallbackUnicode);
    }

    private record RollRequest(int pool, Integer hunger, Integer difficulty) {
    }
}
