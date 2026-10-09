package kervan.core.domain;

/** Every refusal the API answers with: an HTTP status, a stable error code and a message. */
public abstract sealed class CoreException extends RuntimeException {

    private final int status;
    private final String code;

    private CoreException(int status, String code, String message) {
        super(message, null, false, false);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static final class NotFound extends CoreException {
        public NotFound(String message) {
            super(404, "not_found", message);
        }
    }

    public static final class InvalidRequest extends CoreException {
        public InvalidRequest(String message) {
            super(400, "invalid_request", message);
        }
    }

    public static final class InvalidDate extends CoreException {
        public InvalidDate(String message) {
            super(400, "invalid_date", message);
        }
    }

    public static final class VerificationFailed extends CoreException {
        public VerificationFailed() {
            super(403, "verification_failed", "verification code does not match");
        }
    }

    public static final class NotReschedulable extends CoreException {
        public NotReschedulable(String message) {
            super(409, "not_reschedulable", message);
        }
    }
}
