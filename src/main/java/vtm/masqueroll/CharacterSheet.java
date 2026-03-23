package vtm.masqueroll;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CharacterSheet(
    Map<String, String> values,
    String imageUrl,
    String name,
    boolean botOwned
) {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("([+-]?[^+-]+)");
    private static final int DEFAULT_HUNGER = 0;
    private static final String HUNGER_KEY = "hunger";
    private static final int BASE_HEALTH = 3;
    private static final String HEALTH_SUPERFICIAL_KEY = "healthsuperficial";
    private static final String HEALTH_AGGRAVATED_KEY = "healthaggravated";
    private static final String WILLPOWER_KEY = "willpower";
    private static final String STAMINA_KEY = "stamina";
    private static final String COMPOSURE_KEY = "composure";
    private static final String RESOLVE_KEY = "resolve";

    public CharacterSheet {
        values = new LinkedHashMap<>(values);
    }

    public int hunger() {
        return getValue(HUNGER_KEY).orElse(DEFAULT_HUNGER);
    }

    public int healthMax() {
        return getValue(STAMINA_KEY).orElse(0) + BASE_HEALTH;
    }

    public int willpowerMax() {
        return getValue(COMPOSURE_KEY).orElse(0) + getValue(RESOLVE_KEY).orElse(0);
    }

    public int currentWillpower() {
        return getValue(WILLPOWER_KEY).orElse(willpowerMax());
    }

    public OptionalInt getValue(String key) {
        return resolveValue(normalizeKey(key), new HashSet<>());
    }

    public OptionalInt resolvePool(String expression) {
        return resolveExpression(expression, new HashSet<>());
    }

    private OptionalInt resolveExpression(String expression, Set<String> seen) {
        String normalized = normalizeKey(expression);
        OptionalInt directValue = resolveValue(normalized, seen);
        if (directValue.isPresent()) {
            return directValue;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        int total = 0;
        boolean found = false;

        while (matcher.find()) {
            String token = matcher.group(1).trim();
            if (token.isEmpty()) {
                continue;
            }

            found = true;
            int sign = 1;
            if (token.startsWith("+")) {
                token = token.substring(1);
            } else if (token.startsWith("-")) {
                token = token.substring(1);
                sign = -1;
            }

            if (token.isEmpty()) {
                continue;
            }

            try {
                total += sign * Integer.parseInt(token);
                continue;
            } catch (NumberFormatException ignored) {
            }

            OptionalInt resolved = resolveValue(token, seen);
            if (resolved.isEmpty()) {
                return OptionalInt.empty();
            }
            total += sign * resolved.getAsInt();
        }

        if (!found) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(total);
    }

    private OptionalInt resolveValue(String key, Set<String> seen) {
        String normalized = normalizeKey(key);
        String rawValue = values.get(normalized);
        if (rawValue == null || rawValue.isEmpty()) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(rawValue));
        } catch (NumberFormatException ignored) {
        }

        if (!seen.add(normalized)) {
            return OptionalInt.empty();
        }

        OptionalInt resolved = resolveExpression(rawValue, seen);
        seen.remove(normalized);
        return resolved;
    }

    public String describe() {
        String displayName = name != null && !name.isBlank() ? name : "Character";
        return displayName + "\n"
            + hungerSummary() + "\n"
            + healthSummary() + "\n"
            + willpowerSummary();
    }

    public String hungerSummary() {
        int hunger = hunger();
        return "Hunger: " + renderTrack(hunger, 5, "\uD83D\uDD34", "\u26AA") + " (" + hunger + "/5)";
    }

    public String healthSummary() {
        int healthMax = healthMax();
        int superficial = getValue(HEALTH_SUPERFICIAL_KEY).orElse(0);
        int aggravated = getValue(HEALTH_AGGRAVATED_KEY).orElse(0);
        return "Health: " + renderHealthTrack(superficial, aggravated, healthMax)
            + " (" + superficial + " superficial, " + aggravated + " aggravated)";
    }

    public String willpowerSummary() {
        int willpower = currentWillpower();
        int willpowerMax = willpowerMax();
        return "Willpower: " + renderTrack(Math.max(0, Math.min(willpower, willpowerMax)), willpowerMax, "\uD83D\uDD35", "\u26AB")
            + " (" + willpower + "/" + willpowerMax + ")";
    }

    public static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT)
            .replace(" ", "")
            .replace("_", "")
            .trim();
    }

    private String renderHealthTrack(int superficial, int aggravated, int max) {
        if (max < 1) {
            return "-";
        }

        int safeAggravated = Math.max(0, Math.min(aggravated, max));
        int safeSuperficial = Math.max(0, Math.min(superficial, Math.max(0, max - safeAggravated)));
        int empty = Math.max(0, max - safeAggravated - safeSuperficial);

        return "\uD83D\uDFE5".repeat(safeAggravated)
            + "\uD83D\uDFE7".repeat(safeSuperficial)
            + "\u2B1C".repeat(empty);
    }

    private String renderTrack(int filled, int max, String filledEmoji, String emptyEmoji) {
        if (max < 1) {
            return "-";
        }

        int safeFilled = Math.max(0, Math.min(filled, max));
        return filledEmoji.repeat(safeFilled) + emptyEmoji.repeat(max - safeFilled);
    }
}
