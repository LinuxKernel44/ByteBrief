package app.bytebrief.ai;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Neutralizes text from the network before it is placed in a prompt. External content is untrusted:
 * it can contain strings that a tokenizer would turn into real chat-control tokens (forging a turn or
 * ending the document early) and invisible characters used to hide instructions. This class defuses
 * the tokens and strips the invisible characters; it cannot make hostile text harmless, which is why
 * prompts also fence the document, state that it is data, and constrain the output with a grammar.
 */
public final class PromptSanitizer {
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF\\u00AD\\u180E]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern ANGLE_SPECIAL = Pattern.compile(
            "(?i)<\\s*/?\\s*(start_of_turn|end_of_turn|bos|eos|s|pad|unk|sep|cls|mask|think|thinking|tool_call|tool_response"
                    + "|im_start|im_end|system|assistant|user|begin_of_text|end_of_text)\\s*>");
    private static final Pattern BRACKET_SPECIAL = Pattern.compile("(?i)\\[\\s*/?\\s*(INST|SYS|SYSTEM)\\s*]|<<\\s*/?\\s*SYS\\s*>>");
    private static final Pattern BLANK_RUNS = Pattern.compile("\\n{3,}");
    private static final int HARD_CAP_CHARS = 400_000;

    private PromptSanitizer() {}

    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input.length() > HARD_CAP_CHARS ? input.substring(0, HARD_CAP_CHARS) : input;
        // NFKC folds look-alikes such as full-width "<|" into their ASCII forms so they are caught below.
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        // Line endings first: "\r" is a control character and would otherwise be replaced by a space.
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        s = INVISIBLE.matcher(s).replaceAll("");
        s = CONTROL.matcher(s).replaceAll(" ");
        s = s.replace("<|", "< |").replace("|>", "| >");
        s = ANGLE_SPECIAL.matcher(s).replaceAll("($1)");
        s = BRACKET_SPECIAL.matcher(s).replaceAll("($1)");
        return BLANK_RUNS.matcher(s).replaceAll("\n\n").trim();
    }
}
