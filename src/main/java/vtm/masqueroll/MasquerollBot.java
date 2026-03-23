package vtm.masqueroll;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import vtm.masqueroll.dice.DiceDisplayConfig;
import vtm.masqueroll.dice.RollImageRenderer;

import java.nio.file.Path;

public final class MasquerollBot {

    private MasquerollBot() {
    }

    public static void main(String[] args) throws InterruptedException {
        String token = loadToken();
        DiceDisplayConfig displayConfig = DiceDisplayConfig.load();
        RollImageRenderer imageRenderer = new RollImageRenderer(Path.of("src", "images"));

        var jda = JDABuilder.createDefault(token)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(new DiceCommandListener(displayConfig, imageRenderer))
            .build();

        jda.awaitReady();

        jda.updateCommands().addCommands(
            Commands.slash(BotCommand.HELP.slashName(), BotCommand.HELP.description()),
            Commands.slash(BotCommand.ROLL.slashName(), BotCommand.ROLL.description())
                .addOption(OptionType.INTEGER, "pool", "Total dice to roll", true)
                .addOption(OptionType.INTEGER, "hunger", "How many of those dice are hunger dice", false)
                .addOption(OptionType.INTEGER, "difficulty", "Optional target difficulty", false),
            Commands.slash(BotCommand.ROUSE.slashName(), BotCommand.ROUSE.description()),
            Commands.slash(BotCommand.CHARACTER.slashName(), BotCommand.CHARACTER.description())
                .addOption(OptionType.STRING, "name", "Character name for the sheet", false)
                .addOption(OptionType.ATTACHMENT, "image", "Optional character image", false),
            Commands.slash(BotCommand.SET.slashName(), BotCommand.SET.description())
                .addOption(OptionType.STRING, "stat", "Stat to set", true)
                .addOption(OptionType.INTEGER, "value", "Value", true),
            Commands.slash(BotCommand.MACRO.slashName(), BotCommand.MACRO.description())
                .addOption(OptionType.STRING, "name", "Macro name", true)
                .addOption(OptionType.STRING, "formula", "Formula like wits + awareness + 3", true),
            Commands.slash(BotCommand.REMOVE_MACRO.slashName(), BotCommand.REMOVE_MACRO.description())
                .addOption(OptionType.STRING, "name", "Macro name", true),
            Commands.slash(BotCommand.DAMAGE.slashName(), BotCommand.DAMAGE.description())
                .addOption(OptionType.STRING, "type", "superficial or aggravated", true)
                .addOption(OptionType.INTEGER, "amount", "How much damage to apply", false),
            Commands.slash(BotCommand.HEAL.slashName(), BotCommand.HEAL.description()),
            Commands.slash(BotCommand.RESTORE.slashName(), BotCommand.RESTORE.description())
                .addOption(OptionType.STRING, "target", "all, health, or willpower", false)
                .addOption(OptionType.INTEGER, "amount", "Optional amount to restore", false),
            Commands.slash(BotCommand.MY_STATS.slashName(), BotCommand.MY_STATS.description())
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
