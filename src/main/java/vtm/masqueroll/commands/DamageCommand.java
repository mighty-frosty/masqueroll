package vtm.masqueroll.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import vtm.masqueroll.BotCommand;
import vtm.masqueroll.CharacterSheet;

import java.util.function.Consumer;

public record DamageCommand(CommandContext context) implements Command {

    @Override
    public boolean matchesMessage(String content) {
        return content.startsWith(BotCommand.DAMAGE.prefixCommand());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event, String content) {
        String args = content.substring(BotCommand.DAMAGE.prefixCommand().length()).trim();
        if (args.isEmpty()) {
            event.getChannel().sendMessage(BotCommand.DAMAGE.usage()).queue();
            return;
        }

        String[] parts = args.split("\\s+");
        String damageType = parts[0];
        int amount = parseAmount(parts.length > 1 ? parts[1] : null, message -> event.getChannel().sendMessage(message).queue());
        if (amount < 1) {
            return;
        }

        applyDamage(
            event.getGuild(),
            event.getAuthor().getId(),
            damageType,
            amount,
            message -> event.getChannel().sendMessage(message).queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
    }

    @Override
    public boolean matchesSlash(String name) {
        return BotCommand.DAMAGE.slashName().equals(name);
    }

    @Override
    public void handleSlash(SlashCommandInteractionEvent event) {
        applyDamage(
            event.getGuild(),
            event.getUser().getId(),
            event.getOption("type").getAsString(),
            event.getOption("amount") != null ? event.getOption("amount").getAsInt() : 1,
            message -> event.reply(message).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue()
        );
    }

    private int parseAmount(String rawAmount, Consumer<String> onFailure) {
        if (rawAmount == null) {
            return 1;
        }
        try {
            return Integer.parseInt(rawAmount);
        } catch (NumberFormatException ex) {
            onFailure.accept("Damage amount must be a whole number.");
            return -1;
        }
    }

    private void applyDamage(
        net.dv8tion.jda.api.entities.Guild guild,
        String userId,
        String damageType,
        int amount,
        Consumer<String> onSuccess,
        Consumer<String> onFailure
    ) {
        if (amount < 1) {
            onFailure.accept("Damage amount must be at least 1.");
            return;
        }

        String normalized = CharacterSheet.normalizeKey(damageType);
        String stat;
        String label;
        if (normalized.equals("superficial")) {
            stat = "health_superficial";
            label = "superficial";
        } else if (normalized.equals("aggravated")) {
            stat = "health_aggravated";
            label = "aggravated";
        } else {
            onFailure.accept("Use `!damage superficial` or `!damage aggravated`.");
            return;
        }

        context.characterSheetService().adjustStat(
            guild,
            userId,
            stat,
            amount,
            0,
            20,
            sheet -> onSuccess.accept("Applied " + amount + " " + label + " health damage."),
            onFailure
        );
    }
}
