package vtm.masqueroll.dice;

import java.util.List;

public record RollSummary(
    List<DieResult> dice,
    int successes,
    int criticalPairs,
    int hungerDiceUsed,
    boolean success,
    boolean messyCritical,
    boolean bestialFailure,
    RollResultLabel resultLabel,
    String outcome
) {

    public String formatDice(DiceDisplayConfig displayConfig) {
        return dice.stream()
            .map(die -> die.format(displayConfig))
            .reduce((left, right) -> left + " " + right)
            .orElse("-");
    }
}
