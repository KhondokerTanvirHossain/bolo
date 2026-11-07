package app.bolo.common.web;

import app.bolo.common.error.RefusedException;
import app.bolo.common.error.SignedOutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrorAdvice {

    @ExceptionHandler(RefusedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    ApiError refused(RefusedException e) {
        return new ApiError(e.code());
    }

    @ExceptionHandler(SignedOutException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError signedOut() {
        return new ApiError("signed-out");
    }
}
