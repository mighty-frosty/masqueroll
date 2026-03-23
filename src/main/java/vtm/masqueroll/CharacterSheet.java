package vtm.masqueroll;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CharacterSheet {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("([+-]?[^+-]+)");
    private static final int DEFAULT_HUNGER = 0;
    private static final String HUNGER_KEY = "hunger";

    private final Map<String, String> values;
    private final String imageUrl;
    private final String name;
    private final boolean botOwned;

    public CharacterSheet(Map<String, String> values, String imageUrl, String name, boolean botOwned) {
        this.values = new LinkedHashMap<>(values);
        this.imageUrl = imageUrl;
        this.name = name;
        this.botOwned = botOwned;
    }

    public int hunger() {
        return getValue(HUNGER_KEY).orElse(DEFAULT_HUNGER);
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
        return values.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + ", " + right)
            .orElse("No stats found.");
    }

    public String imageUrl() {
        return imageUrl;
    }

    public String name() {
        return name;
    }

    public boolean botOwned() {
        return botOwned;
    }

    public static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT)
            .replace(" ", "")
            .replace("_", "")
            .trim();
    }
}
