package app.bytebrief.domain;

/** Origin of a content item. The {@link #code} is persisted, so existing codes must never change. */
public enum SourceType {
    GITHUB_TRENDING("gh_trending", "GitHub Trending"),
    HACKER_NEWS("hn", "Hacker News"),
    RSS("rss", "RSS"),
    GITHUB_RELEASE("gh_release", "GitHub Releases"),
    XDA_FORUM("xda", "XDA Forums");

    public final String code;
    public final String label;

    SourceType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static SourceType fromCode(String code) {
        for (SourceType t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new IllegalArgumentException("Unknown source type: " + code);
    }
}
