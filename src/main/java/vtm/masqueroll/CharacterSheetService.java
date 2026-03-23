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

    public void updateStat(
        Guild guild,
        String userId,
        String stat,
        int value,
        Consumer<CharacterSheet> onSuccess,
        Consumer<String> onFailure
    ) {
        if (value < 0) {
            onFailure.accept("Stats cannot be negative.");
            return;
        }
        if (CharacterSheet.normalizeKey(stat).equals("hunger") && value > 5) {
            onFailure.accept("Hunger cannot be greater than 5.");
            return;
        }

        updateSheetLine(guild, userId, stat, Integer.toString(value), onSuccess, onFailure);
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
            List<ThreadChannel> posts = guild.getThreadChannels().stream()
                .filter(thread -> thread.getParentChannel().getIdLong() == location.forumChannel().getIdLong())
                .toList();

            searchForumPosts(posts, 0, userId, onSuccess, onFailure);
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

    private CharacterSheet parseSheet(Message message) {
        Map<String, String> values = new LinkedHashMap<>();
        String content = message.getContentRaw();
        String name = null;

        for (String rawLine : content.lines().toList()) {
            String line = rawLine.trim();
            if (line.isEmpty() || !line.contains("=")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            String key = CharacterSheet.normalizeKey(parts[0]);
            if (key.equals("user")) {
                continue;
            }
            if (key.equals("name")) {
                name = parts[1].trim();
                continue;
            }
            if (key.equals("image")) {
                values.put(key, parts[1].trim());
                continue;
            }

            values.put(key, CharacterSheet.normalizeKey(parts[1]));
        }

        String imageUrl = values.get("image");
        if ((imageUrl == null || imageUrl.isEmpty()) && !message.getAttachments().isEmpty()) {
            imageUrl = message.getAttachments().getFirst().getUrl();
        }
        return new CharacterSheet(values, imageUrl, name, message.getAuthor().isBot());
    }

    private String buildDefaultSheet(User user, String displayName, String imageUrl) {
        List<String> lines = new ArrayList<>();
        lines.add("user = " + user.getAsMention());
        lines.add("name = " + displayName);
        if (imageUrl != null && !imageUrl.isBlank()) {
            lines.add("image = " + imageUrl);
        }
        lines.add("hunger = 0");
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
        return String.join("\n", lines);
    }

    private String replaceLine(String content, String key, String value) {
        List<String> lines = new ArrayList<>(content.lines().toList());
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
                return String.join("\n", lines);
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

        return String.join("\n", lines);
    }

    private String removeLine(String content, String key) {
        List<String> lines = new ArrayList<>(content.lines().toList());
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
                return String.join("\n", lines);
            }
        }

        return null;
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

    private record SheetLocation(GuildMessageChannel messageChannel, ForumChannel forumChannel) {
    }
}
