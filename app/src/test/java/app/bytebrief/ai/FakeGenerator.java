package app.bytebrief.ai;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Scripted stand-in for the inference runtime, used only in tests to exercise the pipeline's
 * prompting, validation and retry logic. It is never part of the shipped app.
 */
final class FakeGenerator implements TextGenerator {
    final Deque<Object> script = new ArrayDeque<>();
    final List<String> prompts = new ArrayList<>();
    final List<String> grammars = new ArrayList<>();
    final List<Float> temperatures = new ArrayList<>();
    int contextTokens = 4096;
    int cancelCalls;

    FakeGenerator reply(String text) {
        script.add(new Generation(text, text.length() / 4, false, false));
        return this;
    }

    FakeGenerator replyCutOff(String text) {
        script.add(new Generation(text, text.length() / 4, true, false));
        return this;
    }

    FakeGenerator replyCancelled() {
        script.add(new Generation("", 0, false, true));
        return this;
    }

    FakeGenerator fail(GenerationException e) {
        script.add(e);
        return this;
    }

    int calls() {
        return prompts.size();
    }

    @Override public int contextTokens() { return contextTokens; }

    @Override public int countTokens(String text) { return (text.length() + 3) / 4; }

    @Override public String formatChat(String system, String user) { return "SYS:" + system + "\nUSER:" + user; }

    @Override
    public Generation generate(String prompt, int maxTokens, float temperature, @Nullable String grammar, @Nullable Sink sink)
            throws GenerationException {
        prompts.add(prompt);
        grammars.add(grammar);
        temperatures.add(temperature);
        Object next = script.poll();
        if (next == null) throw new AssertionError("FakeGenerator script exhausted at call " + prompts.size());
        if (next instanceof GenerationException) throw (GenerationException) next;
        Generation g = (Generation) next;
        if (sink != null && !g.text.isEmpty()) sink.onText(g.text);
        return g;
    }

    @Override public void cancel() { cancelCalls++; }
}
