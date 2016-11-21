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
import android.text.TextUtils;
import android.util.Log;

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

    private static final String MXFILE_CRYPTO_STORE_ACCOUNT_FILE = "account";
    private static final String MXFILE_CRYPTO_STORE_DEVICES_FILE = "devices";
    private static final String MXFILE_CRYPTO_STORE_DEVICES_FILE_TMP = "devices.tmp";
    private static final String MXFILE_CRYPTO_STORE_ALGORITHMS_FILE = "roomsAlgorithms";
    private static final String MXFILE_CRYPTO_STORE_ALGORITHMS_FILE_TMP = "roomsAlgorithms";
    private static final String MXFILE_CRYPTO_STORE_SESSIONS_FILE = "sessions";
    private static final String MXFILE_CRYPTO_STORE_SESSIONS_FILE_TMP = "sessions.tmp";
    private static final String MXFILE_CRYPTO_STORE_INBOUND_GROUP_SESSSIONS_FILE = "inboundGroupSessions";

    // The credentials used for this store
    private Credentials mCredentials;

    // Meta data about the store
    private MXFileCryptoStoreMetaData mMetaData;

    // The olm account
    private OlmAccount mOlmAccount;

    // All users devices keys
    private MXUsersDevicesMap<MXDeviceInfo> mUsersDevicesInfoMap;

    // The algorithms used in rooms
    private HashMap<String, String> mRoomsAlgorithms;

    // The olm sessions (<device identity key> -> (<olm session id> -> <olm session>)
    private HashMap<String /*deviceKey*/,
            HashMap<String /*olmSessionId*/, OlmSession>> mOlmSessions;

    // The inbound group megolm sessions (<senderKey> -> (<inbound group session id> -> <inbound group megolm session>)
    private HashMap<String /*senderKey*/,
            HashMap<String /*inboundGroupSessionId*/,MXOlmInboundGroupSession>> mInboundGroupSessions;


    private Context mContext;

    // The path of the MXFileCryptoStore folder
    private File mStoreFile;
    private File mMetaDataFile;
    private File mAccountFile;
    private File mDevicesFile;
    private File mDevicesFileTmp;
    private File mAlgorithmsFile;
    private File mAlgorithmsFileTmp;
    private File mSessionsFile;
    private File mSessionsFileTmp;
    private File mInboundGroupSessionsFile;

    // tell if the store is corrupted
    private boolean mIsCorrupted = false;

    // the background thread
    private HandlerThread mHandlerThread = null;
    private MXOsHandler mFileStoreHandler = null;

    public MXFileCryptoStore() {
    }

    @Override
    public void initWithCredentials(Context context, Credentials credentials) {
        mContext = context;
        mCredentials = credentials;

        mStoreFile = new File(new File(mContext.getApplicationContext().getFilesDir(), MXFILE_CRYPTO_STORE_FOLDER), mCredentials.userId);
        mMetaDataFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_METADATA_FILE);
        mAccountFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_ACCOUNT_FILE);

        mDevicesFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_DEVICES_FILE);
        mDevicesFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_DEVICES_FILE_TMP);

        mAlgorithmsFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_ALGORITHMS_FILE);
        mAlgorithmsFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_ALGORITHMS_FILE_TMP);

        mSessionsFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_SESSIONS_FILE);
        mSessionsFileTmp = new File(mStoreFile, MXFILE_CRYPTO_STORE_SESSIONS_FILE_TMP);

        mInboundGroupSessionsFile = new File(mStoreFile, MXFILE_CRYPTO_STORE_INBOUND_GROUP_SESSSIONS_FILE);

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
     * @return the thread handle
     */
    private MXOsHandler getThreadHandler() {
        if (null == mFileStoreHandler) {
            mHandlerThread = new HandlerThread("MXFileCryptoStoreBackgroundThread_" + mCredentials.userId + System.currentTimeMillis(), Thread.MIN_PRIORITY);
            mHandlerThread.start();
            mFileStoreHandler = new MXOsHandler(mHandlerThread.getLooper());
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
        storeObject(mMetaData, mMetaDataFile, "saveMetaData");
    }

    @Override
    public void storeAccount(OlmAccount account) {
        mOlmAccount = account;
        storeObject(mOlmAccount, mAccountFile, "storeAccount");
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
    public void storeDeviceForUser(String userId, MXDeviceInfo device) {
        mUsersDevicesInfoMap.setObject(device, userId, device.deviceId);
        flushDevicesForUser();
    }

    @Override
    public MXDeviceInfo deviceWithDeviceId(String deviceId, String userId) {
        return mUsersDevicesInfoMap.objectForDevice(deviceId, userId);
    }

    @Override
    public void storeDevicesForUser(String userId, Map<String, MXDeviceInfo> devices, boolean flush) {
        mUsersDevicesInfoMap.setObjects(devices, userId);

        if (flush) {
            flushDevicesForUser();
        }
    }

    @Override
    public void flushDevicesForUser() {
        final MXUsersDevicesMap<MXDeviceInfo> devicesMapClone = cloneUsersDevicesInfoMap(mUsersDevicesInfoMap);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                getThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // the file is temporary copied to avoid loosing data if the application crashes.

                        // delete the previous tmp
                        if (mDevicesFileTmp.exists()) {
                            mDevicesFileTmp.delete();
                        }

                        // copy the existing file
                        if (mDevicesFile.exists()) {
                            mDevicesFile.renameTo(mDevicesFileTmp);
                        }

                        storeObject(devicesMapClone, mDevicesFile, "flushDevicesForUser");

                        // remove the tmp file
                        if (mDevicesFileTmp.exists()) {
                            mDevicesFileTmp.delete();
                        }
                    }
                });
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @Override
    public Map<String, MXDeviceInfo> devicesForUser(String userId) {
        if (null != userId) {
            return mUsersDevicesInfoMap.getMap().get(userId);
        } else {
            return null;
        }
    }

    @Override
    public void storeAlgorithmForRoom(String roomId, String algorithm) {
        if ((null != roomId) && (null != algorithm)) {
            mRoomsAlgorithms.put(roomId, algorithm);
            final HashMap<String, String> roomsAlgorithms = new HashMap<>(mRoomsAlgorithms);

            Runnable r = new Runnable() {
                @Override
                public void run() {
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

                            storeObject(roomsAlgorithms, mAlgorithmsFile, "storeAlgorithmForRoom");

                            // remove the tmp file
                            if (mAlgorithmsFileTmp.exists()) {
                                mAlgorithmsFileTmp.delete();
                            }
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    @Override
    public String algorithmForRoom(String roomId) {
        if (null != roomId) {
            return mRoomsAlgorithms.get(roomId);
        }

        return null;
    }

    @Override
    public void storeSession(OlmSession session, String deviceKey, boolean flush) {
        if ((null != session) && (null != deviceKey) && (null != session.sessionIdentifier())) {

            if (!mOlmSessions.containsKey(deviceKey)) {
                mOlmSessions.put(deviceKey, new HashMap<String, OlmSession>());
            }

            mOlmSessions.get(deviceKey).put(session.sessionIdentifier(), session);

            if (flush) {
                flushSessions();
            }
        }
    }

    @Override
    public void flushSessions() {
        final HashMap<String, HashMap<String,  OlmSession>> olmSessions = cloneOlmSessions(mOlmSessions);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                getThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // the file is temporary copied to avoid loosing data if the application crashes.

                        // delete the previous tmp
                        if (mSessionsFileTmp.exists()) {
                            mSessionsFileTmp.delete();
                        }

                        // copy the existing file
                        if (mSessionsFile.exists()) {
                            mSessionsFile.renameTo(mSessionsFileTmp);
                        }

                        storeObject(olmSessions, mSessionsFile, "storeSession");

                        // remove the tmp file
                        if (mSessionsFileTmp.exists()) {
                            mSessionsFileTmp.delete();
                        }
                    }
                });
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @Override
    public Map<String, OlmSession> sessionsWithDevice(String deviceKey) {
        if (null != deviceKey) {
            return mOlmSessions.get(deviceKey);
        }

        return null;
    }

    @Override
    public void storeInboundGroupSession(MXOlmInboundGroupSession session) {
        if ((null != session) && (null != session.mSenderKey) && (null != session.mSession) && (null != session.mSession.sessionIdentifier())) {
            if (!mInboundGroupSessions.containsKey(session.mSenderKey)) {
                mInboundGroupSessions.put(session.mSenderKey, new HashMap<String, MXOlmInboundGroupSession>());
            }

            mInboundGroupSessions.get(session.mSenderKey).put(session.mSession.sessionIdentifier(), session);
        }

        storeObject(mInboundGroupSessions, mInboundGroupSessionsFile, "storeInboundGroupSession");
    }

    @Override
    public  MXOlmInboundGroupSession inboundGroupSessionWithId(String sessionId, String senderKey) {
        if ((null != sessionId) && (null != senderKey) && mInboundGroupSessions.containsKey(senderKey)) {

            return mInboundGroupSessions.get(senderKey).get(sessionId);
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
                    Log.e(LOG_TAG, description  + "failed : " + e.getMessage());
                }
            }
        }
        return object;
    }

    /**
     * Load the metadata from the store
     */
    private void loadMetaData() {
        Object metadataAsVoid = loadObject(mMetaDataFile, "loadMetadata");

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
        Object olmAccountAsVoid = loadObject(mAccountFile, "preloadCryptoData - mAccountFile");

        if (null != olmAccountAsVoid) {
            try {
                mOlmAccount = (OlmAccount)olmAccountAsVoid;
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## preloadCryptoData() - invalid mAccountFile " + e.getMessage());
            }
        }

        Object usersDevicesInfoMapAsVoid;

        // if the tmp exists, it means that the latest file backup has been killed / stopped
        if (mDevicesFileTmp.exists()) {
            usersDevicesInfoMapAsVoid = loadObject(mDevicesFileTmp, "preloadCryptoData - mUsersDevicesInfoMap - tmp");
        } else {
            usersDevicesInfoMapAsVoid = loadObject(mDevicesFile, "preloadCryptoData - mUsersDevicesInfoMap");
        }

        if (null != usersDevicesInfoMapAsVoid) {
            try {
                MXUsersDevicesMap objectAsMap = (MXUsersDevicesMap)usersDevicesInfoMapAsVoid;
                mUsersDevicesInfoMap = new MXUsersDevicesMap<>(objectAsMap.getMap());
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## preloadCryptoData() - invalid mUsersDevicesInfoMap " + e.getMessage());
            }
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

        Object olmSessionsAsVoid;

        if (mSessionsFileTmp.exists()) {
            olmSessionsAsVoid = loadObject(mSessionsFileTmp, "preloadCryptoData - mOlmSessions - tmp");
        } else {
            olmSessionsAsVoid = loadObject(mSessionsFile, "preloadCryptoData - mOlmSessions");
        }

        if (null != olmSessionsAsVoid) {
            try {
                Map<String, Map<String, OlmSession>> olmSessionMap = (Map<String, Map<String, OlmSession>>)olmSessionsAsVoid;

                mOlmSessions = new HashMap<>();

                for(String key : olmSessionMap.keySet()) {
                    mOlmSessions.put(key, new HashMap<>(olmSessionMap.get(key)));
                }
            } catch (Exception e) {
                mIsCorrupted = true;
                Log.e(LOG_TAG, "## preloadCryptoData() - invalid mSessionsFile " + e.getMessage());
            }
        }

        Object inboundGroupSessionsAsVoid = loadObject(mInboundGroupSessionsFile, "preloadCryptoData - mInboundGroupSessions");

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
    }


    /**
     * @return a, users devices map deep copy
     */
    private static MXUsersDevicesMap<MXDeviceInfo> cloneUsersDevicesInfoMap (MXUsersDevicesMap<MXDeviceInfo> devicesInfos) {
        HashMap<String, HashMap<String, MXDeviceInfo>> map = devicesInfos.getMap();
        HashMap<String, Map<String, MXDeviceInfo>> copy = new HashMap<>();

        Set<String> keys = map.keySet();

        for(String key : keys) {
            HashMap<String, MXDeviceInfo> subMapClone = new HashMap<>();
            HashMap<String, MXDeviceInfo> subMap = map.get(key);

            Set<String> subKeys = subMap.keySet();
            for(String subKey : subKeys) {
                subMapClone.put(subKey, subMap.get(subKey).deepCopy());
            }

            copy.put(key, subMapClone);
        }

        return new MXUsersDevicesMap<>(copy);
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
}
