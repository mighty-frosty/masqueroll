package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;
import vtm.masqueroll.CharacterSheet;

import java.util.function.Consumer;

public record RestoreCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.RESTORE.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String target = content.substring(BotCommand.RESTORE.prefixCommand().length()).trim();
        applyRestore(
            event.getGuild(),
            event.getAuthor().getId(),
            target,
            message -> event.getChannel().sendMessage(message).queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.RESTORE.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target") != null ? event.getOption("target").getAsString() : "";
        if (event.getOption("amount") != null) {
            target = target.isBlank()
                ? Integer.toString(event.getOption("amount").getAsInt())
                : target + " " + event.getOption("amount").getAsInt();
        }
        applyRestore(
            event.getGuild(),
            event.getUser().getId(),
            target,
            message -> event.reply(message).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private void applyRestore(
        net.dv8tion.jda.api.entities.Guild guild,
        String userId,
        String target,
        Consumer<String> onSuccess,
        Consumer<String> onFailure
    ) {
        String trimmed = target == null ? "" : target.trim();
        String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        String normalized = parts.length == 0 ? "" : CharacterSheet.normalizeKey(parts[0]);
        Integer amount = null;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                onFailure.accept(BotCommand.RESTORE.usage());
                return;
            }
            if (amount < 1) {
                onFailure.accept("Restore amount must be at least 1.");
                return;
            }
        }

        boolean restoreHealth;
        boolean restoreWillpower;
        if (normalized.isEmpty() || normalized.equals("all")) {
            restoreHealth = true;
            restoreWillpower = true;
            if (amount != null) {
                onFailure.accept("Use `!restore health <number>` or `!restore willpower <number>` for partial restore.");
                return;
            }
        } else if (normalized.equals("health")) {
            restoreHealth = true;
            restoreWillpower = false;
        } else if (normalized.equals("willpower")) {
            restoreHealth = false;
            restoreWillpower = true;
        } else {
            onFailure.accept(BotCommand.RESTORE.usage());
            return;
        }

        final boolean finalRestoreHealth = restoreHealth;
        final boolean finalRestoreWillpower = restoreWillpower;
        final Integer finalAmount = amount;
        context.characterSheetService().restoreTracks(
            guild,
            userId,
            finalRestoreHealth,
            finalRestoreWillpower,
            finalAmount,
            sheet -> {
                if (finalRestoreHealth && finalRestoreWillpower) {
                    onSuccess.accept("Restored health and willpower.");
                } else if (finalRestoreHealth && finalAmount != null) {
                    onSuccess.accept("Restored " + finalAmount + " health.");
                } else if (finalRestoreWillpower && finalAmount != null) {
                    onSuccess.accept("Restored " + finalAmount + " willpower.");
                } else if (finalRestoreHealth) {
                    onSuccess.accept("Restored health.");
                } else {
                    onSuccess.accept("Restored willpower.");
                }
            },
            onFailure
        );
    }
}
