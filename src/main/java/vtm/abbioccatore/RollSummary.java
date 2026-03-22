package vtm.abbioccatore;

import java.util.List;

public record RollSummary(
    List<DieResult> dice,
    int successes,
    int criticalPairs,
    boolean messyCritical,
    boolean bestialFailure,
    String outcome
) {

    public String formatDice() {
        return dice.stream()
            .map(DieResult::format)
            .reduce((left, right) -> left + " " + right)
            .orElse("-");
    }
}
