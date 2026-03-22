package vtm.abbioccatore;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

public final class AbbioccatoreBot {

    private AbbioccatoreBot() {
    }

    public static void main(String[] args) throws InterruptedException {
        String token = loadToken();

        var jda = JDABuilder.createDefault(token)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(new DiceCommandListener())
            .build();

        jda.awaitReady();

        jda.updateCommands().addCommands(
            Commands.slash("roll", "Roll Vampire: The Masquerade dice.")
                .addOption(OptionType.INTEGER, "pool", "Total dice to roll", true)
                .addOption(OptionType.INTEGER, "hunger", "How many of those dice are hunger dice", false)
                .addOption(OptionType.INTEGER, "difficulty", "Optional target difficulty", false)
        ).queue();
    }

    private static String loadToken() {
        String envToken = System.getenv("DISCORD_BOT_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken;
        }

        Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

        String dotenvToken = dotenv.get("DISCORD_BOT_TOKEN");
        if (dotenvToken == null || dotenvToken.isBlank()) {
            throw new IllegalStateException("Set DISCORD_BOT_TOKEN in the environment or .env before running the bot.");
        }

        return dotenvToken;
    }
}
