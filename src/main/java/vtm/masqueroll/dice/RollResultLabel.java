package vtm.masqueroll.dice;

public enum RollResultLabel {
    NONE(""),
    CRITICAL("CRITICAL"),
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    CRITICAL_SUCCESS("CRITICAL SUCCESS"),
    MESSY_CRITICAL("MESSY CRITICAL"),
    BESTIAL_FAILURE("BESTIAL FAILURE");

    private final String text;

    RollResultLabel(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
