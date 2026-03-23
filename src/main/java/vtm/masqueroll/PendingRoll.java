package vtm.masqueroll;

public record PendingRoll(
    String ownerId,
    int pool,
    int hunger,
    Integer difficulty,
    RollSummary summary,
    String sheetImageUrl,
    String characterName
) {
}
