package app.bytebrief.ranking;

import java.util.List;

import app.bytebrief.data.db.ContentListItem;

/** An item with its score and the human-readable reasons behind it. */
public final class RankedItem {
    public static final class Reason {
        public final String label;
        /** Signed contribution to the score; comparable across reasons of the same item. */
        public final double contribution;

        Reason(String label, double contribution) {
            this.label = label;
            this.contribution = contribution;
        }
    }

    public final ContentListItem entry;
    public final double score;
    public final List<Reason> reasons;

    RankedItem(ContentListItem entry, double score, List<Reason> reasons) {
        this.entry = entry;
        this.score = score;
        this.reasons = reasons;
    }
}
