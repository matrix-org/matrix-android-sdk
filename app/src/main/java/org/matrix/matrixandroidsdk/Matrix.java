package org.matrix.matrixandroidsdk;

import org.matrix.androidsdk.MXSession;

/**
 * Singleton to control access to the Matrix SDK.
 */
public class Matrix {

    private static Matrix instance = null;

    protected Matrix() {

    }

    public synchronized static Matrix getInstance() {
        if (instance == null) {
            instance = new Matrix();
        }
        return instance;
    }

    /**
     * Retrieve the Matrix session for the given user on the given home server, if it exists.
     * @param hs The home server URL.
     * @param username The username to get a session for,
     * @return An MXSession or null.
     */
    public MXSession getMatrixSession(String hs, String username) {
        return null;
    }

    // TODO: Put MatrixApiClient and MatrixSession in here so multiple Activities can hit methods on
    // it. This needs to be able to cope with multiple login sessions.
}
