package app.bolo.common.web;

/** Every error body the API sends: {"code": "..."}. */
public record ApiError(String code) {
}
