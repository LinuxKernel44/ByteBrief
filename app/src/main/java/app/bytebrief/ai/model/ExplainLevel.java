package app.bytebrief.ai.model;

public enum ExplainLevel {
    BEGINNER("beginner", "Beginner", "Assume no prior knowledge. Define every technical term in plain language and use a simple analogy if it helps."),
    INTERMEDIATE("intermediate", "Intermediate", "Assume the reader is a working developer unfamiliar with this specific topic. Explain how it works and why it matters."),
    ADVANCED("advanced", "Advanced", "Assume an expert reader. Focus on design trade-offs, internals, limitations and comparisons to alternatives.");

    public final String code;
    public final String label;
    public final String instruction;

    ExplainLevel(String code, String label, String instruction) {
        this.code = code;
        this.label = label;
        this.instruction = instruction;
    }

    public static ExplainLevel fromCode(String code) {
        for (ExplainLevel l : values()) if (l.code.equals(code)) return l;
        throw new IllegalArgumentException("Unknown level " + code);
    }
}
