package vtm.masqueroll;

import io.github.cdimascio.dotenv.Dotenv;

public record DiceDisplayConfig(
    String normalSuccessEmoji,
    String normalFailureEmoji,
    String hungerSuccessEmoji,
    String hungerFailureEmoji,
    String normalTenEmoji,
    String hungerTenEmoji
) {

    public static DiceDisplayConfig load() {
        Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

        return new DiceDisplayConfig(
            readValue("VTM_EMOJI_NORMAL_SUCCESS", ":white_circle:", dotenv),
            readValue("VTM_EMOJI_NORMAL_FAILURE", ":black_circle:", dotenv),
            readValue("VTM_EMOJI_HUNGER_SUCCESS", ":red_circle:", dotenv),
            readValue("VTM_EMOJI_HUNGER_FAILURE", ":brown_circle:", dotenv),
            readValue("VTM_EMOJI_NORMAL_TEN", ":white_circle:", dotenv),
            readValue("VTM_EMOJI_HUNGER_TEN", ":red_circle:", dotenv)
        );
    }

    private static String readValue(String key, String fallback, Dotenv dotenv) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        String dotenvValue = dotenv.get(key);
        if (dotenvValue != null && !dotenvValue.isBlank()) {
            return dotenvValue;
        }

        return fallback;
    }
}
