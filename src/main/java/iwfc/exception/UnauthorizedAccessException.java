package iwfc.exception;

/** Raised when a user attempts an operation that their role cannot perform. */
public class UnauthorizedAccessException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
