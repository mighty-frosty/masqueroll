package vtm.abbioccatore;

public record DieResult(int value, boolean hunger) {

    public String format() {
        if (hunger) {
            return "**H" + value + "**";
        }
        return Integer.toString(value);
    }
}
