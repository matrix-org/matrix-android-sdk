package org.matrix.androidsdk.api.response;

import java.util.List;

/**
 * Class representing an API response with start and end tokens and a generically-typed chunk.
 */
public class TokensChunkResponse<T> {
    public String start;
    public String end;
    public List<T> chunk;
}
