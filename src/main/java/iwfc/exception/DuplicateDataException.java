package iwfc.exception;

/** Raised when an ID or relationship that must be unique already exists. */
public class DuplicateDataException extends Exception {
    private static final long serialVersionUID = 1L;

    public DuplicateDataException(String message) {
        super(message);
    }
}
