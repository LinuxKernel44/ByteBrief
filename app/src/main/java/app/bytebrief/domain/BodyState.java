package app.bytebrief.domain;

/** How much of an item's text is stored locally. Drives the "partially cached" indicator. */
public final class BodyState {
    /** Only title and metadata are stored. */
    public static final int NONE = 0;
    /** A description/excerpt supplied by the source (not the full article). */
    public static final int SNIPPET = 1;
    /** Full text (article body, README or release notes) is stored. */
    public static final int FULL = 2;

    private BodyState() {}
}
