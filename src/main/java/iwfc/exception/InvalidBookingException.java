package iwfc.exception;

/** Raised when a session or booking violates an IWFC scheduling rule. */
public class InvalidBookingException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidBookingException(String message) {
        super(message);
    }
}
