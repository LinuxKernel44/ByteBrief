package app.bytebrief.ai;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.BriefingCandidate;
import app.bytebrief.ai.model.ContentInput;
import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;

/**
 * Runtime-independent AI logic: prompt construction, context budgeting, chunking, grammar-constrained
 * generation, validation and a bounded repair/retry. It never fabricates output: every success value
 * comes from validated model text, and every failure is reported as a typed {@link AiResult}.
 */
final class AiPipeline {
    /** Below this many characters of text there is nothing to summarize, and the model is not asked. */
    static final int MIN_SUMMARY_CHARS = 240;
    static final int MIN_QA_CHARS = 120;
    private static final int MAX_ATTEMPTS = 2;
    private static final int RESERVE_TOKENS = 48;
    /** A long document is condensed in at most this many parts; anything beyond is dropped and flagged. */
    private static final int MAX_CHUNKS = 4;
    private static final int CONDENSE_MAX_TOKENS = 220;

    /** Progress reporting; all methods are called on the engine thread. */
    interface Progress {
        void stage(String description);

        void text(String piece);

        Progress NONE = new Progress() {
            @Override public void stage(String d) { }
            @Override public void text(String p) { }
        };
    }

    private interface Parser<T> {
        OutputParsers.Parsed<T> parse(JSONObject o, boolean truncated) throws ValidationException, JSONException;
    }

    private final TextGenerator gen;
    private final PromptBuilder prompts;

    AiPipeline(TextGenerator gen, PromptBuilder prompts) {
        this.gen = gen;
        this.prompts = prompts;
    }

    // ---- operations ---------------------------------------------------------------------------

    AiResult<Summary> summarize(ContentInput in, Progress progress, BooleanSupplier cancelled) {
        String doc = PromptSanitizer.sanitize(in.documentText());
        if (doc.length() < MIN_SUMMARY_CHARS) return insufficient("summarize");
        boolean chunked = false;
        String docLabel = "article text";
        int budget = docBudget(600, prompts.summarize(in, "article text", "", "00", false));
        if (budget > 0 && gen.countTokens(doc) > budget) {
            AiResult<String> notes = condense(in, doc, budget, progress, cancelled);
            if (!notes.isSuccess()) return notes.asFailure();
            doc = notes.value;
            docLabel = "condensed notes from a long document, not the original text";
            chunked = true;
        }
        final boolean wasChunked = chunked;
        final String label = docLabel;
        return runJson(in, doc, 600, Grammars.SUMMARY, progress, cancelled,
                (d, m, t) -> prompts.summarize(in, label, d, m, t),
                (o, t) -> OutputParsers.summary(o, t, wasChunked));
    }

    AiResult<Takeaways> extractKeyTakeaways(ContentInput in, Progress progress, BooleanSupplier cancelled) {
        String doc = PromptSanitizer.sanitize(in.documentText());
        if (doc.length() < MIN_SUMMARY_CHARS) return insufficient("extract takeaways from");
        return runJson(in, doc, 700, Grammars.TAKEAWAYS, progress, cancelled,
                (d, m, t) -> prompts.takeaways(in, d, m, t), OutputParsers::takeaways);
    }

    AiResult<Explanation> explain(ContentInput in, ExplainLevel level, Progress progress, BooleanSupplier cancelled) {
        String doc = PromptSanitizer.sanitize(in.documentText());
        if (doc.length() < MIN_SUMMARY_CHARS) return insufficient("explain");
        return runJson(in, doc, 800, Grammars.EXPLANATION, progress, cancelled,
                (d, m, t) -> prompts.explain(in, level, d, m, t), (o, t) -> OutputParsers.explanation(o, level, t));
    }

