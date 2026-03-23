package vtm.masqueroll.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class VtmDiceRoller {

    private VtmDiceRoller() {
    }

    public static RollSummary roll(int pool, int hunger, Integer difficulty) {
        validate(pool, hunger, difficulty);
        int hungerDiceUsed = Math.min(hunger, pool);

        List<DieResult> dice = new ArrayList<>(pool);
        for (int i = 0; i < hungerDiceUsed; i++) {
            dice.add(new DieResult(randomDie(), true));
        }
        for (int i = hungerDiceUsed; i < pool; i++) {
            dice.add(new DieResult(randomDie(), false));
        }

        return evaluate(dice, difficulty, hungerDiceUsed);
    }

    public static RollSummary rerollFailedNormalDice(RollSummary summary, Integer difficulty, int maxDice) {
        List<DieResult> updatedDice = new ArrayList<>(summary.dice());
        int rerolled = 0;

        for (int i = 0; i < updatedDice.size() && rerolled < maxDice; i++) {
            DieResult die = updatedDice.get(i);
            if (!die.hunger() && die.value() < 6) {
                updatedDice.set(i, new DieResult(randomDie(), false));
                rerolled++;
            }
        }

        return evaluate(updatedDice, difficulty, summary.hungerDiceUsed());
    }

    public static RollSummary rerollNormalSuccessesForCrit(RollSummary summary, Integer difficulty, int maxDice) {
        List<DieResult> updatedDice = new ArrayList<>(summary.dice());
        int rerolled = 0;

        for (int i = 0; i < updatedDice.size() && rerolled < maxDice; i++) {
            DieResult die = updatedDice.get(i);
            if (!die.hunger() && die.value() >= 6 && die.value() <= 9) {
                updatedDice.set(i, new DieResult(randomDie(), false));
                rerolled++;
            }
        }

        return evaluate(updatedDice, difficulty, summary.hungerDiceUsed());
    }

    public static RollSummary rerollNormalCriticalsForMessy(RollSummary summary, Integer difficulty) {
        List<DieResult> updatedDice = new ArrayList<>(summary.dice());
        int hungerTens = (int) summary.dice().stream()
            .filter(die -> die.hunger() && die.value() == 10)
            .count();
        int rerolled = 0;

        for (int i = 0; i < updatedDice.size(); i++) {
            DieResult die = updatedDice.get(i);
            if (rerolled >= hungerTens) {
                break;
            }
            if (!die.hunger() && die.value() == 10) {
                updatedDice.set(i, new DieResult(randomDie(), false));
                rerolled++;
            }
        }

        return evaluate(updatedDice, difficulty, summary.hungerDiceUsed());
    }

    public static int countFailedNormalRerolls(RollSummary summary) {
        return (int) summary.dice().stream()
            .filter(die -> !die.hunger() && die.value() < 6)
            .limit(3)
            .count();
    }

    public static int countNormalSuccessRerollsForCrit(RollSummary summary) {
        return (int) summary.dice().stream()
            .filter(die -> !die.hunger() && die.value() >= 6 && die.value() <= 9)
            .limit(3)
            .count();
    }

    public static int countNormalCriticalRerollsForMessy(RollSummary summary) {
        int hungerTens = (int) summary.dice().stream()
            .filter(die -> die.hunger() && die.value() == 10)
            .count();
        int normalTens = (int) summary.dice().stream()
            .filter(die -> !die.hunger() && die.value() == 10)
            .count();

        if (!summary.messyCritical()) {
            return 0;
        }

        return Math.min(hungerTens, normalTens);
    }

    private static RollSummary evaluate(List<DieResult> dice, Integer difficulty, int hungerDiceUsed) {
        int successes = 0;
        int tens = 0;
        int hungerTens = 0;
        int hungerOnes = 0;

        for (DieResult die : dice) {
            if (die.value() >= 6) {
                successes++;
            }
            if (die.value() == 10) {
                tens++;
                if (die.hunger()) {
                    hungerTens++;
                }
            }
            if (die.hunger() && die.value() == 1) {
                hungerOnes++;
            }
        }

        int criticalPairs = tens / 2;
        successes += criticalPairs * 2;

        boolean messyCritical = criticalPairs > 0 && hungerTens > 0;
        boolean success = difficulty == null || successes >= difficulty;
        boolean failedTest = difficulty != null && !success;
        boolean bestialFailure = failedTest && hungerOnes > 0;

        return new RollSummary(
            List.copyOf(dice),
            successes,
            criticalPairs,
            hungerDiceUsed,
            success,
            messyCritical,
            bestialFailure,
            buildResultLabel(criticalPairs, messyCritical, bestialFailure, difficulty, success),
            buildOutcome(successes, criticalPairs, messyCritical, bestialFailure, difficulty)
        );
    }

    private static int randomDie() {
        return ThreadLocalRandom.current().nextInt(1, 11);
    }

    private static void validate(int pool, int hunger, Integer difficulty) {
        if (pool < 1) {
            throw new IllegalArgumentException("Pool must be at least 1.");
        }
        if (hunger < 0) {
            throw new IllegalArgumentException("Hunger cannot be negative.");
        }
        if (hunger > 5) {
            throw new IllegalArgumentException("Hunger cannot be greater than 5.");
        }
        if (difficulty != null && difficulty < 1) {
            throw new IllegalArgumentException("Difficulty must be at least 1 when provided.");
        }
    }

    private static String buildOutcome(
        int successes,
        int criticalPairs,
        boolean messyCritical,
        boolean bestialFailure,
        Integer difficulty
    ) {
        if (difficulty == null) {
            return "No difficulty set.";
        }
        if (successes >= difficulty) {
            int margin = successes - difficulty;
            if (messyCritical) {
                return "Success with a messy critical (+" + margin + " margin).";
            }
            if (criticalPairs > 0) {
                return "Critical success (+" + margin + " margin).";
            }
            return "Success (+" + margin + " margin).";
        }
        if (bestialFailure) {
            return "Bestial failure.";
        }

        int deficit = difficulty - successes;
        return "Failure (-" + deficit + " short).";
    }

    private static RollResultLabel buildResultLabel(
        int criticalPairs,
        boolean messyCritical,
        boolean bestialFailure,
        Integer difficulty,
        boolean success
    ) {
        if (difficulty == null) {
            if (messyCritical) {
                return RollResultLabel.MESSY_CRITICAL;
            }
            if (criticalPairs > 0) {
                return RollResultLabel.CRITICAL;
            }
            return RollResultLabel.NONE;
        }
        if (bestialFailure) {
            return RollResultLabel.BESTIAL_FAILURE;
        }
        if (messyCritical) {
            return RollResultLabel.MESSY_CRITICAL;
        }
        if (criticalPairs > 0 && success) {
            return RollResultLabel.CRITICAL_SUCCESS;
        }
        if (success) {
            return RollResultLabel.SUCCESS;
        }
        return RollResultLabel.FAILURE;
    }
}
