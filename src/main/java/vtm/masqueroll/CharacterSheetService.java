package vtm.masqueroll;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CharacterSheetService {

    private static final String CHANNEL_NAME = "character-sheets";
    private static final int HISTORY_SCAN_LIMIT = 100;
    private static final String TRACKERS_MARKER = "---- TRACKERS ----";

    public void findSheet(Guild guild, String userId, Consumer<CharacterSheet> onSuccess, Consumer<String> onFailure) {
        findSheetMessage(
            guild,
            userId,
            message -> onSuccess.accept(parseSheet(message)),
            onFailure
        );
    }

    public void createSheet(
        Guild guild,
        User user,
        String displayName,
        String imageUrl,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        if (guild == null) {
            onFailure.accept("This command only works inside a server.");
            return;
        }

        findSheetMessage(
            guild,
            user.getId(),
            message -> onFailure.accept("You already have a sheet in #" + CHANNEL_NAME + "."),
            ignored -> {
                SheetLocation location = getSheetLocation(guild, onFailure);
                if (location == null) {
                    return;
                }

                if (location.forumChannel() != null) {
                    location.forumChannel()
                        .createForumPost(displayName, MessageCreateData.fromContent(buildDefaultSheet(user, displayName, imageUrl)))
                        .queue(
                            post -> retrieveFirstMessage(
                                post.getThreadChannel(),
                                message -> onSuccess.accept(parseSheet(message)),
                                failure -> onFailure.accept("I created the post, but couldn't read the sheet message.")
                            ),
                            failure -> onFailure.accept("I couldn't create your sheet right now.")
                        );
                    return;
                }

                location.messageChannel().sendMessage(buildDefaultSheet(user, displayName, imageUrl)).queue(
                    message -> onSuccess.accept(parseSheet(message)),
                    failure -> onFailure.accept("I couldn't create your sheet right now.")
                );
            }
        );
    }

    public String buildTemplate(User user, String displayName, String imageUrl) {
        return stripGeneratedSection(buildDefaultSheet(user, displayName, imageUrl));
    }

    public void updateStat(
        Guild guild,
        String userId,
        String stat,
        int value,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                CharacterSheet sheet = parseSheet(message);
                String validationError = validateStatUpdate(sheet, stat, value);
                if (validationError != null) {
                    onFailure.accept(validationError);
                    return;
                }

                editSheetMessage(message, replaceLine(message.getContentRaw(), stat, Integer.toString(value)), onSuccess, onFailure);
            },
            onFailure
        );
    }

    public void adjustStat(
        Guild guild,
        String userId,
        String stat,
        int delta,
        int minValue,
        int maxValue,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                CharacterSheet sheet = parseSheet(message);
                int current = currentStatValue(sheet, stat);
                int updated = clampStatValue(sheet, stat, Math.max(minValue, Math.min(maxValue, current + delta)));
                editSheetMessage(message, replaceLine(message.getContentRaw(), stat, Integer.toString(updated)), onSuccess, onFailure);
            },
            onFailure
        );
    }

    public void updateMacro(
        Guild guild,
        String userId,
        String macroName,
        String formula,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        updateSheetLine(guild, userId, macroName, formula.trim(), onSuccess, onFailure);
    }

    public void updateImage(
        Guild guild,
        String userId,
        String imageUrl,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        String trimmed = imageUrl == null ? "" : imageUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            onFailure.accept("Image URLs must start with http:// or https://");
            return;
        }

        updateSheetLine(guild, userId, "image", trimmed, onSuccess, onFailure);
    }

    public void removeMacro(
        Guild guild,
        String userId,
        String macroName,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                String updated = removeLine(message.getContentRaw(), macroName);
                if (updated == null) {
                    onFailure.accept("I couldn't find macro `" + macroName + "` on your sheet.");
                    return;
                }
                editSheetMessage(message, updated, onSuccess, onFailure);
            },
            onFailure
        );
    }

    public void incrementHunger(
        Guild guild,
        String userId,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                CharacterSheet sheet = parseSheet(message);
                int newHunger = Math.min(5, sheet.hunger() + 1);
                editSheetMessage(message, replaceLine(message.getContentRaw(), "hunger", Integer.toString(newHunger)), onSuccess, onFailure);
            },
            onFailure
        );
    }

    public void restoreTracks(
        Guild guild,
        String userId,
        boolean restoreHealth,
        boolean restoreWillpower,
        Integer amount,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                String updated = stripGeneratedSection(message.getContentRaw());
                CharacterSheet sheet = parseSheet(message);
                if (restoreHealth) {
                    updated = restoreHealth(updated, sheet, amount);
                }
                if (restoreWillpower) {
                    updated = restoreWillpower(updated, sheet, amount);
                }
                editSheetMessage(message, updated, onSuccess, onFailure);
            },
            onFailure
        );
    }

    public void replaceSheet(
        Guild guild,
        String userId,
        String newContent,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                String sanitized = sanitizeSheetContent(newContent, userId, parseSheet(message));
                if (sanitized == null || sanitized.isBlank()) {
                    onFailure.accept("Your sheet reply was empty.");
                    return;
                }
                editSheetMessage(message, sanitized, onSuccess, onFailure);
            },
            onFailure
        );
    }

    public void deleteSheet(
        Guild guild,
        String userId,
        String characterName,
        Consumer<String> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> {
                CharacterSheet sheet = parseSheet(message);
                if (!message.getAuthor().isBot()) {
                    onFailure.accept("This sheet is not bot-owned, so I won't delete it automatically.");
                    return;
                }
                if (!namesMatch(sheet.name(), characterName)) {
                    onFailure.accept("I found your sheet, but the name didn't match `" + characterName + "`.");
                    return;
                }

                if (message.getChannel() instanceof ThreadChannel threadChannel) {
                    threadChannel.delete().queue(
                        ignored -> onSuccess.accept(characterName),
                        failure -> onFailure.accept("I couldn't delete your character sheet right now.")
                    );
                    return;
                }

                message.delete().queue(
                    ignored -> onSuccess.accept(characterName),
                    failure -> onFailure.accept("I couldn't delete your character sheet right now.")
                );
            },
            onFailure
        );
    }

    private void updateSheetLine(
        Guild guild,
        String userId,
        String key,
        String value,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        findSheetMessage(
            guild,
            userId,
            message -> editSheetMessage(message, replaceLine(message.getContentRaw(), key, value), onSuccess, onFailure),
            onFailure
        );
    }

    private void editSheetMessage(
        Message message,
        String newContent,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        if (!message.getAuthor().isBot()) {
            onFailure.accept("This sheet is not bot-owned, so I can't update it automatically.");
            return;
        }

        message.editMessage(newContent).queue(
            edited -> onSuccess.accept(parseSheet(edited)),
            failure -> onFailure.accept("I couldn't update your sheet right now.")
        );
    }

    private void findSheetMessage(Guild guild, String userId, Consumer<Message> onSuccess, Consumer<String> onFailure) {
        if (guild == null) {
            onFailure.accept("This command only works inside a server.");
            return;
        }

        SheetLocation location = getSheetLocation(guild, onFailure);
        if (location == null) {
            return;
        }

        if (location.forumChannel() != null) {
            List<ThreadChannel> activePosts = guild.getThreadChannels().stream()
                .filter(thread -> thread.getParentChannel().getIdLong() == location.forumChannel().getIdLong())
                .toList();
            location.forumChannel().retrieveArchivedPublicThreadChannels().queue(
                archivedPosts -> {
                    List<ThreadChannel> posts = new ArrayList<>(activePosts);
                    posts.addAll(archivedPosts);
                    searchForumPosts(posts, 0, userId, onSuccess, onFailure);
                },
                failure -> searchForumPosts(activePosts, 0, userId, onSuccess, onFailure)
            );
            return;
        }

        location.messageChannel().getHistory().retrievePast(HISTORY_SCAN_LIMIT).queue(
            messages -> {
                for (Message message : messages) {
                    if (isMatchingSheet(message, userId)) {
                        onSuccess.accept(message);
                        return;
                    }
                }
                onFailure.accept("No character sheet found in #" + CHANNEL_NAME + " for you yet.");
            },
            failure -> onFailure.accept("I couldn't read #" + CHANNEL_NAME + " right now.")
        );
    }

    private void searchForumPosts(
        List<ThreadChannel> posts,
        int index,
        String userId,
        Consumer<Message> onSuccess,
        Consumer<String> onFailure
    ) {
        if (index >= posts.size()) {
            onFailure.accept("No character sheet found in #" + CHANNEL_NAME + " for you yet.");
            return;
        }

        ThreadChannel post = posts.get(index);
        retrieveFirstMessage(
            post,
            message -> {
                if (message != null && isMatchingSheet(message, userId)) {
                    onSuccess.accept(message);
                    return;
                }
                searchForumPosts(posts, index + 1, userId, onSuccess, onFailure);
            },
            failure -> searchForumPosts(posts, index + 1, userId, onSuccess, onFailure)
        );
    }

    private void retrieveFirstMessage(
        GuildMessageChannel channel,
        Consumer<Message> onSuccess,
        Consumer<Throwable> onFailure
    ) {
        channel.getHistoryFromBeginning(1).queue(
            history -> {
                List<Message> messages = history.getRetrievedHistory();
                if (messages.isEmpty()) {
                    onFailure.accept(new IllegalStateException("No messages found."));
                    return;
                }
                    onSuccess.accept(messages.getFirst());
            },
            onFailure
        );
    }

    private SheetLocation getSheetLocation(Guild guild, Consumer<String> onFailure) {
        var textChannels = guild.getTextChannelsByName(CHANNEL_NAME, true);
        if (!textChannels.isEmpty()) {
            return new SheetLocation(textChannels.getFirst(), null);
        }

        var threadChannels = guild.getThreadChannels().stream()
            .filter(thread -> thread.getName().equalsIgnoreCase(CHANNEL_NAME))
            .toList();
        if (!threadChannels.isEmpty()) {
            return new SheetLocation(threadChannels.getFirst(), null);
        }

        var forumChannels = guild.getForumChannelsByName(CHANNEL_NAME, true);
        if (!forumChannels.isEmpty()) {
            return new SheetLocation(null, forumChannels.getFirst());
        }

        onFailure.accept("I couldn't find #" + CHANNEL_NAME + ".");
        return null;
    }

    private boolean isMatchingSheet(Message message, String userId) {
        if (message.getAuthor().getId().equals(userId)) {
            return true;
        }

        String expectedId = CharacterSheet.normalizeKey("user=" + userId);
        String expectedMention = CharacterSheet.normalizeKey("user=<@" + userId + ">");
        String expectedNicknameMention = CharacterSheet.normalizeKey("user=<@!" + userId + ">");

        return message.getContentRaw().lines()
            .map(CharacterSheet::normalizeKey)
            .anyMatch(line ->
                line.equalsIgnoreCase(expectedId)
                    || line.equalsIgnoreCase(expectedMention)
                    || line.equalsIgnoreCase(expectedNicknameMention)
            );
    }

    private boolean namesMatch(String actualName, String requestedName) {
        if (actualName == null || requestedName == null) {
            return false;
        }
        return CharacterSheet.normalizeKey(actualName).equals(CharacterSheet.normalizeKey(requestedName));
    }

    private CharacterSheet parseSheet(Message message) {
        String imageUrl = message.getAttachments().isEmpty()
            ? null
            : message.getAttachments().getFirst().getUrl();
        if ((imageUrl == null || imageUrl.isBlank()) && !message.getEmbeds().isEmpty()) {
            var firstEmbed = message.getEmbeds().getFirst();
            if (firstEmbed.getImage() != null) {
                imageUrl = firstEmbed.getImage().getUrl();
            } else if (firstEmbed.getThumbnail() != null) {
                imageUrl = firstEmbed.getThumbnail().getUrl();
            }
        }
        return parseSheetContent(message.getContentRaw(), imageUrl, message.getAuthor().isBot());
    }

    private String buildDefaultSheet(User user, String displayName, String imageUrl) {
        List<String> lines = new ArrayList<>();
        lines.add("user = " + user.getAsMention());
        lines.add("name = " + displayName);
        if (imageUrl != null && !imageUrl.isBlank()) {
            lines.add("image = " + imageUrl);
        }
        lines.add("");
        lines.add("strength = 0");
        lines.add("dexterity = 0");
        lines.add("stamina = 0");
        lines.add("charisma = 0");
        lines.add("manipulation = 0");
        lines.add("composure = 0");
        lines.add("intelligence = 0");
        lines.add("wits = 0");
        lines.add("resolve = 0");
        lines.add("");
        lines.add("athletics = 0");
        lines.add("brawl = 0");
        lines.add("craft = 0");
        lines.add("drive = 0");
        lines.add("firearms = 0");
        lines.add("larceny = 0");
        lines.add("melee = 0");
        lines.add("stealth = 0");
        lines.add("survival = 0");
        lines.add("");
        lines.add("animalken = 0");
        lines.add("etiquette = 0");
        lines.add("insight = 0");
        lines.add("intimidation = 0");
        lines.add("leadership = 0");
        lines.add("performance = 0");
        lines.add("persuasion = 0");
        lines.add("streetwise = 0");
        lines.add("subterfuge = 0");
        lines.add("");
        lines.add("academics = 0");
        lines.add("awareness = 0");
        lines.add("finance = 0");
        lines.add("investigation = 0");
        lines.add("medicine = 0");
        lines.add("occult = 0");
        lines.add("politics = 0");
        lines.add("science = 0");
        lines.add("technology = 0");
        lines.add("");
        lines.add("---- MACRO ----");
        return appendDisplaySection(String.join("\n", lines));
    }

    private String replaceLine(String content, String key, String value) {
        List<String> lines = new ArrayList<>(stripGeneratedSection(content).lines().toList());
        String normalizedKey = CharacterSheet.normalizeKey(key);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.contains("=")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            if (CharacterSheet.normalizeKey(parts[0]).equals(normalizedKey)) {
                lines.set(i, key + " = " + value);
                return appendDisplaySection(String.join("\n", lines));
            }
        }

        int macroDivider = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equalsIgnoreCase("---- MACRO ----")) {
                macroDivider = i;
                break;
            }
        }

        if (macroDivider >= 0 && !isCoreStat(normalizedKey)) {
            lines.add(macroDivider + 1, key + " = " + value);
        } else {
            lines.add(key + " = " + value);
        }

        return appendDisplaySection(String.join("\n", lines));
    }

    private String removeLine(String content, String key) {
        List<String> lines = new ArrayList<>(stripGeneratedSection(content).lines().toList());
        String normalizedKey = CharacterSheet.normalizeKey(key);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.contains("=")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            if (CharacterSheet.normalizeKey(parts[0]).equals(normalizedKey)) {
                lines.remove(i);
                return appendDisplaySection(String.join("\n", lines));
            }
        }

        return null;
    }

    private String sanitizeSheetContent(String content, String userId, CharacterSheet existingSheet) {
        String normalized = stripGeneratedSection(stripCodeFence(content)).trim();
        if (normalized.isEmpty()) {
            return null;
        }

        List<String> lines = new ArrayList<>(normalized.lines().toList());
        replaceOrInsertLine(lines, "user", "<@" + userId + ">");

        if (existingSheet.name() != null && lines.stream().noneMatch(line -> CharacterSheet.normalizeKey(line).startsWith("name="))) {
            replaceOrInsertLine(lines, "name", existingSheet.name());
        }

        if (existingSheet.imageUrl() != null && !existingSheet.imageUrl().isBlank()
            && lines.stream().noneMatch(line -> CharacterSheet.normalizeKey(line).startsWith("image="))) {
            int insertionIndex = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (CharacterSheet.normalizeKey(lines.get(i)).startsWith("name=")) {
                    insertionIndex = i + 1;
                    break;
                }
            }
            lines.add(insertionIndex, "image = " + existingSheet.imageUrl());
        }

        return appendDisplaySection(String.join("\n", lines));
    }

    private void replaceOrInsertLine(List<String> lines, String key, String value) {
        String normalizedKey = CharacterSheet.normalizeKey(key);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.contains("=")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            if (CharacterSheet.normalizeKey(parts[0]).equals(normalizedKey)) {
                lines.set(i, key + " = " + value);
                return;
            }
        }

        lines.addFirst(key + " = " + value);
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) {
            return content;
        }

        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return "";
        }

        return trimmed.substring(firstLineBreak + 1, trimmed.length() - 3).trim();
    }

    private String stripGeneratedSection(String content) {
        int markerIndex = content.indexOf(TRACKERS_MARKER);
        if (markerIndex < 0) {
            return content;
        }
        return content.substring(0, markerIndex).trim();
    }

    private String appendDisplaySection(String baseContent) {
        String normalizedBase = stripGeneratedSection(baseContent).trim();
        CharacterSheet sheet = parseRawSheet(normalizedBase);
        StringBuilder builder = new StringBuilder()
            .append(normalizedBase)
            .append("\n\n")
            .append(TRACKERS_MARKER)
            .append("\n")
            .append(sheet.describe());
        if (sheet.imageUrl() != null && !sheet.imageUrl().isBlank()) {
            builder.append("\n").append(sheet.imageUrl());
        }
        return builder.toString();
    }

    private CharacterSheet parseRawSheet(String content) {
        return parseSheetContent(content, null, true);
    }

    private CharacterSheet parseSheetContent(String content, String fallbackImageUrl, boolean botOwned) {
        Map<String, String> values = new LinkedHashMap<>();
        String name = null;

        for (String rawLine : content.lines().toList()) {
            String line = rawLine.trim();
            if (!line.contains("=")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            String key = CharacterSheet.normalizeKey(parts[0]);
            switch (key) {
                case "user" -> {
                    continue;
                }
                case "name" -> {
                    name = parts[1].trim();
                    continue;
                }
                case "image" -> {
                    values.put(key, parts[1].trim());
                    continue;
                }
            }

            values.put(key, CharacterSheet.normalizeKey(parts[1]));
        }

        String imageUrl = values.get("image");
        if ((imageUrl == null || imageUrl.isEmpty()) && fallbackImageUrl != null && !fallbackImageUrl.isBlank()) {
            imageUrl = fallbackImageUrl;
        }

        return new CharacterSheet(values, imageUrl, name, botOwned);
    }

    private boolean isCoreStat(String normalizedKey) {
        return !normalizedKey.contains("+")
            && !normalizedKey.equals("auspex")
            && !normalizedKey.equals("celerity")
            && !normalizedKey.equals("dominate")
            && !normalizedKey.equals("fortitude")
            && !normalizedKey.equals("obfuscate")
            && !normalizedKey.equals("potence")
            && !normalizedKey.equals("presence");
    }

    private String restoreHealth(String content, CharacterSheet sheet, Integer amount) {
        if (amount == null) {
            String cleared = replaceLine(content, "health_superficial", "0");
            return replaceLine(cleared, "health_aggravated", "0");
        }

        int remaining = Math.max(0, amount);
        int superficial = sheet.getValue("health_superficial").orElse(0);
        int aggravated = sheet.getValue("health_aggravated").orElse(0);

        int healedSuperficial = Math.min(superficial, remaining);
        superficial -= healedSuperficial;
        remaining -= healedSuperficial;

        int healedAggravated = Math.min(aggravated, remaining);
        aggravated -= healedAggravated;

        String updated = replaceLine(content, "health_superficial", Integer.toString(superficial));
        return replaceLine(updated, "health_aggravated", Integer.toString(aggravated));
    }

    private String restoreWillpower(String content, CharacterSheet sheet, Integer amount) {
        if (amount == null) {
            String removed = removeLine(content, "willpower");
            return removed == null || removed.isBlank() ? content : removed;
        }

        int restored = Math.min(sheet.willpowerMax(), sheet.currentWillpower() + Math.max(0, amount));
        return replaceLine(content, "willpower", Integer.toString(restored));
    }

    private int currentStatValue(CharacterSheet sheet, String stat) {
        String normalized = CharacterSheet.normalizeKey(stat);
        if (normalized.equals("willpower")) {
            return sheet.currentWillpower();
        }
        return sheet.getValue(stat).orElse(0);
    }

    private int clampStatValue(CharacterSheet sheet, String stat, int value) {
        String normalized = CharacterSheet.normalizeKey(stat);
        return switch (normalized) {
            case "hunger" -> clamp(value, 0, 5);
            case "willpower" -> clamp(value, 0, Math.max(0, sheet.willpowerMax()));
            case "healthsuperficial" -> {
                int aggravated = sheet.getValue("health_aggravated").orElse(0);
                yield clamp(value, 0, Math.max(0, sheet.healthMax() - aggravated));
            }
            case "healthaggravated" -> {
                int superficial = sheet.getValue("health_superficial").orElse(0);
                yield clamp(value, 0, Math.max(0, sheet.healthMax() - superficial));
            }
            default -> Math.max(0, value);
        };
    }

    private String validateStatUpdate(CharacterSheet sheet, String stat, int value) {
        String normalized = CharacterSheet.normalizeKey(stat);
        if (value < 0) {
            return "Stats cannot be negative.";
        }

        return switch (normalized) {
            case "hunger" -> value > 5 ? "Hunger cannot be greater than 5." : null;
            case "willpower" -> value > sheet.willpowerMax()
                ? "Willpower cannot be greater than composure + resolve (" + sheet.willpowerMax() + ")."
                : null;
            case "healthsuperficial" -> validateHealthTrack(
                value,
                sheet.getValue("health_aggravated").orElse(0),
                sheet.healthMax()
            );
            case "healthaggravated" -> validateHealthTrack(
                value,
                sheet.getValue("health_superficial").orElse(0),
                sheet.healthMax()
            );
            default -> null;
        };
    }

    private String validateHealthTrack(int updatedValue, int otherTrack, int maxHealth) {
        if (updatedValue + otherTrack > maxHealth) {
            return "Total health damage cannot be greater than stamina + 3 (" + maxHealth + ").";
        }
        return updatedValue < 0 ? "Health damage cannot be negative." : null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SheetLocation(GuildMessageChannel messageChannel, ForumChannel forumChannel) {
    }
}
