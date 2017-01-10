/*
 * Copyright 2015 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.data.cryptostore;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.ContentUtils;
import org.matrix.androidsdk.util.MXOsHandler;
import org.matrix.olm.OlmAccount;
import org.matrix.olm.OlmSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * the crypto data store
 */
public class MXFileCryptoStore implements IMXCryptoStore {
    private static final String LOG_TAG = "MXFileCryptoStore";

    private static final int MXFILE_CRYPTO_VERSION = 1;

    private static final String MXFILE_CRYPTO_STORE_FOLDER = "MXFileCryptoStore";

    private static final String MXFILE_CRYPTO_STORE_METADATA_FILE = "MXFileCryptoStore";
    private static final String MXFILE_CRYPTO_STORE_METADATA_FILE_TMP = "MXFileCryptoStore.tmp";

    private static final String MXFILE_CRYPTO_STORE_ACCOUNT_FILE = "account";
    private static final String MXFILE_CRYPTO_STORE_ACCOUNT_FILE_TMP = "account.tmp";

    private static final String MXFILE_CRYPTO_STORE_DEVICES_FOLDER = "devicesFolder";
    private static final String MXFILE_CRYPTO_STORE_DEVICES_FILE = "devices";
    private static final String MXFILE_CRYPTO_STORE_DEVICES_FILE_TMP = "devices.tmp";

    private static final String MXFILE_CRYPTO_STORE_ALGORITHMS_FILE = "roomsAlgorithms";
    private static final String MXFILE_CRYPTO_STORE_ALGORITHMS_FILE_TMP = "roomsAlgorithms";

    private static final String MXFILE_CRYPTO_STORE_OLM_SESSIONS_FILE = "sessions";
    private static final String MXFILE_CRYPTO_STORE_OLM_SESSIONS_FILE_TMP = "sessions.tmp";
    private static final String MXFILE_CRYPTO_STORE_OLM_SESSIONS_FOLDER = "olmSessionsFolder";

    private static final String MXFILE_CRYPTO_STORE_INBOUND_GROUP_SESSSIONS_FILE = "inboundGroupSessions";
    private static final String MXFILE_CRYPTO_STORE_INBOUND_GROUP_SESSSIONS_FILE_TMP = "inboundGroupSessions.tmp";


    // The credentials used for this store
    private Credentials mCredentials;

    // Meta data about the store
    private MXFileCryptoStoreMetaData mMetaData;

    // The olm account
    private OlmAccount mOlmAccount;

    // All users devices keys
    private MXUsersDevicesMap<MXDeviceInfo> mUsersDevicesInfoMap;
    private final Object mUsersDevicesInfoMapLock = new Object();

    // The algorithms used in rooms
    private HashMap<String, String> mRoomsAlgorithms;

    // The olm sessions (<device identity key> -> (<olm session id> -> <olm session>)
    private HashMap<String /*deviceKey*/,
            HashMap<String /*olmSessionId*/, OlmSession>> mOlmSessions;
    private static final Object mOlmSessionsLock = new Object();

    // The inbound group megolm sessions (<senderKey> -> (<inbound group session id> -> <inbound group megolm session>)
    private HashMap<String /*senderKey*/,
            HashMap<String /*inboundGroupSessionId*/,MXOlmInboundGroupSession>> mInboundGroupSessions;
    private final Object mInboundGroupSessionsLock = new Object();


    // The path of the MXFileCryptoStore folder
    private File mStoreFile;

    private File mMetaDataFile;
    private File mMetaDataFileTmp;

    private File mAccountFile;
    private File mAccountFileTmp;

    private File mDevicesFolder;
    private File mDevicesFile;
    private File mDevicesFileTmp;

    private File mAlgorithmsFile;
    private File mAlgorithmsFileTmp;

    private File mOlmSessionsFile;
    private File mOlmSessionsFileTmp;
    private File mOlmSessionsFolder;

    private File mInboundGroupSessionsFile;
    private File mInboundGroupSessionsFileTmp;

    // tell if the store is corrupted
    private boolean mIsCorrupted = false;

    // the background thread
    private HandlerThread mHandlerThread = null;
    private MXOsHandler mFileStoreHandler = null;

    public MXFileCryptoStore() {
    }

