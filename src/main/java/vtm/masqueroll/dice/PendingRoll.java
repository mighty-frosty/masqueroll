package vtm.masqueroll.dice;

import vtm.masqueroll.CharacterSheet;

public record PendingRoll(
    String ownerId,
    int pool,
    int hunger,
    Integer difficulty,
    RollSummary summary,
    CharacterSheet sheet
) {
}
