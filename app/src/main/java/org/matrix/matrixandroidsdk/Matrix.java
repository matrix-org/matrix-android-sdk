package org.matrix.matrixandroidsdk;

import android.content.Context;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager;
import org.matrix.matrixandroidsdk.store.LoginStorage;
import org.matrix.matrixandroidsdk.util.RageShake;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton to control access to the Matrix SDK and providing point of control for MXSessions.
 */
public class Matrix {

    private static Matrix instance = null;

    private LoginStorage mLoginStorage;
    private MXSession mDefaultSession;
    private GcmRegistrationManager mGcmRegistrationManager;
    private Context mAppContext;

    protected Matrix(Context appContext) {
        mAppContext = appContext.getApplicationContext();
        mLoginStorage = new LoginStorage(mAppContext);
        mGcmRegistrationManager = new GcmRegistrationManager(mAppContext);
        RageShake.getInstance().start(mAppContext);
    }

    public synchronized static Matrix getInstance(Context appContext) {
        if ((instance == null) && (null != appContext)) {
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
     * Return the used media caches.
     * This class can inherited to customized it.
     * @return the mediasCache.
     */
    public MXMediasCache getDefaultMediasCache() {
        if (null != mDefaultSession) {
            return mDefaultSession.getMediasCache();
        }
        return null;
    }

    /**
     * Return the used latestMessages caches.
     * This class can inherited to customized it.
     * @return the latest messages cache.
     */
    public MXLatestChatMessageCache getDefaultLatestChatMessageCache() {
        if (null != mDefaultSession) {
            return mDefaultSession.getLatestChatMessageCache();
        }
        return null;
    }
    /**
     *
     * @return true if the matrix client instance defines a valid session
     */
    public static Boolean hasValidValidSession() {
        return (null != instance) && (null != instance.mDefaultSession);
    }

    /**
     * Clears the default session and the login credentials.
     */
    public synchronized void clearDefaultSessionAndCredentials(Context context) {
        mLoginStorage.removeCredentials(mDefaultSession.getCredentials());
        mDefaultSession.clear(context);
        mDefaultSession = null;
    }

    /**
     * Clears the default session.
     */
    public synchronized void clearDefaultSession() {
        mDefaultSession = null;
    }

    /**
     * Set a default session.
     * @param session The session to store as the default session.
     */
    public synchronized void setDefaultSession(MXSession session) {
        mLoginStorage.addCredentials(session.getCredentials());
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
     * @param useHttps True to enforce https URIs on the home server.
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
        return new MXSession(new MXDataHandler(new MXMemoryStore(), credentials), credentials, mAppContext);
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

    public GcmRegistrationManager getSharedGcmRegistrationManager() {
        return mGcmRegistrationManager;
    }
}
