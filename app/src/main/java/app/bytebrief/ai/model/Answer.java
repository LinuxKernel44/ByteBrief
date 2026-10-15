package app.bytebrief.ai.model;

/** Answer to a question about one item. */
public final class Answer {
    public final boolean answerable;
    /** The answer, or an explanation that the text does not contain it. */
    public final String answer;
    /** A short quote from the source the answer relies on; empty when not answerable. */
    public final String evidence;
    /** True when {@link #evidence} was found verbatim (ignoring case and punctuation) in the source text. */
    public final boolean grounded;
    public final boolean truncatedInput;

    public Answer(boolean answerable, String answer, String evidence, boolean grounded, boolean truncatedInput) {
        this.answerable = answerable;
        this.answer = answer;
        this.evidence = evidence;
        this.grounded = grounded;
        this.truncatedInput = truncatedInput;
    }
}
