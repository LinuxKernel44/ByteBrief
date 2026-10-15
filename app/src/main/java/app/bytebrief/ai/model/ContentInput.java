package app.bytebrief.ai.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The untrusted material a model is asked about. Everything here comes from the network and is
 * treated as data, never as instructions.
 */
public final class ContentInput {
    public final long itemId;
    public final String title;
    public final String sourceLabel;
    public final String url;
    /** Facts reported by the source API or feed (stars, license, tag...), one per line; may be empty. */
    public final String metadata;
    /** Plain text of the article, README, release notes or post. May be only a short snippet. */
    public final String text;
    /** True when {@link #text} is only a teaser or description, not the complete content. */
    public final boolean textIsPartial;
    /** Top Hacker News comments, if the discussion was retrieved. */
    public final List<String> discussion;

    public ContentInput(long itemId, String title, String sourceLabel, String url, String metadata, String text,
                        boolean textIsPartial, @Nullable List<String> discussion) {
        this.itemId = itemId;
        this.title = title;
        this.sourceLabel = sourceLabel;
        this.url = url;
        this.metadata = metadata;
        this.text = text;
        this.textIsPartial = textIsPartial;
        this.discussion = discussion == null ? Collections.emptyList() : new ArrayList<>(discussion);
    }

    /** Document body the model sees: the text, then the discussion if present. */
    public String documentText() {
        if (discussion.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("Discussion (top comments):\n");
        for (int i = 0; i < discussion.size(); i++) sb.append("- ").append(discussion.get(i).replace('\n', ' ')).append('\n');
        return sb.toString();
    }
}
