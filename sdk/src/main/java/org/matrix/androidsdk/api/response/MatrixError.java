package org.matrix.androidsdk.api.response;

/**
 * Represents a standard error response.
 */
public class MatrixError {
    public static final String FORBIDDEN = "M_FORBIDDEN";
    public static final String UNKNOWN_TOKEN = "M_UNKNOWN_TOKEN";
    public static final String BAD_JSON = "M_BAD_JSON";
    public static final String NOT_JSON = "M_NOT_JSON";
    public static final String NOT_FOUND = "M_NOT_FOUND";
    public static final String LIMIT_EXCEEDED = "M_LIMIT_EXCEEDED";
    public static final String USER_IN_USE = "M_USER_IN_USE";
    public static final String ROOM_IN_USE = "M_ROOM_IN_USE";
    public static final String BAD_PAGINATION = "M_BAD_PAGINATION";

    public String errcode;
    public String error;
}
