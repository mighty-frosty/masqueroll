package vtm.masqueroll;

public record DieResult(int value, boolean hunger) {

    public String format(DiceDisplayConfig displayConfig) {
        if (value >= 6 && value <= 9) {
            return hunger ? displayConfig.hungerSuccessEmoji() : displayConfig.normalSuccessEmoji();
        }
        if (value == 10) {
            return hunger ? displayConfig.hungerTenEmoji() : displayConfig.normalTenEmoji();
        }
        return hunger ? displayConfig.hungerFailureEmoji() : displayConfig.normalFailureEmoji();
    }
}