    @Override
    public void initWithCredentials(Context context, Credentials credentials) {
        mCredentials = credentials;

        mStoreFile = new File(new File(context.getApplicationContext().getFilesDir(), MXFILE_CRYPTO_STORE_FOLDER), mCredentials.userId);

        mMetaDataFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_METADATA_FILE);
        mMetaDataFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_METADATA_FILE_TMP);

        mAccountFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_ACCOUNT_FILE);
        mAccountFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_ACCOUNT_FILE_TMP);

        mDevicesFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_DEVICES_FILE);
        mDevicesFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_DEVICES_FILE_TMP);
        mDevicesFolder = new File(mStoreFile, MXFILE_CRYPTO_STORE_DEVICES_FOLDER);

        mAlgorithmsFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_ALGORITHMS_FILE);
        mAlgorithmsFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_ALGORITHMS_FILE_TMP);

        // backward compatibility : the sessions used to be stored in an unique file
        mOlmSessionsFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_OLM_SESSIONS_FILE);
        mOlmSessionsFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_OLM_SESSIONS_FILE_TMP);
        // each session is now stored in a dedicated file
        mOlmSessionsFolder = new File(mStoreFile, MXFILE_CRYPTO_STORE_OLM_SESSIONS_FOLDER);

        mInboundGroupSessionsFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_INBOUND_GROUP_SESSSIONS_FILE);
        mInboundGroupSessionsFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_INBOUND_GROUP_SESSSIONS_FILE_TMP);

        // Build default metadata
        if ((null == mMetaData)
                && (null != credentials.homeServer)
                && (null != credentials.userId)
                && (null != credentials.accessToken)) {
            mMetaData = new MXFileCryptoStoreMetaData();
            mMetaData.mUserId = new String(mCredentials.userId);
            if (null != mCredentials.deviceId) {
                mMetaData.mDeviceId = new String(mCredentials.deviceId);
            }
            mMetaData.mVersion = MXFILE_CRYPTO_VERSION;
            mMetaData.mDeviceAnnounced = false;
        }

        mUsersDevicesInfoMap = new MXUsersDevicesMap<>();
        mRoomsAlgorithms = new HashMap<>();
        mOlmSessions = new HashMap<>();
        mInboundGroupSessions = new HashMap<>();
    }

    @Override
    public boolean hasData() {
        boolean result = mStoreFile.exists();

        if (result) {
            // User ids match. Check device ids
            loadMetaData();

            if (null != mMetaData) {
                result = TextUtils.isEmpty(mMetaData.mDeviceId) ||
                        TextUtils.equals(mCredentials.deviceId, mMetaData.mDeviceId);
            }
        }

        return result;
    }


    @Override
    public boolean isCorrupted() {
        return  mIsCorrupted;
    }


    @Override
    public void deleteStore() {
        // delete the dedicated directories
        try {
            ContentUtils.deleteDirectory(mStoreFile);
        } catch(Exception e) {
            Log.e(LOG_TAG, "deleteStore failed " + e.getMessage());
        }
    }

    @Override
    public void open() {
        mMetaData = null;

        loadMetaData();

        // Check if
        if (null == mMetaData) {
            resetData();
        }
        // Check store version
        else if (MXFILE_CRYPTO_VERSION != mMetaData.mVersion) {
            Log.e(LOG_TAG, "## open() : New MXFileCryptoStore version detected");
            resetData();
        }
        // Check credentials
        // The device id may not have been provided in credentials.
        // Check it only if provided, else trust the stored one.
        else if  (!TextUtils.equals(mMetaData.mUserId, mCredentials.userId) ||
                ((null != mCredentials.deviceId) && !TextUtils.equals(mCredentials.deviceId, mMetaData.mDeviceId))
                ) {
            Log.e(LOG_TAG, "## open() : Credentials do not match");
            resetData();
        }

        // If metaData is still defined, we can load rooms data
        if (null != mMetaData) {
            preloadCryptoData();
        }

        // Else, if credentials is valid, create and store it
        if ((null == mMetaData)
                && (null != mCredentials.homeServer)
                && (null != mCredentials.userId)
                && (null != mCredentials.accessToken)) {
            mMetaData = new MXFileCryptoStoreMetaData();
            mMetaData.mUserId = new String(mCredentials.userId);
            if (null != mCredentials.deviceId) {
                mMetaData.mDeviceId = new String(mCredentials.deviceId);
            }
            mMetaData.mVersion = MXFILE_CRYPTO_VERSION;
            mMetaData.mDeviceAnnounced = false;
            saveMetaData();
        }
    }

    @Override
    public void storeDeviceId(String deviceId) {
        mMetaData.mDeviceId = deviceId;
        saveMetaData();
    }

    @Override
    public String getDeviceId() {
        return mMetaData.mDeviceId;
    }

    /**
     * @return the thread handler
     */
    public MXOsHandler getThreadHandler() {
        if (null == mFileStoreHandler) {
            if (null == mHandlerThread) {
                mHandlerThread = new HandlerThread("MXFileCryptoStoreBackgroundThread_" + mCredentials.userId + System.currentTimeMillis(), Thread.MIN_PRIORITY);
                mHandlerThread.start();
            }

            // GA issue
            if (null != mHandlerThread.getLooper()) {
                mFileStoreHandler = new MXOsHandler(mHandlerThread.getLooper());
            } else {
                return new MXOsHandler(Looper.getMainLooper());
            }
        }

        return mFileStoreHandler;
    }

    /**
     * Store a serializable object into a dedicated file.
     * @param object the object to write.
     * @param file the file
     * @param description the object description
     */
    private void storeObject(Object object, File file, String description) {
        synchronized (LOG_TAG) {
            try {
                long t0 = System.currentTimeMillis();

                if (file.exists()) {
                    file.delete();
                }

                FileOutputStream fos = new FileOutputStream(file);
                GZIPOutputStream gz = new GZIPOutputStream(fos);
                ObjectOutputStream out = new ObjectOutputStream(gz);

                out.writeObject(object);
                out.close();

                Log.d(LOG_TAG, "## storeObject () : " + description + " done in " + (System.currentTimeMillis() - t0) + " ms");
            } catch (OutOfMemoryError oom) {
                Log.e(LOG_TAG, "storeObject failed : " + description + " -- " + oom.getMessage());
            } catch (Exception e) {
                Log.e(LOG_TAG, "storeObject failed : " + description + " -- " + e.getMessage());
            }
        }
    }

    /**
     * Save the metadata into the crypto file store
     */
    private void saveMetaData() {
        if (mMetaDataFileTmp.exists()) {
            mMetaDataFileTmp.delete();
        }

        if (mMetaDataFile.exists()) {
            mMetaDataFile.renameTo(mMetaDataFileTmp);
        }

        storeObject(mMetaData, mMetaDataFile, "saveMetaData");

        if (mMetaDataFileTmp.exists()) {
            mMetaDataFileTmp.delete();
        }
    }

    @Override
    public void storeAccount(OlmAccount account) {
        mOlmAccount = account;

        if (mAccountFileTmp.exists()) {
            mAccountFileTmp.delete();
        }

        if (mAccountFile.exists()) {
            mAccountFile.renameTo(mAccountFileTmp);
        }

        storeObject(mOlmAccount, mAccountFile, "storeAccount");

        if (mAccountFileTmp.exists()) {
            mAccountFileTmp.delete();
        }
    }

    @Override
    public OlmAccount getAccount() {
        return mOlmAccount;
    }

    @Override
    public  void storeDeviceAnnounced() {
        mMetaData.mDeviceAnnounced = true;
        saveMetaData();
    }

    @Override
    public boolean deviceAnnounced() {
        return mMetaData.mDeviceAnnounced;
    }

    @Override
    public void storeUserDevice(String userId, MXDeviceInfo device) {
        synchronized (mUsersDevicesInfoMapLock) {
            mUsersDevicesInfoMap.setObject(device, userId, device.deviceId);
        }

        flushDevicesForUser(userId);
    }

    @Override
    public MXDeviceInfo getUserDevice(String deviceId, String userId) {
        MXDeviceInfo deviceInfo;

        synchronized (mUsersDevicesInfoMapLock) {
            deviceInfo = mUsersDevicesInfoMap.getObject(deviceId, userId);
        }

        return deviceInfo;
    }

    @Override
    public void storeUserDevices(String userId, Map<String, MXDeviceInfo> devices) {
        synchronized (mUsersDevicesInfoMapLock) {
            mUsersDevicesInfoMap.setObjects(devices, userId);
        }

        flushDevicesForUser(userId);
    }

    /**
     * Flush the devices list for an userId
     * @param userId the userId
     */
    private void flushDevicesForUser(final String userId) {
        final HashMap<String, MXDeviceInfo> devicesMap = cloneUserDevicesInfoMap(userId);

        getThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                storeObject(devicesMap, new File(mDevicesFolder, userId), "flushDevicesForUser " + userId);
            }
        });
    }

    @Override
    public Map<String, MXDeviceInfo> getUserDevices(String userId) {
        if (null != userId) {
            Map<String, MXDeviceInfo> devicesMap;

            synchronized (mUsersDevicesInfoMapLock) {
                devicesMap = mUsersDevicesInfoMap.getMap().get(userId);
            }

            return devicesMap;
        } else {
            return null;
        }
    }

    @Override
    public void storeRoomAlgorithm(String roomId, String algorithm) {
        if ((null != roomId) && (null != algorithm)) {
            mRoomsAlgorithms.put(roomId, algorithm);

            try {
                final HashMap<String, String> roomsAlgorithms = new HashMap<>(mRoomsAlgorithms);

                getThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // the file is temporary copied to avoid loosing data if the application crashes.

                        // delete the previous tmp
                        if (mAlgorithmsFileTmp.exists()) {
                            mAlgorithmsFileTmp.delete();
                        }

                        // copy the existing file
                        if (mAlgorithmsFile.exists()) {
                            mAlgorithmsFile.renameTo(mAlgorithmsFileTmp);
                        }

                        storeObject(roomsAlgorithms, mAlgorithmsFile, "storeAlgorithmForRoom - in background");

                        // remove the tmp file
                        if (mAlgorithmsFileTmp.exists()) {
                            mAlgorithmsFileTmp.delete();
                        }
                    }
                });
            } catch (OutOfMemoryError oom) {
                Log.e(LOG_TAG, "## storeAlgorithmForRoom() : oom");
            }
        }
    }

    @Override
    public String getRoomAlgorithm(String roomId) {
        if (null != roomId) {
            return mRoomsAlgorithms.get(roomId);
        }

        return null;
    }

    @Override
    public void storeSession(final OlmSession olmSession, final String deviceKey) {
        String sessionIdentifier = null;

        if (null != olmSession) {
            try {
                sessionIdentifier = olmSession.sessionIdentifier();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## storeSession : session.sessionIdentifier() failed " + e.getMessage());
            }
        }

        if ((null != deviceKey) && (null != sessionIdentifier)) {
            synchronized (mOlmSessionsLock) {
                if (!mOlmSessions.containsKey(deviceKey)) {
                    mOlmSessions.put(deviceKey, new HashMap<String, OlmSession>());
                }

                final File keyFolder = new File(mOlmSessionsFolder, deviceKey);

                if (!keyFolder.exists()) {
                    keyFolder.mkdir();
                }

                OlmSession prevOlmSession = mOlmSessions.get(deviceKey).get(sessionIdentifier);

                // test if the session is a new one
                if (olmSession != prevOlmSession) {
                    if (null != prevOlmSession) {
                        prevOlmSession.releaseSession();
                    }
                    mOlmSessions.get(deviceKey).put(sessionIdentifier, olmSession);
                }

                try {
                    final File fOlmFile = new File(keyFolder, sessionIdentifier);
                    final String fSessionIdentifier = sessionIdentifier;

                    getThreadHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (fOlmFile.exists()) {
                                fOlmFile.delete();
                            }

                            storeObject(olmSession, fOlmFile, "Store olm session " + deviceKey + " " + fSessionIdentifier);
                        }
                    });
                } catch (OutOfMemoryError oom) {
                    Log.e(LOG_TAG, "## flushSessions() : oom");
                }
            }
        }
    }

    @Override
    public Map<String, OlmSession> getDeviceSessions(String deviceKey) {
        if (null != deviceKey) {
            Map<String, OlmSession> map;

            synchronized (mOlmSessionsLock) {
                map = mOlmSessions.get(deviceKey);
            }

            return map;
        }

        return null;
    }

    /**
     * Flush mInboundGroupSessions
     * @param fOlmInboundGroupSessionToRelease the session to release when the flush is completed.
     */
    private void saveInboundGroupSessions(final MXOlmInboundGroupSession fOlmInboundGroupSessionToRelease) {
        final HashMap<String, HashMap<String, MXOlmInboundGroupSession>> fInboundGroupSessions = cloneInboundGroupSessions(mInboundGroupSessions);

        getThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                if (mInboundGroupSessionsFileTmp.exists()) {
                    mInboundGroupSessionsFileTmp.delete();
                }

                if (mInboundGroupSessionsFile.exists()) {
                    mInboundGroupSessionsFile.renameTo(mInboundGroupSessionsFileTmp);
                }

                storeObject(fInboundGroupSessions, mInboundGroupSessionsFile, "storeInboundGroupSession - in background");

                if (mInboundGroupSessionsFileTmp.exists()) {
                    mInboundGroupSessionsFileTmp.delete();
                }

                if ((null != fOlmInboundGroupSessionToRelease) && (null != fOlmInboundGroupSessionToRelease.mSession)) {
                    // JNI release
                    fOlmInboundGroupSessionToRelease.mSession.releaseSession();
                }
            }
        });
    }

    @Override
    public void removeInboundGroupSession(String sessionId, String senderKey) {
        if ((null != sessionId) && (null != senderKey)) {
            synchronized (mInboundGroupSessionsLock) {
                if (mInboundGroupSessions.containsKey(senderKey)) {
                    MXOlmInboundGroupSession session = mInboundGroupSessions.get(senderKey).get(sessionId);

                    if (null != session) {
                        session.mSession.releaseSession();
                        mInboundGroupSessions.get(senderKey).remove(sessionId);

                        saveInboundGroupSessions(null);
                    }
                }
            }
        }
    }

    @Override
    public void storeInboundGroupSession(MXOlmInboundGroupSession session) {
        String sessionIdentifier = null;

        if ((null != session) && (null != session.mSenderKey) && (null != session.mSession)) {
            try {
                sessionIdentifier = session.mSession.sessionIdentifier();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## storeInboundGroupSession() : sessionIdentifier failed " + e.getMessage());
            }
        }

        if (null != sessionIdentifier) {
            synchronized (mInboundGroupSessionsLock) {
                if (!mInboundGroupSessions.containsKey(session.mSenderKey)) {
                    mInboundGroupSessions.put(session.mSenderKey, new HashMap<String, MXOlmInboundGroupSession>());
                }

                final MXOlmInboundGroupSession fOlmInboundGroupSessionToRelease;

                if (session != mInboundGroupSessions.get(session.mSenderKey).get(sessionIdentifier)) {
                    fOlmInboundGroupSessionToRelease = mInboundGroupSessions.get(session.mSenderKey).get(sessionIdentifier);
                    mInboundGroupSessions.get(session.mSenderKey).put(sessionIdentifier, session);

                    if (null != fOlmInboundGroupSessionToRelease) {
                        try {
                            Log.d(LOG_TAG, "## storeInboundGroupSession() : release session " + fOlmInboundGroupSessionToRelease.mSession.sessionIdentifier());
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## storeInboundGroupSession() : fOlmInboundGroupSessionToRelease.sessionIdentifier() failed " + e.getMessage());
                        }
                    }
                } else {
                    fOlmInboundGroupSessionToRelease = null;
                }

                Log.d(LOG_TAG, "## storeInboundGroupSession() : store session " + sessionIdentifier);

                saveInboundGroupSessions(fOlmInboundGroupSessionToRelease);
            }
        }
    }


    @Override
    public  MXOlmInboundGroupSession getInboundGroupSession(String sessionId, String senderKey) {
        if ((null != sessionId) && (null != senderKey) && mInboundGroupSessions.containsKey(senderKey)) {
            MXOlmInboundGroupSession session;

            synchronized (mInboundGroupSessionsLock) {
                session = mInboundGroupSessions.get(senderKey).get(sessionId);
            }

            return session;
        }
        return null;
    }

    @Override
    public void close() {
        // release JNI objects
        ArrayList<OlmSession> olmSessions = new ArrayList<>();
        Collection<HashMap<String , OlmSession>> sessionValues = mOlmSessions.values();

        for(HashMap<String , OlmSession> value : sessionValues) {
            olmSessions.addAll(value.values());
        }

        for(OlmSession olmSession : olmSessions) {
            olmSession.releaseSession();
        }
        mOlmSessions.clear();

        ArrayList<MXOlmInboundGroupSession> groupSessions = new ArrayList<>();
        Collection<HashMap<String ,MXOlmInboundGroupSession>> groupSessionsValues = mInboundGroupSessions.values();

        for(HashMap<String ,MXOlmInboundGroupSession> map : groupSessionsValues) {
            groupSessions.addAll(map.values());
        }

        for(MXOlmInboundGroupSession groupSession : groupSessions) {
            if (null != groupSession.mSession) {
                groupSession.mSession.releaseSession();
            }
        }
        mInboundGroupSessions.clear();
    }

    /**
     * Reset the crypto store data
     */
    private void resetData() {
        close();

        // ensure there is background writings while deleting the store
        synchronized (LOG_TAG) {
            deleteStore();
        }

        if (null != mHandlerThread) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }

        if (!mStoreFile.exists()) {
            mStoreFile.mkdirs();
        }

        if (!mDevicesFolder.exists()) {
            mDevicesFolder.mkdirs();
        }

        if (!mOlmSessionsFolder.exists()) {
            mOlmSessionsFolder.mkdir();
        }

        mMetaData = null;
    }

    /**
     * Load a file from the crypto store
     * @param file the file to read
     * @param description the operation description
     * @return the read object, null if it fails
     */
    private Object loadObject(File file, String description) {
        Object object = null;

        if (file.exists()) {
            try {
                // the files are now zipped to reduce saving time
                FileInputStream fis = new FileInputStream(file);
                GZIPInputStream gz = new GZIPInputStream(fis);
                ObjectInputStream ois = new ObjectInputStream(gz);
                object = ois.readObject();
                ois.close();
            } catch (Exception e) {
                // if the zip unflating fails, try to use the former file saving method
                try {
                    FileInputStream fis2 = new FileInputStream(file);
                    ObjectInputStream out = new ObjectInputStream(fis2);

                    object = out.readObject();
                    fis2.close();
                } catch (Exception subEx) {
                    // warn that some file loading fails
                    mIsCorrupted = true;
                    Log.e(LOG_TAG, description  + "failed : " + subEx.getMessage());
                }
            }
        }
        return object;
    }

    /**
     * Load the metadata from the store
     */
    private void loadMetaData() {
        Object metadataAsVoid;

        if (mMetaDataFileTmp.exists()) {
            metadataAsVoid = loadObject(mMetaDataFileTmp, "loadMetadata");
        } else {
            metadataAsVoid = loadObject(mMetaDataFile, "loadMetadata");
        }

        if (null != metadataAsVoid) {
            try {
                mMetaData = (MXFileCryptoStoreMetaData) metadataAsVoid;
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## loadMetadata() : metadata has been corrupted");
            }
        }
    }

    /**
     * Preload the crypto data
     */
    private void preloadCryptoData() {
        Object olmAccountAsVoid;

        if (mAccountFileTmp.exists()) {
            olmAccountAsVoid = loadObject(mAccountFileTmp, "preloadCryptoData - mAccountFile - tmp");
        } else {
            olmAccountAsVoid = loadObject(mAccountFile, "preloadCryptoData - mAccountFile");
        }

        if (null != olmAccountAsVoid) {
            try {
                mOlmAccount = (OlmAccount)olmAccountAsVoid;
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## preloadCryptoData() - invalid mAccountFile " + e.getMessage());
            }
        }

        // previous store
        if (!mDevicesFolder.exists()) {
            Object usersDevicesInfoMapAsVoid;

            // if the tmp exists, it means that the latest file backup has been killed / stopped
            if (mDevicesFileTmp.exists()) {
                usersDevicesInfoMapAsVoid = loadObject(mDevicesFileTmp, "preloadCryptoData - mUsersDevicesInfoMap - tmp");
            } else {
                usersDevicesInfoMapAsVoid = loadObject(mDevicesFile, "preloadCryptoData - mUsersDevicesInfoMap");
            }

            if (null != usersDevicesInfoMapAsVoid) {
                try {
                    MXUsersDevicesMap objectAsMap = (MXUsersDevicesMap) usersDevicesInfoMapAsVoid;
                    mUsersDevicesInfoMap = new MXUsersDevicesMap<>(objectAsMap.getMap());
                } catch (Exception e) {
                    mIsCorrupted = true;
                    Log.e(LOG_TAG, "## preloadCryptoData() - invalid mUsersDevicesInfoMap " + e.getMessage());
                }
            }

            mDevicesFolder.mkdirs();

            if (null != mUsersDevicesInfoMap) {
                HashMap<String, HashMap<String, MXDeviceInfo>> map = mUsersDevicesInfoMap.getMap();

                Set<String> userIds = map.keySet();

                for(String userId : userIds) {
                    storeObject(map.get(userId), new File(mDevicesFolder, userId), "convert devices map of " + userId);
                }

                mDevicesFileTmp.delete();
                mDevicesFile.delete();
            }
        } else {
            String[] files = mDevicesFolder.list();
            HashMap<String, Map<String, MXDeviceInfo>> map = new HashMap<>();

            for(int i = 0; i < files.length; i++) {
                String userId = files[i];
                Object devicesMapAsVoid = loadObject(new File(mDevicesFolder, userId), "load devices of " + userId);

                if (null != devicesMapAsVoid) {
                    try {
                        map.put(userId, (Map<String, MXDeviceInfo>)devicesMapAsVoid);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## preloadCryptoData() - cannot cast to map");
                    }
                }
            }

            mUsersDevicesInfoMap = new MXUsersDevicesMap<>(map);
        }

        Object algorithmsAsVoid;

        if (mAlgorithmsFileTmp.exists()) {
            algorithmsAsVoid = loadObject(mAlgorithmsFileTmp, "preloadCryptoData - mRoomsAlgorithms - tmp");
        } else {
            algorithmsAsVoid = loadObject(mAlgorithmsFile, "preloadCryptoData - mRoomsAlgorithms");
        }

        if (null != algorithmsAsVoid) {
            try {
                Map<String, String> algorithmsMap = (Map<String, String>)algorithmsAsVoid;
                mRoomsAlgorithms = new HashMap<>(algorithmsMap);
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## preloadCryptoData() - invalid mAlgorithmsFile " + e.getMessage());
            }
        }


        if (mOlmSessionsFolder.exists()) {
            String[] olmSessionFiles = mOlmSessionsFolder.list();

            mOlmSessions = new HashMap<>();

            // build mOlmSessions for the file system
            for(int i = 0; i < olmSessionFiles.length; i++) {
                try {
                    String deviceKey = olmSessionFiles[i];

                    HashMap<String, OlmSession> olmSessionSubMap = new HashMap<>();

                    File sessionsDeviceFolder = new File(mOlmSessionsFolder, deviceKey);
                    String[] sessionIds = sessionsDeviceFolder.list();

                    for(int j = 0; j < sessionIds.length; j++) {
                        String sessionId = sessionIds[j];
                        OlmSession olmSession = (OlmSession)loadObject(new File(sessionsDeviceFolder, sessionId), "load the olmSession " + deviceKey + " " + sessionId);

                        if (null != olmSession) {
                            olmSessionSubMap.put(sessionId, olmSession);
                        }
                    }

                    mOlmSessions.put(deviceKey, olmSessionSubMap);
                } catch (Exception e) {
                    mIsCorrupted = true;
                    Log.e(LOG_TAG, "## preloadCryptoData() - invalid mSessionsFile " + e.getMessage());
                }
            }
        } else {
            Object olmSessionsAsVoid;

            if (mOlmSessionsFileTmp.exists()) {
                olmSessionsAsVoid = loadObject(mOlmSessionsFileTmp, "preloadCryptoData - mOlmSessions - tmp");
            } else {
                olmSessionsAsVoid = loadObject(mOlmSessionsFile, "preloadCryptoData - mOlmSessions");
            }

            if (null != olmSessionsAsVoid) {
                try {
                    Map<String, Map<String, OlmSession>> olmSessionMap = (Map<String, Map<String, OlmSession>>) olmSessionsAsVoid;

                    mOlmSessions = new HashMap<>();

                    for (String key : olmSessionMap.keySet()) {
                        mOlmSessions.put(key, new HashMap<>(olmSessionMap.get(key)));
                    }

                    // convert to the new format
                    mOlmSessionsFolder.mkdir();

                    for (String key : olmSessionMap.keySet()) {
                        Map<String, OlmSession> submap = olmSessionMap.get(key);
                        File submapFile = new File(mOlmSessionsFolder, key);

                        submapFile.mkdir();

                        for(String sessionId : submap.keySet()) {
                            File olmFile = new File(submapFile, sessionId);
                            storeObject(submap.get(sessionId), olmFile, "Convert olmSession " + key + " " + sessionId);
                        }
                    }
                } catch (Exception e) {
                    mIsCorrupted = true;
                    Log.e(LOG_TAG, "## preloadCryptoData() - invalid mSessionsFile " + e.getMessage());
                }
            }
        }

        Object inboundGroupSessionsAsVoid;

        if (mInboundGroupSessionsFileTmp.exists()) {
            inboundGroupSessionsAsVoid = loadObject(mInboundGroupSessionsFileTmp, "preloadCryptoData - mInboundGroupSessions - tmp");
        } else {
            inboundGroupSessionsAsVoid = loadObject(mInboundGroupSessionsFile, "preloadCryptoData - mInboundGroupSessions");
        }

        if (null != inboundGroupSessionsAsVoid) {
            try {
                Map<String, Map<String, MXOlmInboundGroupSession>> inboundGroupSessionsMap = (Map<String, Map<String, MXOlmInboundGroupSession>>)inboundGroupSessionsAsVoid;

                mInboundGroupSessions = new HashMap<>();

                for (String key : inboundGroupSessionsMap.keySet()) {
                    mInboundGroupSessions.put(key, new HashMap<>(inboundGroupSessionsMap.get(key)));
                }
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## preloadCryptoData() - invalid mInboundGroupSessions " + e.getMessage());
            }
        }

        if ((null == mOlmAccount) && (mUsersDevicesInfoMap.getMap().size() > 0)) {
            mIsCorrupted = true;
            Log.e(LOG_TAG, "## preloadCryptoData() - there is no account but some devices are defined");
        }
    }

    /**
     * @return clone the device infos map
     */
    private HashMap<String, MXDeviceInfo> cloneUserDevicesInfoMap(String user) {
        HashMap<String, MXDeviceInfo> clone = new HashMap<>();

        synchronized (mUsersDevicesInfoMapLock) {
            HashMap<String, MXDeviceInfo> source = mUsersDevicesInfoMap.getMap().get(user);

            if (null != source) {
                Set<String> deviceIds = source.keySet();
                for (String deviceId : deviceIds) {
                    clone.put(deviceId, cloneDeviceInfo(source.get(deviceId)));
                }
            }
        }

        return clone;
    }


    /**
     * @return an olm sessions map deep copy
     */
    private static HashMap<String, HashMap<String,  OlmSession>> cloneOlmSessions(HashMap<String, HashMap<String,  OlmSession>> olmSession) {
        HashMap<String, HashMap<String,  OlmSession>> clone = new HashMap<>();

        Set<String> keys = olmSession.keySet();

        for(String key : keys) {
            clone.put(key, new HashMap<>(olmSession.get(key)));
        }

        return clone;
    }

    /**
     * @return a deep copy
     */
    public static MXDeviceInfo cloneDeviceInfo(MXDeviceInfo di) {
        MXDeviceInfo copy = new MXDeviceInfo(di.deviceId);

        copy.userId = di.userId;

        if (null != di.algorithms) {
            copy.algorithms = new ArrayList<>(di.algorithms);
        }

        if (null != di.keys) {
            copy.keys = new HashMap<>(di.keys);
        }

        if (null != di.signatures) {
            copy.signatures = new HashMap<>();

            Set<String> keySet = di.signatures.keySet();

            for(String k : keySet) {
                copy.signatures.put(k, new HashMap<>(di.signatures.get(k)));
            }
        }

        if (null != di.unsigned) {
            copy.unsigned = new HashMap<>(di.unsigned);
        }

        copy.mVerified = di.mVerified;

        return copy;
    }

    /**
     * Clone an inbound group sessions map
     * @param inboundSession the inbound group sessions map to clone
     * @return the clone
     */
    private static HashMap<String, HashMap<String ,MXOlmInboundGroupSession>> cloneInboundGroupSessions(HashMap<String, HashMap<String ,MXOlmInboundGroupSession>> inboundSession) {
        HashMap<String, HashMap<String ,MXOlmInboundGroupSession>> copy = new HashMap<>();

        Set<String> keys = inboundSession.keySet();

        for(String k : keys) {
            copy.put(k, new HashMap<>(inboundSession.get(k)));
        }

        return copy;
    }
}
