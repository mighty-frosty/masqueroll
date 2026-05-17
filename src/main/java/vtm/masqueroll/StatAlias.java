package vtm.masqueroll;

public enum StatAlias {

    // Attributes
    STR("str", "strength"),
    DEX("dex", "dexterity"),
    STA("sta", "stamina"),
    CHA("cha", "charisma"),
    MAN("man", "manipulation"),
    COM("com", "composure"),
    INT("int", "intelligence"),
    WIT("wit", "wits"),
    RES("res", "resolve"),

    // Physical skills
    ATH("ath", "athletics"),
    BRA("bra", "brawl"),
    CRA("cra", "craft"),
    DRI("dri", "drive"),
    FIR("fir", "firearms"),
    LAR("lar", "larceny"),
    MEL("mel", "melee"),
    STE("ste", "stealth"),
    SUR("sur", "survival"),

    // Social skills
    ANI("ani", "animalken"),
    ETI("eti", "etiquette"),
    INS("ins", "insight"),
    ITI("iti", "intimidation"),
    LEA("lea", "leadership"),
    PRF("prf", "performance"),
    PER("per", "persuasion"),
    STW("stw", "streetwise"),
    SUB("sub", "subterfuge"),

    // Mental skills
    ACA("aca", "academics"),
    AWA("awa", "awareness"),
    FIN("fin", "finance"),
    INV("inv", "investigation"),
    MED("med", "medicine"),
    OCC("occ", "occult"),
    POL("pol", "politics"),
    SCI("sci", "science"),
    TEC("tec", "technology");

    private final String abbreviation;
    private final String fullName;

    StatAlias(String abbreviation, String fullName) {
        this.abbreviation = abbreviation;
        this.fullName = fullName;
    }

    public static String resolve(String key) {
        for (StatAlias alias : values()) {
            if (alias.abbreviation.equals(key)) {
                return alias.fullName;
            }
        }
        return key;
    }
}