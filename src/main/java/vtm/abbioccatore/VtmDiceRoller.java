package vtm.abbioccatore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class VtmDiceRoller {

    private VtmDiceRoller() {
    }

    public static RollSummary roll(int pool, int hunger, Integer difficulty) {
        validate(pool, hunger, difficulty);

        List<DieResult> dice = new ArrayList<>(pool);
        for (int i = 0; i < hunger; i++) {
            dice.add(new DieResult(randomDie(), true));
        }
        for (int i = hunger; i < pool; i++) {
            dice.add(new DieResult(randomDie(), false));
        }

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
        boolean failedTest = difficulty != null && successes < difficulty;
        boolean bestialFailure = failedTest && hungerOnes > 0;

        return new RollSummary(
            List.copyOf(dice),
            successes,
            criticalPairs,
            messyCritical,
            bestialFailure,
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
        if (hunger > pool) {
            throw new IllegalArgumentException("Hunger cannot be greater than the pool.");
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
}
