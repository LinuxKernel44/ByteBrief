package app.bytebrief.ai.model;

/** One pre-selected item offered to the briefing model. {@code number} is 1-based and local to one briefing. */
public final class BriefingCandidate {
    public final int number;
    public final long itemId;
    public final String title;
    public final String sourceLabel;
    public final String snippet;
    public final String topics;

    public BriefingCandidate(int number, long itemId, String title, String sourceLabel, String snippet, String topics) {
        this.number = number;
        this.itemId = itemId;
        this.title = title;
        this.sourceLabel = sourceLabel;
        this.snippet = snippet;
        this.topics = topics;
    }
}
