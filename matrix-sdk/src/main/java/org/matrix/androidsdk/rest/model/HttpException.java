package org.matrix.androidsdk.rest.model;

public class HttpException extends Exception {

    private final HttpError httpError;

    private HttpException(HttpError httpError) {
        this.httpError = httpError;
    }

    public HttpError getHttpError() {
        return httpError;
    }
}
