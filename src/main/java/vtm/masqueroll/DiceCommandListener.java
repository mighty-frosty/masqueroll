package vtm.masqueroll;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DiceCommandListener extends ListenerAdapter {

    private static final Color SUCCESS_COLOR = new Color(41, 128, 72);
    private static final Color FAILURE_COLOR = new Color(170, 32, 32);
    private static final Color SPECIAL_FAILURE_COLOR = new Color(120, 0, 0);
    private static final String CHARACTER_ALIAS = "!characters";

    private final DiceDisplayConfig displayConfig;
    private final RollImageRenderer imageRenderer;
    private final CharacterSheetService characterSheetService;
    private final Map<String, PendingRoll> pendingRolls = new ConcurrentHashMap<>();

    public DiceCommandListener(DiceDisplayConfig displayConfig, RollImageRenderer imageRenderer) {
        this.displayConfig = displayConfig;
        this.imageRenderer = imageRenderer;
        this.characterSheetService = new CharacterSheetService();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        if (content.startsWith(BotCommand.ROLL.prefixCommand())) {
            handleRollMessage(event, content);
            return;
        }
        if (content.equalsIgnoreCase(BotCommand.CHARACTER.prefixCommand())
            || content.startsWith(BotCommand.CHARACTER.prefixCommand() + " ")
            || content.equalsIgnoreCase(CHARACTER_ALIAS)
            || content.startsWith(CHARACTER_ALIAS + " ")) {
            handleCharacterMessage(event, content);
            return;
        }
        if (content.startsWith(BotCommand.SET.prefixCommand())) {
            handleSetMessage(event, content);
            return;
        }
        if (content.startsWith(BotCommand.MACRO.prefixCommand())) {
            handleMacroMessage(event, content);
            return;
        }
        if (content.startsWith(BotCommand.REMOVE_MACRO.prefixCommand())) {
            handleRemoveMacroMessage(event, content);
            return;
        }
        if (content.equalsIgnoreCase(BotCommand.ROUSE.prefixCommand())) {
            handleRouseMessage(event);
            return;
        }
        if (content.equalsIgnoreCase(BotCommand.MY_STATS.prefixCommand())) {
            characterSheetService.findSheet(
                event.getGuild(),
                event.getAuthor().getId(),
                sheet -> event.getChannel().sendMessage(sheet.describe()).queue(),
                error -> event.getChannel().sendMessage(error).queue()
            );
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (BotCommand.ROLL.slashName().equals(event.getName())) {
            handleRollSlash(event);
            return;
        }
        if (BotCommand.CHARACTER.slashName().equals(event.getName())) {
            handleCharacterSlash(event);
            return;
        }
        if (BotCommand.SET.slashName().equals(event.getName())) {
            handleSetSlash(event);
            return;
        }
        if (BotCommand.MACRO.slashName().equals(event.getName())) {
            handleMacroSlash(event);
            return;
        }
        if (BotCommand.REMOVE_MACRO.slashName().equals(event.getName())) {
            handleRemoveMacroSlash(event);
            return;
        }
        if (BotCommand.ROUSE.slashName().equals(event.getName())) {
            handleRouseSlash(event);
            return;
        }
        if (BotCommand.MY_STATS.slashName().equals(event.getName())) {
            characterSheetService.findSheet(
                event.getGuild(),
                event.getUser().getId(),
                sheet -> event.reply(sheet.describe()).setEphemeral(true).queue(),
                error -> event.reply(error).setEphemeral(true).queue()
            );
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        RerollType rerollType = RerollType.fromComponentId(componentId);
        if (rerollType == null) {
            return;
        }

        PendingRoll pendingRoll = pendingRolls.remove(componentId);
        if (pendingRoll == null) {
            event.reply("That reroll is no longer available.").setEphemeral(true).queue();
            return;
        }
        if (!pendingRoll.ownerId().equals(event.getUser().getId())) {
            pendingRolls.put(componentId, pendingRoll);
            event.reply("Only the original roller can use this reroll.").setEphemeral(true).queue();
            return;
        }

        RollSummary newSummary = applyReroll(rerollType, pendingRoll.summary(), pendingRoll.difficulty());
        if (newSummary == null) {
            event.reply("That reroll is not available for this roll.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue(
            ignored -> {
                disableRerollButtons(event);
                sendRerollResult(event, pendingRoll, newSummary);
            },
            failure -> event.getChannel().sendMessage("Could not apply that reroll. Please try a new roll.").queue()
        );
    }

    private void handleRollMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.ROLL.prefixCommand().length()).trim();
        if (args.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.ROLL.usage()).queue();
            return;
        }

        RollRequest numericRequest = tryParseNumericRoll(args);
        if (numericRequest != null) {
            if (numericRequest.hunger() != null) {
                executeRoll(event, numericRequest.pool(), numericRequest.hunger(), numericRequest.difficulty(), null, null);
                return;
            }

            characterSheetService.findSheet(
                event.getGuild(),
                event.getAuthor().getId(),
                sheet -> executeRoll(event, numericRequest.pool(), sheet.hunger(), numericRequest.difficulty(), sheet.imageUrl(), sheet.name()),
                error -> event.getChannel().sendMessage(error).queue()
            );
            return;
        }

        characterSheetService.findSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            sheet -> {
                try {
                    RollRequest sheetRequest = parseSheetRoll(args, sheet);
                    executeRoll(event, sheetRequest.pool(), sheetRequest.hunger(), sheetRequest.difficulty(), sheet.imageUrl(), sheet.name());
                } catch (IllegalArgumentException ex) {
                    event.getChannel().sendMessage(ex.getMessage()).queue();
                }
            },
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    private void handleCharacterMessage(MessageReceivedEvent event, String content) {
        String args;
        if (content.startsWith(CHARACTER_ALIAS)) {
            args = content.substring(CHARACTER_ALIAS.length()).trim();
        } else {
            args = content.substring(BotCommand.CHARACTER.prefixCommand().length()).trim();
        }
        String displayName = args.isEmpty()
            ? event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName()
            : args;
        String imageUrl = event.getMessage().getAttachments().isEmpty()
            ? null
            : event.getMessage().getAttachments().getFirst().getUrl();

        characterSheetService.createSheet(
            event.getGuild(),
            event.getAuthor(),
            displayName,
            imageUrl,
            sheet -> event.getChannel().sendMessage("Created your character sheet in #character-sheets.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    private void handleSetMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.SET.prefixCommand().length()).trim();
        String[] parts = args.split("\\s+");
        if (parts.length != 2) {
            event.getChannel().sendMessage(BotCommand.SET.usage()).queue();
            return;
        }

        try {
            int value = Integer.parseInt(parts[1]);
            characterSheetService.updateStat(
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

    private void handleMacroMessage(MessageReceivedEvent event, String content) {
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

        characterSheetService.updateMacro(
            event.getGuild(),
            event.getAuthor().getId(),
            macroName,
            formula,
            sheet -> event.getChannel().sendMessage("Updated macro `" + macroName + "`.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    private void handleRemoveMacroMessage(MessageReceivedEvent event, String content) {
        String macroName = content.substring(BotCommand.REMOVE_MACRO.prefixCommand().length()).trim();
        if (macroName.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.REMOVE_MACRO.usage()).queue();
            return;
        }

        characterSheetService.removeMacro(
            event.getGuild(),
            event.getAuthor().getId(),
            macroName,
            sheet -> event.getChannel().sendMessage("Removed macro `" + macroName + "`.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    private void handleRollSlash(SlashCommandInteractionEvent event) {
        int pool = event.getOption("pool").getAsInt();
        int hunger = event.getOption("hunger") != null ? event.getOption("hunger").getAsInt() : 0;
        Integer difficulty = event.getOption("difficulty") != null ? event.getOption("difficulty").getAsInt() : null;

        try {
            RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
            replyWithRoll(event, pool, hunger, difficulty, summary);
        } catch (IllegalArgumentException ex) {
            event.reply(ex.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleCharacterSlash(SlashCommandInteractionEvent event) {
        String displayName = event.getOption("name") != null
            ? event.getOption("name").getAsString()
            : event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        String imageUrl = event.getOption("image") != null
            ? event.getOption("image").getAsAttachment().getUrl()
            : null;

        characterSheetService.createSheet(
            event.getGuild(),
            event.getUser(),
            displayName,
            imageUrl,
            sheet -> event.reply("Created your character sheet in #character-sheets.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private void handleSetSlash(SlashCommandInteractionEvent event) {
        characterSheetService.updateStat(
            event.getGuild(),
            event.getUser().getId(),
            event.getOption("stat").getAsString(),
            event.getOption("value").getAsInt(),
            sheet -> event.reply("Updated `" + event.getOption("stat").getAsString() + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private void handleMacroSlash(SlashCommandInteractionEvent event) {
        String macroName = event.getOption("name").getAsString();
        String formula = event.getOption("formula").getAsString();
        characterSheetService.updateMacro(
            event.getGuild(),
            event.getUser().getId(),
            macroName,
            formula,
            sheet -> event.reply("Updated macro `" + macroName + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private void handleRemoveMacroSlash(SlashCommandInteractionEvent event) {
        String macroName = event.getOption("name").getAsString();
        characterSheetService.removeMacro(
            event.getGuild(),
            event.getUser().getId(),
            macroName,
            sheet -> event.reply("Removed macro `" + macroName + "`.").setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private void handleRouseMessage(MessageReceivedEvent event) {
        characterSheetService.findSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            sheet -> {
                int dieValue = rollRouse();
                if (dieValue < 6) {
                    characterSheetService.incrementHunger(
                        event.getGuild(),
                        event.getAuthor().getId(),
                        updatedSheet -> sendRouseMessage(event, buildRouseEmbed(dieValue, updatedSheet.imageUrl(), updatedSheet.name(), updatedSheet.hunger())),
                        error -> sendRouseMessage(event, buildRouseEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger()))
                    );
                } else {
                    sendRouseMessage(event, buildRouseEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger()));
                }
            },
            error -> sendRouseMessage(event, buildRouseEmbed(rollRouse(), null, null, null))
        );
    }

    private void handleRouseSlash(SlashCommandInteractionEvent event) {
        characterSheetService.findSheet(
            event.getGuild(),
            event.getUser().getId(),
            sheet -> {
                int dieValue = rollRouse();
                if (dieValue < 6) {
                    characterSheetService.incrementHunger(
                        event.getGuild(),
                        event.getUser().getId(),
                        updatedSheet -> event.replyEmbeds(buildRouseEmbed(dieValue, updatedSheet.imageUrl(), updatedSheet.name(), updatedSheet.hunger())).queue(),
                        error -> event.replyEmbeds(buildRouseEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())).queue()
                    );
                } else {
                    event.replyEmbeds(buildRouseEmbed(dieValue, sheet.imageUrl(), sheet.name(), sheet.hunger())).queue();
                }
            },
            error -> event.replyEmbeds(buildRouseEmbed(rollRouse(), null, null, null)).queue()
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

    private void executeRoll(MessageReceivedEvent event, int pool, int hunger, Integer difficulty, String sheetImageUrl, String characterName) {
        try {
            RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
            sendRollMessage(event, pool, hunger, difficulty, summary, sheetImageUrl, characterName);
        } catch (IllegalArgumentException ex) {
            event.getChannel().sendMessage(ex.getMessage()).queue();
        }
    }

    private MessageEmbed buildRouseEmbed(int dieValue, String sheetImageUrl, String characterName, Integer hunger) {
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

    private int rollRouse() {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 11);
    }

    private void sendRouseMessage(MessageReceivedEvent event, MessageEmbed embed) {
        event.getChannel().sendMessageEmbeds(embed).queue();
    }

    private MessageEmbed buildEmbed(int pool, int hunger, Integer difficulty, RollSummary summary, String sheetImageUrl, String characterName) {
        EmbedBuilder builder = new EmbedBuilder()
            .setColor(resolveColor(summary));

        String banner = formatResultBanner(summary);
        if (banner != null) {
            builder.setDescription(banner);
        }

        if (imageRenderer.isEnabled()) {
            builder.setImage("attachment://roll-result.png");
        } else {
            builder.addField("Dice", summary.formatDice(displayConfig), false);
        }

        if (sheetImageUrl != null && !sheetImageUrl.isEmpty()) {
            builder.setThumbnail(sheetImageUrl);
        }

        if (characterName != null && !characterName.isEmpty()) {
            builder.setAuthor(characterName);
        }

        return builder.build();
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

    private void sendRollMessage(MessageReceivedEvent event, int pool, int hunger, Integer difficulty, RollSummary summary, String sheetImageUrl, String characterName) {
        MessageEmbed embed = buildEmbed(pool, hunger, difficulty, summary, sheetImageUrl, characterName);
        List<Button> buttons = buildRerollButtons(event.getAuthor().getId(), event.getGuild(), pool, hunger, difficulty, summary, sheetImageUrl, characterName);
        if (imageRenderer.isEnabled()) {
            byte[] imageBytes = imageRenderer.render(summary);
            var action = event.getChannel()
                .sendFiles(FileUpload.fromData(imageBytes, "roll-result.png"))
                .setEmbeds(embed);
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

    private void replyWithRoll(SlashCommandInteractionEvent event, int pool, int hunger, Integer difficulty, RollSummary summary) {
        MessageEmbed embed = buildEmbed(pool, hunger, difficulty, summary, null, null);
        List<Button> buttons = buildRerollButtons(event.getUser().getId(), event.getGuild(), pool, hunger, difficulty, summary, null, null);
        if (imageRenderer.isEnabled()) {
            byte[] imageBytes = imageRenderer.render(summary);
            var action = event.replyFiles(FileUpload.fromData(imageBytes, "roll-result.png"))
                .addEmbeds(embed);
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
        String sheetImageUrl,
        String characterName
    ) {
        List<Button> buttons = new ArrayList<>();

        if (VtmDiceRoller.countFailedNormalRerolls(summary) > 0) {
            String id = RerollType.FAILED_NORMAL.componentPrefix() + UUID.randomUUID();
            pendingRolls.put(id, new PendingRoll(ownerId, pool, hunger, difficulty, summary, sheetImageUrl, characterName));
            Button button = Button.secondary(id, RerollType.FAILED_NORMAL.buttonLabel())
                .withEmoji(resolveGuildEmoji(guild, RerollType.FAILED_NORMAL.guildEmojiName(), RerollType.FAILED_NORMAL.fallbackEmoji()));
            buttons.add(button);
        }

        if (VtmDiceRoller.countNormalSuccessRerollsForCrit(summary) > 0) {
            String id = RerollType.SEARCH_CRIT.componentPrefix() + UUID.randomUUID();
            pendingRolls.put(id, new PendingRoll(ownerId, pool, hunger, difficulty, summary, sheetImageUrl, characterName));
            Button button = Button.primary(id, RerollType.SEARCH_CRIT.buttonLabel())
                .withEmoji(resolveGuildEmoji(guild, RerollType.SEARCH_CRIT.guildEmojiName(), RerollType.SEARCH_CRIT.fallbackEmoji()));
            buttons.add(button);
        }

        if (VtmDiceRoller.countNormalCriticalRerollsForMessy(summary) > 0) {
            String id = RerollType.BREAK_MESSY_CRIT.componentPrefix() + UUID.randomUUID();
            pendingRolls.put(id, new PendingRoll(ownerId, pool, hunger, difficulty, summary, sheetImageUrl, characterName));
            Button button = Button.danger(id, RerollType.BREAK_MESSY_CRIT.buttonLabel())
                .withEmoji(resolveGuildEmoji(guild, RerollType.BREAK_MESSY_CRIT.guildEmojiName(), RerollType.BREAK_MESSY_CRIT.fallbackEmoji()));
            buttons.add(button);
        }

        return buttons;
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

    private void disableRerollButtons(ButtonInteractionEvent event) {
        List<ActionRow> rows = event.getMessage().getComponents().stream()
            .filter(ActionRow.class::isInstance)
            .map(ActionRow.class::cast)
            .map(ActionRow::asDisabled)
            .toList();
        event.getHook().editOriginalComponents(rows).queue();
    }

    private void sendRerollResult(ButtonInteractionEvent event, PendingRoll pendingRoll, RollSummary summary) {
        MessageEmbed embed = buildEmbed(
            pendingRoll.pool(),
            pendingRoll.hunger(),
            pendingRoll.difficulty(),
            summary,
            pendingRoll.sheetImageUrl(),
            pendingRoll.characterName()
        );
        List<Button> buttons = buildRerollButtons(
            pendingRoll.ownerId(),
            event.getGuild(),
            pendingRoll.pool(),
            pendingRoll.hunger(),
            pendingRoll.difficulty(),
            summary,
            pendingRoll.sheetImageUrl(),
            pendingRoll.characterName()
        );

        if (imageRenderer.isEnabled()) {
            byte[] imageBytes = imageRenderer.render(summary);
            var action = event.getChannel().sendFiles(FileUpload.fromData(imageBytes, "roll-result.png"))
                .setEmbeds(embed);
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

    private record RollRequest(int pool, Integer hunger, Integer difficulty) {
    }
}