    AiResult<Answer> answer(ContentInput in, String question, Progress progress, BooleanSupplier cancelled) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) return AiResult.failure(AiResult.Status.ERROR, "The question is empty");
        if (q.length() > 500) q = q.substring(0, 500);
        String doc = PromptSanitizer.sanitize(in.documentText());
        if (doc.length() + in.metadata.length() < MIN_QA_CHARS) return insufficient("answer questions about");
        final String question1 = q;
        final String groundingText = doc + "\n" + in.metadata + "\n" + in.title;
        return runJson(in, doc, 500, Grammars.ANSWER, progress, cancelled,
                (d, m, t) -> prompts.answer(in, question1, d, m, t),
                (o, t) -> OutputParsers.Parsed.of(OutputParsers.answer(o, groundingText, t)));
    }

    AiResult<DailyBriefing> briefing(List<BriefingCandidate> items, Progress progress, BooleanSupplier cancelled) {
        if (items.isEmpty()) return AiResult.failure(AiResult.Status.INSUFFICIENT_CONTENT, "There is nothing to brief on yet");
        ContentInput shell = new ContentInput(0, "", "", "", "", "", false, null);
        return runJsonRaw(items, shell, 700, progress, cancelled);
    }

    // ---- core ---------------------------------------------------------------------------------

    private interface PromptFactory {
        String build(String doc, String marker, boolean truncated);
    }

    private <T> AiResult<T> runJson(ContentInput in, String doc, int maxOut, String grammar, Progress progress,
                                    BooleanSupplier cancelled, PromptFactory factory, Parser<T> parser) {
        String marker = prompts.newMarker();
        maxOut = Math.min(maxOut, gen.contextTokens() / 3); // small windows must still leave room for the document
        int budget = docBudget(maxOut, factory.build("", marker, false));
        if (budget < 200) return AiResult.failure(AiResult.Status.ERROR, "The model's context window is too small for this task");
        Fit fit = fit(doc, budget);
        String lastReason = "unknown";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cancelled.getAsBoolean()) return cancelledResult();
            progress.stage(attempt == 1 ? "Thinking" : "Retrying with stricter instructions");
            String user = factory.build(fit.text, marker, fit.truncated);
            if (attempt > 1) user += "\nYour previous reply was rejected: " + lastReason + ". Reply again with a valid JSON object that follows the schema.";
            try {
                TextGenerator.Generation g = gen.generate(gen.formatChat(PromptBuilder.SYSTEM, user), maxOut,
                        attempt == 1 ? 0.3f : 0.0f, grammar, piece -> {
                            progress.text(piece);
                            return !cancelled.getAsBoolean();
                        });
                if (g.cancelled || cancelled.getAsBoolean()) return cancelledResult();
                try {
                    OutputParsers.Parsed<T> p = parser.parse(new JSONObject(g.text), fit.truncated);
                    if (p.insufficient) return AiResult.failure(AiResult.Status.INSUFFICIENT_CONTENT,
                            "The model reports there is not enough text to do this reliably");
                    return AiResult.success(p.value);
                } catch (JSONException e) {
                    lastReason = g.hitLimit ? "the output was cut off before the JSON ended; keep every field short"
                            : "the output was not valid JSON for the schema";
                } catch (ValidationException e) {
                    lastReason = e.getMessage();
                }
            } catch (TextGenerator.GenerationException e) {
                switch (e.kind) {
                    case OUT_OF_MEMORY:
                        return AiResult.failure(AiResult.Status.OUT_OF_MEMORY, e.getMessage());
                    case CONTEXT_OVERFLOW:
                        budget = (int) (Math.min(budget, gen.countTokens(fit.text)) * 0.6);
                        if (budget < 200) return AiResult.failure(AiResult.Status.ERROR, e.getMessage());
                        fit = fit(doc, budget);
                        lastReason = "the input was too long";
                        break;
                    default:
                        return AiResult.failure(AiResult.Status.ERROR, e.getMessage());
                }
            }
        }
        return AiResult.failure(AiResult.Status.MALFORMED_OUTPUT, "The model did not produce a valid result (" + lastReason + ")");
    }

    /** Briefing variant: the "document" is the numbered list, validated against the offered candidates. */
    private AiResult<DailyBriefing> runJsonRaw(List<BriefingCandidate> items, ContentInput shell, int maxOut,
                                               Progress progress, BooleanSupplier cancelled) {
        String marker = prompts.newMarker();
        List<BriefingCandidate> offered = new ArrayList<>(items);
        String lastReason = "unknown";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cancelled.getAsBoolean()) return cancelledResult();
            progress.stage(attempt == 1 ? "Writing your briefing" : "Retrying with stricter instructions");
            String user = prompts.briefing(offered, marker);
            if (attempt > 1) user += "\nYour previous reply was rejected: " + lastReason + ". Reply again with a valid JSON object that follows the schema.";
            try {
                if (gen.countTokens(PromptBuilder.SYSTEM + user) + maxOut + RESERVE_TOKENS > gen.contextTokens()) {
                    // Too many items for the window: drop the lowest-ranked ones (the list is already in rank order).
                    while (offered.size() > 3 && gen.countTokens(PromptBuilder.SYSTEM + prompts.briefing(offered, marker)) + maxOut + RESERVE_TOKENS > gen.contextTokens()) {
                        offered.remove(offered.size() - 1);
                    }
                    user = prompts.briefing(offered, marker);
                }
                TextGenerator.Generation g = gen.generate(gen.formatChat(PromptBuilder.SYSTEM, user), maxOut,
                        attempt == 1 ? 0.3f : 0.0f, Grammars.BRIEFING, piece -> {
                            progress.text(piece);
                            return !cancelled.getAsBoolean();
                        });
                if (g.cancelled || cancelled.getAsBoolean()) return cancelledResult();
                try {
                    return AiResult.success(OutputParsers.briefing(new JSONObject(g.text), offered));
                } catch (JSONException e) {
                    lastReason = g.hitLimit ? "the output was cut off before the JSON ended; keep every field short"
                            : "the output was not valid JSON for the schema";
                } catch (ValidationException e) {
                    lastReason = e.getMessage();
                }
            } catch (TextGenerator.GenerationException e) {
                return AiResult.failure(e.kind == TextGenerator.GenerationException.Kind.OUT_OF_MEMORY
                        ? AiResult.Status.OUT_OF_MEMORY : AiResult.Status.ERROR, e.getMessage());
            }
        }
        return AiResult.failure(AiResult.Status.MALFORMED_OUTPUT, "The model did not produce a valid briefing (" + lastReason + ")");
    }

    // ---- budgeting and chunking ---------------------------------------------------------------

    /** Tokens available for the document once the fixed prompt parts and the output are accounted for. */
    private int docBudget(int maxOut, String promptWithEmptyDoc) {
        int overhead = gen.countTokens(PromptBuilder.SYSTEM) + gen.countTokens(promptWithEmptyDoc) + 24;
        return gen.contextTokens() - overhead - maxOut - RESERVE_TOKENS;
    }

    static final class Fit {
        final String text;
        final boolean truncated;

        Fit(String text, boolean truncated) {
            this.text = text;
            this.truncated = truncated;
        }
    }

    /** Cuts {@code doc} at a paragraph or word boundary so it fits {@code tokenBudget} (never exceeds it). */
    Fit fit(String doc, int tokenBudget) {
        if (gen.countTokens(doc) <= tokenBudget) return new Fit(doc, false);
        String cut = doc;
        // Scale the character count by the measured token density; each pass shrinks by at least 10%.
        for (int i = 0; i < 10 && gen.countTokens(cut) > tokenBudget; i++) {
            int tokens = Math.max(1, gen.countTokens(cut));
            int chars = (int) Math.min(cut.length() * 0.9, (long) cut.length() * tokenBudget / tokens * 0.95);
            cut = boundaryCut(doc, Math.max(1, chars));
        }
        return new Fit(cut, true);
    }

    private static String boundaryCut(String doc, int maxChars) {
        if (doc.length() <= maxChars) return doc;
        int para = doc.lastIndexOf("\n\n", maxChars);
        if (para > maxChars * 0.6) return doc.substring(0, para).trim();
        int space = doc.lastIndexOf(' ', maxChars);
        return doc.substring(0, space > maxChars * 0.6 ? space : maxChars).trim();
    }

    /** Splits {@code doc} into at most {@link #MAX_CHUNKS} parts that each fit {@code tokenBudget}. */
    List<String> chunks(String doc, int tokenBudget) {
        List<String> out = new ArrayList<>();
        String rest = doc;
        while (!rest.isEmpty() && out.size() < MAX_CHUNKS) {
            Fit f = fit(rest, tokenBudget);
            out.add(f.text);
            rest = f.truncated ? rest.substring(f.text.length()).trim() : "";
        }
        return out;
    }

    /** Map step for documents that exceed the context window: condense each part into short plain-text notes. */
    private AiResult<String> condense(ContentInput in, String doc, int finalBudget, Progress progress, BooleanSupplier cancelled) {
        int partBudget = Math.max(300, gen.contextTokens() - gen.countTokens(PromptBuilder.SYSTEM) - CONDENSE_MAX_TOKENS - 300);
        List<String> parts = chunks(doc, partBudget);
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (cancelled.getAsBoolean()) return cancelledResult();
            progress.stage("Reading part " + (i + 1) + " of " + parts.size());
            String user = prompts.condense(in, parts.get(i), prompts.newMarker(), i + 1, parts.size());
            try {
                TextGenerator.Generation g = gen.generate(gen.formatChat(PromptBuilder.SYSTEM
                        .replace("Reply with a single JSON object that follows the schema in the task, and nothing else.",
                                "Reply only with the plain lines the task asks for."), user), CONDENSE_MAX_TOKENS, 0.2f, null,
                        piece -> !cancelled.getAsBoolean());
                if (g.cancelled || cancelled.getAsBoolean()) return cancelledResult();
                String part = PromptSanitizer.sanitize(TextClean.clean(g.text.replace("\n", " \n ")).replace(" - ", "\n- "));
                if (!part.isEmpty()) notes.append("Part ").append(i + 1).append(":\n").append(part).append("\n\n");
            } catch (TextGenerator.GenerationException e) {
                return AiResult.failure(e.kind == TextGenerator.GenerationException.Kind.OUT_OF_MEMORY
                        ? AiResult.Status.OUT_OF_MEMORY : AiResult.Status.ERROR, e.getMessage());
            }
        }
        String joined = notes.toString().trim();
        if (joined.length() < MIN_SUMMARY_CHARS / 2) return AiResult.failure(AiResult.Status.MALFORMED_OUTPUT, "Could not condense the document");
        Fit f = fit(joined, finalBudget);
        return AiResult.success(f.text);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static <T> AiResult<T> insufficient(String verb) {
        return AiResult.failure(AiResult.Status.INSUFFICIENT_CONTENT,
                "Not enough text is stored to " + verb + " this item. Open the original to read it, or fetch the full text first.");
    }

    private static <T> AiResult<T> cancelledResult() {
        return AiResult.failure(AiResult.Status.CANCELLED, "Cancelled");
    }
}
