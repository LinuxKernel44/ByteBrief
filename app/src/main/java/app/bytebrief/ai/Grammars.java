package app.bytebrief.ai;

import static app.bytebrief.ai.Gbnf.array;
import static app.bytebrief.ai.Gbnf.bool;
import static app.bytebrief.ai.Gbnf.field;
import static app.bytebrief.ai.Gbnf.integer;
import static app.bytebrief.ai.Gbnf.object;
import static app.bytebrief.ai.Gbnf.str;

/** The output schemas, as grammars. Field order here is the order the model emits them. */
final class Grammars {
    /** Longest summary the grammar allows; validation applies tighter sentence-level rules. */
    static final int SUMMARY_MAX = 1400;

    static final String SUMMARY = Gbnf.grammar(
            field("sufficient", bool()),
            field("summary", str(0, SUMMARY_MAX)),
            field("interpretation", str(0, 500)));

    static final String TAKEAWAYS = Gbnf.grammar(
            field("sufficient", bool()),
            field("main_contribution", str(0, 300)),
            field("key_points", array(str(1, 220), 0, 6)),
            field("technologies", array(str(1, 60), 0, 8)),
            field("audience", str(0, 200)),
            field("use_cases", array(str(1, 200), 0, 5)),
            field("caveats", array(str(1, 250), 0, 5)));

    static final String EXPLANATION = Gbnf.grammar(
            field("sufficient", bool()),
            field("explanation", str(0, 1600)),
            field("inferred", array(str(1, 220), 0, 5)));

    static final String ANSWER = Gbnf.grammar(
            field("answerable", bool()),
            field("answer", str(0, 900)),
            field("evidence", str(0, 300)));

    static final String BRIEFING = Gbnf.grammar(
            field("headline", str(1, 160)),
            field("entries", array(object(
                    field("id", integer()),
                    field("why", str(1, 240))), 1, 12)),
            field("themes", array(object(
                    field("name", str(1, 40)),
                    field("ids", array(integer(), 1, 12))), 0, 4)));

    private Grammars() {}
}
