package org.matrix.androidsdk.api.response;

/**
 * Class representing an API response with start and end tokens and a generically-typed chunk.
 */
public class TokensChunkResponse<T> {

    private String start;
    private String end;
    private T chunk;

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public T getChunk() {
        return chunk;
    }

    public void setChunk(T chunk) {
        this.chunk = chunk;
    }
}
