package app.bytebrief.ai;

/** Model output was syntactically valid JSON but violated the schema or content rules. */
public class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }
}
