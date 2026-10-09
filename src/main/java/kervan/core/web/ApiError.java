package kervan.core.web;

/** The error body of every refusal: {@code {"error": "<code>", "message": "…"}}. */
public record ApiError(String error, String message) {
}
