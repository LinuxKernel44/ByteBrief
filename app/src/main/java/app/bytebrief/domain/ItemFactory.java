package app.bytebrief.domain;

import androidx.annotation.Nullable;

import java.time.Clock;
import java.util.Collection;

import app.bytebrief.data.db.ContentItem;

/** Builds fully-populated {@link ContentItem}s so every source applies identical normalization. */
public final class ItemFactory {
    private final Clock clock;

    public ItemFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param url the item's link as given by the source; invalid or non-http(s) URLs are rejected with null
     * @param bodyText text to store; its length decides the {@link BodyState}
     */
    @Nullable
    public ContentItem create(SourceType type, String sourceId, String title, String url, @Nullable String author,
                              long publishedAtMillis, @Nullable String description, @Nullable String bodyText,
                              int bodyState, @Nullable Collection<String> topics, @Nullable String language) {
        String canonical = UrlCanonicalizer.canonicalize(url);
        String dedup = UrlCanonicalizer.dedupKey(url);
        String cleanTitle = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        if (canonical == null || dedup == null || cleanTitle.isEmpty() || sourceId == null || sourceId.isEmpty()) {
            return null;
        }
        long now = clock.millis();
        ContentItem item = new ContentItem();
        item.uid = ContentHasher.uid(type.code, sourceId);
        item.sourceType = type.code;
        item.sourceId = sourceId;
        item.title = cleanTitle;
        item.canonicalUrl = canonical;
        item.originalUrl = url.trim();
        item.dedupKey = dedup;
        item.author = blankToNull(author);
        item.publishedAt = publishedAtMillis > 0 ? Math.min(publishedAtMillis, now) : now;
        item.retrievedAt = now;
        item.updatedAt = now;
        item.description = blankToNull(description);
        item.bodyText = blankToNull(bodyText);
        item.bodyState = item.bodyText == null ? (item.description == null ? BodyState.NONE : BodyState.SNIPPET) : bodyState;
        item.topics = Topics.join(topics);
        item.language = blankToNull(language);
        refreshHash(item);
        return item;
    }

    /** Recomputes the hash from the text a model would actually see. */
    public static void refreshHash(ContentItem item) {
        String text = item.bodyText != null ? item.bodyText : item.description;
        item.contentHash = ContentHasher.contentHash(item.title, text);
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
