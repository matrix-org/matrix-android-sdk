package org.matrix.matrixandroidsdk;

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

    // TODO: Put MatrixApiClient and MatrixSession in here so multiple Activities can hit methods on
    // it.
}
