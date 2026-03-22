package vtm.abbioccatore;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;

public final class DiceCommandListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        if (!content.startsWith("!roll")) {
            return;
        }

        String[] parts = content.split("\\s+");
        if (parts.length < 2 || parts.length > 4) {
            event.getChannel().sendMessage("Usage: `!roll <pool> [hunger] [difficulty]`").queue();
            return;
        }

        try {
            int pool = Integer.parseInt(parts[1]);
            int hunger = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
            Integer difficulty = parts.length == 4 ? Integer.parseInt(parts[3]) : null;

            RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
            event.getChannel().sendMessageEmbeds(buildEmbed(pool, hunger, difficulty, summary)).queue();
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessage("All values must be whole numbers.").queue();
        } catch (IllegalArgumentException ex) {
            event.getChannel().sendMessage(ex.getMessage()).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"roll".equals(event.getName())) {
            return;
        }

        int pool = event.getOption("pool").getAsInt();
        int hunger = event.getOption("hunger") != null ? event.getOption("hunger").getAsInt() : 0;
        Integer difficulty = event.getOption("difficulty") != null ? event.getOption("difficulty").getAsInt() : null;

        try {
            RollSummary summary = VtmDiceRoller.roll(pool, hunger, difficulty);
            event.replyEmbeds(buildEmbed(pool, hunger, difficulty, summary)).queue();
        } catch (IllegalArgumentException ex) {
            event.reply(ex.getMessage()).setEphemeral(true).queue();
        }
    }

    private MessageEmbed buildEmbed(int pool, int hunger, Integer difficulty, RollSummary summary) {
        return new EmbedBuilder()
            .setTitle("Vampire: The Masquerade Roll")
            .setColor(new Color(105, 15, 20))
            .addField("Pool", String.valueOf(pool), true)
            .addField("Hunger", String.valueOf(hunger), true)
            .addField("Difficulty", difficulty == null ? "None" : String.valueOf(difficulty), true)
            .addField("Dice", summary.formatDice(), false)
            .addField("Successes", String.valueOf(summary.successes()), true)
            .addField("Critical Pairs", String.valueOf(summary.criticalPairs()), true)
            .addField("Outcome", summary.outcome(), false)
            .setFooter("Hunger dice are bolded.")
            .build();
    }
}
