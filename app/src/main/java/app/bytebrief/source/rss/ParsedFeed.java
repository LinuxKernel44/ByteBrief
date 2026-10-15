package app.bytebrief.source.rss;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Format-neutral result of parsing an RSS or Atom document. */
public final class ParsedFeed {
    public static final class Entry {
        @Nullable public String id;
        @Nullable public String title;
        @Nullable public String link;
        @Nullable public String author;
        /** Epoch millis, or 0 when the entry carried no parseable date. */
        public long publishedAt;
        /** Teaser or summary (may contain HTML). */
        @Nullable public String summaryHtml;
        /** Full content (may contain HTML) when the feed supplies it. */
        @Nullable public String contentHtml;
        @Nullable public String imageUrl;
        public final List<String> categories = new ArrayList<>();
    }

    @Nullable public String title;
    @Nullable public String siteUrl;
    public final List<Entry> entries = new ArrayList<>();
    /** True when the document ended abnormally and only the leading entries were recovered. */
    public boolean truncated;
}
