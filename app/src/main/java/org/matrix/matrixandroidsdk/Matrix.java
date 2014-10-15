package org.matrix.matrixandroidsdk;

import android.content.Context;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.matrixandroidsdk.store.LoginStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton to control access to the Matrix SDK.
 */
public class Matrix {

    private static Matrix instance = null;

    private LoginStorage mLoginStorage;
    private MXSession mDefaultSession;

    protected Matrix(Context appContext) {
        mLoginStorage = new LoginStorage(appContext.getApplicationContext());
    }

    public synchronized static Matrix getInstance(Context appContext) {
        if (instance == null) {
            instance = new Matrix(appContext);
        }
        return instance;
    }

    /**
     * Retrieve the default session if one exists.
     *
     * The default session may be user-configured, or it may be the last session the user was using.
     * @return The default session or null.
     */
    public synchronized MXSession getDefaultSession() {
        if (mDefaultSession != null) {
            return mDefaultSession;
        }

        Credentials creds = mLoginStorage.getDefaultCredentials();
        if (creds == null) {
            return null;
        }
        mDefaultSession = createSession(creds);
        return mDefaultSession;
    }

    /**
     * Clear the default session.
     */
    public synchronized void clearDefaultSession() {
        mDefaultSession = null;
        mLoginStorage.setDefaultCredentials(null);
    }

    /**
     * Set a default session.
     * @param session The session to store as the default session.
     */
    public synchronized void setDefaultSession(MXSession session) {
        mLoginStorage.setDefaultCredentials(session.getCredentials());
        mDefaultSession = session;
    }

    /**
     * Creates an MXSession from some credentials.
     * @param credentials The credentials to create a session from.
     * @return The session.
     */
    public MXSession createSession(Credentials credentials) {
        return createSession(credentials, true);
    }

    /**
     * Creates an MXSession from some credentials.
     * @param credentials The credentials to create a session from.
     * @return The session.
     */
    public MXSession createSession(Credentials credentials, boolean useHttps) {
        if (!credentials.homeServer.startsWith("http")) {
            if (useHttps) {
                credentials.homeServer = "https://" + credentials.homeServer;
            }
            else {
                credentials.homeServer = "http://" + credentials.homeServer;
            }
        }
        return new MXSession(new MXDataHandler(new MXMemoryStore()), credentials);
    }

    /**
     * Retrieve a list of possible credentials to use.
     * @param context Application context
     * @return A list of credentials, or an empty list.
     */
    public List<Credentials> getCredentialsList(Context context) {
        List<Credentials> credList = new ArrayList<Credentials>();

        Credentials creds = mLoginStorage.getDefaultCredentials();
        if (creds != null) {
            credList.add(creds);
        }
        // TODO support >1 creds.

        return credList;
    }
}
