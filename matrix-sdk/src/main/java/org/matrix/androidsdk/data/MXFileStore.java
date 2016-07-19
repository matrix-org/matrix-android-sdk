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

package org.matrix.androidsdk.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.util.ContentUtils;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * An in-file IMXStore.
 */
public class MXFileStore extends MXMemoryStore {
    private static final String LOG_TAG = "MXFileStore";

    // some constant values
    final int MXFILE_VERSION = 1;

    // ensure that there is enough messages to fill a tablet screen
    final int MAX_STORED_MESSAGES_COUNT = 50;

    final String MXFILE_STORE_FOLDER = "MXFileStore";
    final String MXFILE_STORE_METADATA_FILE_NAME = "MXFileStore";

    final String MXFILE_STORE_GZ_ROOMS_MESSAGES_FOLDER = "messages_gz";
    final String MXFILE_STORE_ROOMS_TOKENS_FOLDER = "tokens";
    final String MXFILE_STORE_GZ_ROOMS_STATE_FOLDER = "state_gz";
    final String MXFILE_STORE_ROOMS_SUMMARY_FOLDER = "summary";
    final String MXFILE_STORE_ROOMS_RECEIPT_FOLDER = "receipts";
    final String MXFILE_STORE_ROOMS_ACCOUNT_DATA_FOLDER = "accountData";

    // the data is read from the file system
    private boolean mIsReady = false;

    private boolean mIsCorrupted = false;

    // the store is currently opening
    private boolean mIsOpening = false;

    private MXStoreListener mListener = null;

    private String mPreferencesStatusKey;

    // List of rooms to save on [MXStore commit]
    // filled with roomId
    private ArrayList<String> mRoomsToCommitForMessages;
    private ArrayList<String> mRoomsToCommitForStates;
    private ArrayList<String> mRoomsToCommitForSummaries;
    private ArrayList<String> mRoomsToCommitForAccountData;
    private ArrayList<String> mRoomsToCommitForReceipts;

    // Flag to indicate metaData needs to be store
    private boolean mMetaDataHasChanged = false;

    // The path of the MXFileStore folders
    private File mStoreFolderFile = null;
    private File mGzStoreRoomsMessagesFolderFile = null;
    private File mStoreRoomsTokensFolderFile = null;
    private File mGzStoreRoomsStateFolderFile = null;
    private File mStoreRoomsSummaryFolderFile = null;
    private File mStoreRoomsMessagesReceiptsFolderFile = null;
    private File mStoreRoomsAccountDataFolderFile = null;

    // the background thread
    private HandlerThread mHandlerThread = null;
    private android.os.Handler mFileStoreHandler = null;

    private boolean mIsKilled = false;

    private boolean mIsNewStorage = false;

    /**
     * Create the file store dirtrees
     */
    private void createDirTree(String userId) {
        // data path
        // MXFileStore/userID/
        // MXFileStore/userID/MXFileStore
        // MXFileStore/userID/Messages/
        // MXFileStore/userID/Tokens/
        // MXFileStore/userID/States/
        // MXFileStore/userID/Summaries/
        // MXFileStore/userID/receipt/<room Id>/receipts
        // MXFileStore/userID/accountData/

        // create the dirtree
        mStoreFolderFile = new File(new File(mContext.getApplicationContext().getFilesDir(), MXFILE_STORE_FOLDER), userId);

        if (!mStoreFolderFile.exists()) {
            mStoreFolderFile.mkdirs();
        }

        mGzStoreRoomsMessagesFolderFile = new File(mStoreFolderFile, MXFILE_STORE_GZ_ROOMS_MESSAGES_FOLDER);
        if (!mGzStoreRoomsMessagesFolderFile.exists()) {
            mGzStoreRoomsMessagesFolderFile.mkdirs();
        }

        mStoreRoomsTokensFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_TOKENS_FOLDER);
        if (!mStoreRoomsTokensFolderFile.exists()) {
            mStoreRoomsTokensFolderFile.mkdirs();
        }

        mGzStoreRoomsStateFolderFile = new File(mStoreFolderFile, MXFILE_STORE_GZ_ROOMS_STATE_FOLDER);
        if (!mGzStoreRoomsStateFolderFile.exists()) {
            mGzStoreRoomsStateFolderFile.mkdirs();
        }

        mStoreRoomsSummaryFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_SUMMARY_FOLDER);
        if (!mStoreRoomsSummaryFolderFile.exists()) {
            mStoreRoomsSummaryFolderFile.mkdirs();
        }

        mStoreRoomsMessagesReceiptsFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_RECEIPT_FOLDER);
        if (!mStoreRoomsMessagesReceiptsFolderFile.exists()) {
            mStoreRoomsMessagesReceiptsFolderFile.mkdirs();
        }

        mStoreRoomsAccountDataFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_ACCOUNT_DATA_FOLDER);
        if (!mStoreRoomsAccountDataFolderFile.exists()) {
            mStoreRoomsAccountDataFolderFile.mkdirs();
        }
    }

    /**
     * Default constructor
     * @param hsConfig the expected credentials
     * @param context the context.
     */
    public MXFileStore(HomeserverConnectionConfig hsConfig, Context context) {
        initCommon();
        mContext = context;
        mIsReady = false;
        mCredentials = hsConfig.getCredentials();

        mHandlerThread = new HandlerThread("MXFileStoreBackgroundThread_" + mCredentials.userId, Thread.MIN_PRIORITY);

        mPreferencesStatusKey = "MXfileStore_saved_status_" + mCredentials.userId;

        createDirTree(mCredentials.userId);

        // updated data
        mRoomsToCommitForMessages = new ArrayList<String>();
        mRoomsToCommitForStates = new ArrayList<String>();
        mRoomsToCommitForSummaries = new ArrayList<String>();
        mRoomsToCommitForAccountData = new ArrayList<String>();
        mRoomsToCommitForReceipts = new ArrayList<String>();

        // check if the metadata file exists and if it is valid
        loadMetaData();

        if ( (null == mMetadata) ||
                (mMetadata.mVersion != MXFILE_VERSION) ||
                !TextUtils.equals(mMetadata.mUserId, mCredentials.userId) ||
                !TextUtils.equals(mMetadata.mAccessToken, mCredentials.accessToken)) {
            deleteAllData(true);
        }

        // create the medatata file if it does not exist
        if (null == mMetadata) {
            mIsNewStorage = true;
            mIsOpening = true;
            mHandlerThread.start();
            mFileStoreHandler = new android.os.Handler(mHandlerThread.getLooper());

            mMetadata = new MXFileStoreMetaData();
            mMetadata.mUserId = mCredentials.userId;
            mMetadata.mAccessToken = mCredentials.accessToken;
            mMetadata.mVersion = MXFILE_VERSION;
            mMetaDataHasChanged = true;
            saveMetaData();

            mEventStreamToken = null;

            mIsOpening = false;
            // nothing to load so ready to work
            mIsReady = true;
        }
    }

    /**
     * Killed the background thread.
     * @param isKilled
     */
    private void setIsKilled(boolean isKilled) {
        synchronized (this) {
            mIsKilled = isKilled;
        }
    }

    /**
     * @return true if the background thread is killed.
     */
    private boolean isKilled() {
        boolean isKilled;

        synchronized (this) {
            isKilled = mIsKilled;
        }

        return isKilled;
    }

    /**
     * Save changes in the store.
     * If the store uses permanent storage like database or file, it is the optimised time
     * to commit the last changes.
     */
    @Override
    public void commit() {
        // Save data only if metaData exists
        if ((null != mMetadata) && !isKilled()) {
            Log.d(LOG_TAG, "++ Commit");
            saveRoomsMessages();
            saveRoomStates();
            saveSummaries();
            saveRoomsAccountData();
            saveReceipts();
            saveMetaData();
            Log.d(LOG_TAG, "-- Commit");
        }
    }

    /**
     * Save the committing status
     * @param status true if the store is saving data.
     */
    private void committing(boolean status) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(mPreferencesStatusKey, !status);
        editor.commit();
    }

    /**
     * Open the store.
     */
    public void open() {
        super.open();

        // avoid concurrency call.
        synchronized (this) {
            if (!mIsReady && !mIsOpening && (null != mMetadata) && (null != mHandlerThread)) {
                mIsOpening = true;

                Log.e(LOG_TAG, "Open the store.");

                // creation the background handler.
                if (null == mFileStoreHandler) {
                    // avoid already started exception
                    // never succeeded to reproduce but it was reported in GA.
                    try {
                        mHandlerThread.start();
                    } catch (IllegalThreadStateException e) {
                        Log.e(LOG_TAG, "mHandlerThread is already started.");
                        // already started
                        return;
                    }
                    mFileStoreHandler = new android.os.Handler(mHandlerThread.getLooper());
                }

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        mFileStoreHandler.post(new Runnable() {
                            public void run() {
                                Log.e(LOG_TAG, "Open the store in the background thread.");

                                // clear the preferences
                                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

                                String errorDescription = null;
                                boolean succeed = preferences.getBoolean(mPreferencesStatusKey, true);

                                if (!succeed) {
                                    errorDescription = "The latest save did not work properly";
                                    Log.e(LOG_TAG, errorDescription);
                                }

                                if (succeed) {
                                    succeed &= loadRoomsMessages();
                                    if (!succeed) {
                                        errorDescription = "loadRoomsMessages fails";
                                        Log.e(LOG_TAG, errorDescription);
                                    } else {
                                        Log.e(LOG_TAG, "loadRoomsMessages succeeds");
                                    }
                                }

                                if (succeed) {
                                    succeed &= loadRoomsState();

                                    if (!succeed) {
                                        errorDescription = "loadRoomsState fails";
                                        Log.e(LOG_TAG, errorDescription);
                                    } else {
                                        Log.e(LOG_TAG, "loadRoomsState succeeds");
                                    }
                                }

                                if (succeed) {
                                    succeed &= loadSummaries();

                                    if (!succeed) {
                                        errorDescription = "loadSummaries fails";
                                        Log.e(LOG_TAG, errorDescription);
                                    } else {
                                        Log.e(LOG_TAG, "loadSummaries succeeds");
                                    }
                                }

                                if (succeed) {
                                    succeed &= loadReceipts();

                                    if (!succeed) {
                                        errorDescription = "loadEventsReceipts fails";
                                        Log.e(LOG_TAG, errorDescription);
                                    } else {
                                        Log.e(LOG_TAG, "loadEventsReceipts succeeds");
                                    }
                                }

                                if (succeed) {
                                    succeed &= loadRoomsAccountData();

                                    if (!succeed) {
                                        errorDescription = "loadRoomsAccountData fails";
                                        Log.e(LOG_TAG, errorDescription);
                                    } else {
                                        Log.e(LOG_TAG, "loadRoomsAccountData succeeds");
                                    }
                                }

                                // do not expect having empty list
                                // assume that something is corrupted
                                if (!succeed) {

                                    Log.e(LOG_TAG, "Fail to open the store in background");

                                    // delete all data set mMetadata to null
                                    // backup it to restore it
                                    // the behaviour should be the same as first login
                                    MXFileStoreMetaData tmpMetadata = mMetadata;

                                    deleteAllData(true);

                                    mRoomsToCommitForMessages = new ArrayList<String>();
                                    mRoomsToCommitForStates = new ArrayList<String>();
                                    mRoomsToCommitForSummaries = new ArrayList<String>();
                                    mRoomsToCommitForReceipts = new ArrayList<String>();

                                    mMetadata = tmpMetadata;
                                    mMetadata.mEventStreamToken = null;

                                    //  the event stream token is put to zero to ensure ta
                                    mEventStreamToken = null;
                                }

                                synchronized (this) {
                                    mIsReady = true;
                                }
                                mIsOpening = false;

                                if (null != mListener) {
                                    if (!succeed && !mIsNewStorage) {
                                        Log.e(LOG_TAG, "The store is corrupted.");
                                        mListener.onStoreCorrupted(mCredentials.userId, errorDescription);
                                    } else {
                                        Log.e(LOG_TAG, "The store is opened.");
                                        mListener.onStoreReady(mCredentials.userId);
                                    }
                                }
                            }
                        });
                    }
                };

                Thread t = new Thread(r);
                t.start();
            }
        }
    }

    /**
     * Close the store.
     * Any pending operation must be complete in this call.
     */
    @Override
    public void close() {
        Log.d(LOG_TAG, "Close the store");

        super.close();
        setIsKilled(true);
        mHandlerThread.quit();
        mHandlerThread = null;
    }

    /**
     * Clear the store.
     * Any pending operation must be complete in this call.
     */
    @Override
    public void clear() {
        Log.d(LOG_TAG, "Clear the store");
        super.clear();
        deleteAllData(false);
    }

    /**
     * Clear the filesystem storage.
     * @param init true to init the filesystem dirtree
     */
    private void deleteAllData(boolean init) {
        // delete the dedicated directories
        try {
            ContentUtils.deleteDirectory(mStoreFolderFile);
            if (init) {
                createDirTree(mCredentials.userId);
            }
        } catch(Exception e) {
        }

        if (init) {
            initCommon();
        }
        mMetadata = null;
        mEventStreamToken = null;
    }

    /**
     * Indicate if the MXStore implementation stores data permanently.
     * Permanent storage allows the SDK to make less requests at the startup.
     * @return true if permanent.
     */
    @Override
    public boolean isPermanent() {
        return true;
    }

    /**
     * Check if the initial load is performed.
     * @return true if it is ready.
     */
    @Override
    public boolean isReady() {
        synchronized (this) {
            return mIsReady;
        }
    }

    /**
     * @return true if the store is corrupted.
     */
    @Override
    public boolean isCorrupted() {
        synchronized (this) {
            return mIsCorrupted;
        }
    }

    /**
     * Delete a directory with its content
     * @param directory the base directory
     * @return
     */
    private long directorySize(File directory) {
        long directorySize = 0;

        if (directory.exists()) {
            File[] files = directory.listFiles();

            if (null != files) {
                for(int i=0; i<files.length; i++) {
                    if(files[i].isDirectory()) {
                        directorySize += directorySize(files[i]);
                    }
                    else {
                        directorySize += files[i].length();
                    }
                }
            }
        }

        return directorySize;
    }

    /**
     * Returns to disk usage size in bytes.
     * @return disk usage size
     */
    @Override
    public long diskUsage() {
        return directorySize(mStoreFolderFile);
    }

    /**
     * Set the event stream token.
     * @param token the event stream token
     */
    @Override
    public void setEventStreamToken(String token) {
        Log.d(LOG_TAG, "Set token to " + token);
        super.setEventStreamToken(token);
        mMetaDataHasChanged = true;
    }

    @Override
    public void setDisplayName(String displayName) {
        Log.d(LOG_TAG, "Set setDisplayName to " + displayName);
        mMetaDataHasChanged = true;
        super.setDisplayName(displayName);
    }

    @Override
    public void setAvatarURL(String avatarURL) {
        Log.d(LOG_TAG, "Set setAvatarURL to " + avatarURL);
        mMetaDataHasChanged = true;
        super.setAvatarURL(avatarURL);
    }

    @Override
    public void setThirdPartyIdentifiers(List<ThirdPartyIdentifier> identifiers) {
        Log.d(LOG_TAG, "Set setThirdPartyIdentifiers to " + identifiers);
        mMetaDataHasChanged = true;
        super.setThirdPartyIdentifiers(identifiers);
    }

    @Override
    public void setIgnoredUserIdsList(List<String> users) {
        Log.d(LOG_TAG, "Set setIgnoredUsers to " + users);
        mMetaDataHasChanged = true;
        super.setIgnoredUserIdsList(users);
    }

    /**
     * Define a MXStore listener.
     * @param listener
     */
    @Override
    public void setMXStoreListener(MXStoreListener listener) {
        mListener = listener;
    }

    @Override
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, EventTimeline.Direction direction) {
        boolean canStore = true;

        // do not flush the room messages file
        // when the user reads the room history and the events list size reaches its max size.
        if (direction == EventTimeline.Direction.BACKWARDS) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            if (null != events) {
                canStore = (events.size() < MAX_STORED_MESSAGES_COUNT);

                if (!canStore) {
                    Log.d(LOG_TAG, "storeRoomEvents : do not flush because reaching the max size");
                }
            }
        }

        super.storeRoomEvents(roomId, eventsResponse, direction);

        if (canStore && (mRoomsToCommitForMessages.indexOf(roomId) < 0)) {
            mRoomsToCommitForMessages.add(roomId);
        }
    }

    /**
     * Store a live room event.
     * @param event The event to be stored.
     */
    @Override
    public void storeLiveRoomEvent(Event event) {
        super.storeLiveRoomEvent(event);

        if (mRoomsToCommitForMessages.indexOf(event.roomId) < 0) {
            mRoomsToCommitForMessages.add(event.roomId);
        }
    }

    @Override
    public void deleteEvent(Event event) {
        super.deleteEvent(event);

        if (mRoomsToCommitForMessages.indexOf(event.roomId) < 0) {
            mRoomsToCommitForMessages.add(event.roomId);
        }
    }

    /**
     * Delete the room messages and token files.
     * @param roomId the room id.
     */
    private void deleteRoomMessagesFiles(String roomId) {
        // messages list
        File messagesListFile = new File(mGzStoreRoomsMessagesFolderFile, roomId);

        // remove the files
        if (messagesListFile.exists()) {
            try {
                messagesListFile.delete();
            } catch (Exception e) {
                Log.d(LOG_TAG,"deleteRoomMessagesFiles - messagesListFile failed " + e.getLocalizedMessage());
            }
        }

        File tokenFile = new File(mStoreRoomsTokensFolderFile, roomId);
        if (tokenFile.exists()) {
            try {
                tokenFile.delete();
            } catch (Exception e) {
                Log.d(LOG_TAG,"deleteRoomMessagesFiles - tokenFile failed " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void deleteRoom(String roomId) {
        Log.d(LOG_TAG, "deleteRoom " + roomId);

        super.deleteRoom(roomId);
        deleteRoomMessagesFiles(roomId);
        deleteRoomStateFile(roomId);
        deleteRoomSummaryFile(roomId);
        deleteRoomReceiptsFile(roomId);
        deleteRoomAccountDataFile(roomId);
    }

    @Override
    public void deleteAllRoomMessages(String roomId, boolean keepUnsent) {
        Log.d(LOG_TAG, "deleteAllRoomMessages " + roomId);

        super.deleteAllRoomMessages(roomId, keepUnsent);
        if (!keepUnsent) {
            deleteRoomMessagesFiles(roomId);
        }

        deleteRoomSummaryFile(roomId);

        if (mRoomsToCommitForMessages.indexOf(roomId) < 0) {
            mRoomsToCommitForMessages.add(roomId);
        }

        if (mRoomsToCommitForSummaries.indexOf(roomId) < 0) {
            mRoomsToCommitForSummaries.add(roomId);
        }
    }

    @Override
    public void storeLiveStateForRoom(String roomId) {
        super.storeLiveStateForRoom(roomId);

        if (mRoomsToCommitForStates.indexOf(roomId) < 0) {
            mRoomsToCommitForStates.add(roomId);
        }
    }

    //================================================================================
    // Summary management
    //================================================================================

    @Override
    public void flushSummary(RoomSummary summary) {
        super.flushSummary(summary);

        if (mRoomsToCommitForSummaries.indexOf(summary.getRoomId()) < 0) {
            mRoomsToCommitForSummaries.add(summary.getRoomId());
            saveSummaries();
        }
    }

    @Override
    public void flushSummaries() {
        super.flushSummaries();

        // add any existing roomid to the list to save all
        Collection<String> roomIds = mRoomSummaries.keySet();

        for(String roomId : roomIds) {
            if (mRoomsToCommitForSummaries.indexOf(roomId) < 0) {
                mRoomsToCommitForSummaries.add(roomId);
            }
        }

        saveSummaries();
    }

    @Override
    public RoomSummary storeSummary(String roomId, Event event, RoomState roomState, String selfUserId) {
        RoomSummary summary = super.storeSummary(roomId, event, roomState, selfUserId);

        if (mRoomsToCommitForSummaries.indexOf(roomId) < 0) {
            mRoomsToCommitForSummaries.add(roomId);
        }

        return summary;
    }

    //================================================================================
    // Room messages management
    //================================================================================

    private void saveRoomMessages(String roomId) {
        try {
            deleteRoomMessagesFiles(roomId);

            // messages list
            File messagesListFile = new File(mGzStoreRoomsMessagesFolderFile, roomId);

            File tokenFile = new File(mStoreRoomsTokensFolderFile, roomId);

            LinkedHashMap<String, Event> eventsHash = mRoomEvents.get(roomId);
            String token = mRoomTokens.get(roomId);

            // the list exists ?
            if ((null != eventsHash) && (null != token)) {
                FileOutputStream fos = new FileOutputStream(messagesListFile);
                GZIPOutputStream gz = new GZIPOutputStream(fos);
                ObjectOutputStream out = new ObjectOutputStream(gz);

                LinkedHashMap<String, Event> hashCopy = new LinkedHashMap<String, Event>();
                ArrayList<Event> eventsList = new ArrayList<Event>(eventsHash.values());

                int startIndex = 0;

                // try to reduce the number of stored messages
                // it does not make sense to keep the full history.

                // the method consists in saving messages until finding the oldest known token.
                // At initial sync, it is not saved so keep the whole history.
                // if the user back paginates, the token is stored in the event.
                // if some messages are received, the token is stored in the event.
                if (eventsList.size() > MAX_STORED_MESSAGES_COUNT) {
                    startIndex = eventsList.size() - MAX_STORED_MESSAGES_COUNT;

                    // search backward the first known token
                    for (; !eventsList.get(startIndex).hasToken() && (startIndex > 0); startIndex--)
                        ;

                    // avoid saving huge messages count
                    // with a very verbosed room, the messages token
                    if ((eventsList.size() - startIndex) > (2 * MAX_STORED_MESSAGES_COUNT)) {
                        Log.d(LOG_TAG, "saveRoomsMessage (" + roomId + ") : too many messages, try reducing more");

                        // start from 10 messages
                        startIndex = eventsList.size() - 10;

                        // search backward the first known token
                        for (; !eventsList.get(startIndex).hasToken() && (startIndex > 0); startIndex--)
                            ;
                    }

                    if (startIndex > 0) {
                        Log.d(LOG_TAG, "saveRoomsMessage (" + roomId + ") :  reduce the number of messages " + eventsList.size() + " -> " + (eventsList.size() - startIndex));
                    }
                }

                long t0 = System.currentTimeMillis();

                for (int index = startIndex; index < eventsList.size(); index++) {
                    Event event = eventsList.get(index);
                    event.prepareSerialization();
                    hashCopy.put(event.eventId, event);
                }

                out.writeObject(hashCopy);
                out.close();

                fos = new FileOutputStream(tokenFile);
                out = new ObjectOutputStream(fos);
                out.writeObject(token);
                out.close();

                Log.d(LOG_TAG, "saveRoomsMessage (" + roomId + ") : " + eventsList.size() + " messages saved in " +  (System.currentTimeMillis() - t0) + " ms");
            }
        } catch (Exception e) {
            //Toast.makeText(mContext, "saveRoomsMessage  " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "saveRoomsMessage failed ");
        }
    }

    /**
     * Flush updates rooms messages list files.
     */
    private void saveRoomsMessages() {
        // some updated rooms ?
        if  ((mRoomsToCommitForMessages.size() > 0) && (null != mFileStoreHandler)) {
            // get the list
            final ArrayList<String> fRoomsToCommitForMessages = mRoomsToCommitForMessages;
            mRoomsToCommitForMessages = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            if (!isKilled()) {
                                committing(true);
                                long start = System.currentTimeMillis();

                                for (String roomId : fRoomsToCommitForMessages) {
                                    saveRoomMessages(roomId);
                                }

                                Log.d(LOG_TAG, "saveRoomsMessages : " + fRoomsToCommitForMessages.size() + " rooms in " + (System.currentTimeMillis() - start) + " ms");
                                committing(false);
                            }
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    /**
     * Load room messages from the filesystem.
     * @param roomId the room id.
     * @return true if succeed.
     */
    private boolean loadRoomMessages(final String roomId) {
        boolean succeeded = true;
        boolean shouldSave = false;
        LinkedHashMap<String, Event> events = null;

        try {
            File messagesListFile = new File(mGzStoreRoomsMessagesFolderFile, roomId);

            if (messagesListFile.exists()) {
                FileInputStream fis = new FileInputStream(messagesListFile);
                GZIPInputStream gz = new GZIPInputStream(fis);
                ObjectInputStream ois = new ObjectInputStream(gz);
                events = (LinkedHashMap<String, Event>) ois.readObject();

                ArrayList<String> eventIds = mRoomEventIds.get(roomId);

                if (null == eventIds) {
                    eventIds = new ArrayList<String>();
                    mRoomEventIds.put(roomId, eventIds);
                }

                long undeliverableTs = 1L << 50;

                // finalizes the deserialization
                for (Event event : events.values()) {
                    // if a message was not sent, mark at as UNDELIVERABLE
                    if ((event.mSentState == Event.SentState.UNDELIVERABLE) || (event.mSentState == Event.SentState.UNSENT) || (event.mSentState == Event.SentState.SENDING) || (event.mSentState == Event.SentState.WAITING_RETRY)) {
                        event.mSentState = Event.SentState.UNDELIVERABLE;
                        event.originServerTs = undeliverableTs++;
                        shouldSave = true;
                    }

                    event.finalizeDeserialization();

                    eventIds.add(event.eventId);
                }

                ois.close();
            }
        } catch (Exception e){
            succeeded = false;
            // the exception got some null message to display at least its class.
            Log.e(LOG_TAG, "loadRoomMessages failed : " + e.toString());
        }

        // succeeds to extract the message list
        if (null != events) {
            // create the room object
            Room room = new Room();
            room.init(roomId, null);
            // do not wait that the live state update
            room.setReadyState(true);
            storeRoom(room);

            mRoomEvents.put(roomId, events);
        }

        if (shouldSave) {
            saveRoomMessages(roomId);
        }

        return succeeded;
    }

    /**
     * Load the room token from the file system.
     * @param roomId the room id.
     * @return true if it succeeds.
     */
    private boolean loadRoomToken(final String roomId) {
        boolean succeed = true;

        Room room = getRoom(roomId);

        // should always be true
        if (null != room) {
            String token = null;

            try {
                File messagesListFile = new File(mStoreRoomsTokensFolderFile, roomId);

                FileInputStream fis = new FileInputStream(messagesListFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                token = (String) ois.readObject();

                // check if the oldest event has a token.
                LinkedHashMap<String, Event> eventsHash = mRoomEvents.get(roomId);
                if ((null != eventsHash) && (eventsHash.size() > 0)) {
                    Event event = eventsHash.values().iterator().next();

                    // the room history could have been reduced to save memory
                    // so, if the oldest messages has a token, use it instead of the stored token.
                    if (null != event.mToken) {
                        token = event.mToken;
                    }
                }

                ois.close();
            } catch (Exception e) {
                succeed = false;
                Log.e(LOG_TAG, "loadRoomToken failed : " + e.toString());
            }

            if (null != token) {
                mRoomTokens.put(roomId, token);
            } else {
                deleteRoom(roomId);
            }
        } else {
            try {
                File messagesListFile = new File(mStoreRoomsTokensFolderFile, roomId);
                messagesListFile.delete();

            } catch (Exception e) {
            }
        }

        return succeed;
    }

    /**
     * Load room messages from the filesystem.
     * @return  true if the operation succeeds.
     */
    private boolean loadRoomsMessages() {
        boolean succeed = true;

        try {
            // extract the messages list
            String[] filenames = mGzStoreRoomsMessagesFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; succeed && (index < filenames.length); index++) {
                succeed &= loadRoomMessages(filenames[index]);
            }

            if (succeed) {
                Log.d(LOG_TAG, "loadRoomMessages : " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");
            }

            // extract the tokens list
            filenames = mStoreRoomsTokensFolderFile.list();

            start = System.currentTimeMillis();

            for(int index = 0; succeed && (index < filenames.length); index++) {
                succeed &= loadRoomToken(filenames[index]);
            }

            if (succeed) {
                Log.d(LOG_TAG, "loadRoomToken : " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");
            }

        } catch (Exception e) {
            succeed = false;
            Log.e(LOG_TAG, "loadRoomToken failed : " + e.getMessage());
        }

        return succeed;
    }

    //================================================================================
    // Room states management
    //================================================================================

    /**
     * Delete the room state file.
     * @param roomId the room id.
     */
    private void deleteRoomStateFile(String roomId) {
        // states list
        File statesFile = statesFile = new File(mGzStoreRoomsStateFolderFile, roomId);

        if (statesFile.exists()) {
            try {
                statesFile.delete();
            } catch (Exception e) {
            }
        }

    }

    /**
     * Save the room state.
     * @param roomId the room id.
     */
    private void saveRoomState(String roomId) {
        try {
            deleteRoomStateFile(roomId);

            File roomStateFile = new File(mGzStoreRoomsStateFolderFile, roomId);
            Room room = mRooms.get(roomId);

            if (null != room) {
                long start1 = System.currentTimeMillis();
                FileOutputStream fos = new FileOutputStream(roomStateFile);
                GZIPOutputStream gz = new GZIPOutputStream(fos);
                ObjectOutputStream out = new ObjectOutputStream(gz);

                out.writeObject(room.getState());
                out.close();
                Log.d(LOG_TAG, "saveRoomsState " + room.getState().getMembers().size() + " : " + (System.currentTimeMillis() - start1) + " ms");
            }

        } catch (Exception e) {
            // (mContext, "saveRoomsState failed " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "saveRoomsState failed : " + e.getMessage());
        }
    }

    /**
     * Flush the room state files.
     */
    private void saveRoomStates() {
        if ((mRoomsToCommitForStates.size() > 0) && (null != mFileStoreHandler)) {
            // get the list
            final ArrayList<String> fRoomsToCommitForStates = mRoomsToCommitForStates;
            mRoomsToCommitForStates = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            if (!isKilled()) {
                                committing(true);

                                long start = System.currentTimeMillis();

                                for (String roomId : fRoomsToCommitForStates) {
                                    saveRoomState(roomId);
                                }

                                Log.d(LOG_TAG, "saveRoomsState : " + fRoomsToCommitForStates.size() + " rooms in " + (System.currentTimeMillis() - start) + " ms");

                                committing(false);
                            }
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    /**
     * Load a room state from the file system.
     * @param roomId the room id.
     * @return true if the operation succeeds.
     */
    private boolean loadRoomState(final String roomId) {
        boolean succeed = true;

        Room room = getRoom(roomId);

        // should always be true
        if (null != room) {
            RoomState liveState = null;
            boolean shouldSave = false;

            try {
                // the room state is not zipped
                File messagesListFile = new File(mGzStoreRoomsStateFolderFile, roomId);

                // new format
                if (messagesListFile.exists()) {
                    FileInputStream fis = new FileInputStream(messagesListFile);
                    GZIPInputStream gz = new GZIPInputStream(fis);
                    ObjectInputStream ois = new ObjectInputStream(gz);
                    liveState = (RoomState) ois.readObject();
                    ois.close();

                }
            } catch (Exception e) {
                succeed = false;
                Log.e(LOG_TAG, "loadRoomState failed : " + e.getMessage());
            }

            if (null != liveState) {
                room.getLiveTimeLine().setState(liveState);

                // check if some user can be retrieved from the room members
                Collection<RoomMember> members = liveState.getMembers();

                for(RoomMember member : members) {
                    updateUserWithRoomMemberEvent(member);
                }

                // force to use the new format
                if (shouldSave) {
                    saveRoomState(roomId);
                }
            } else {
                deleteRoom(roomId);
            }
        } else {
            try {
                File messagesListFile = new File(mGzStoreRoomsStateFolderFile, roomId);
                messagesListFile.delete();

            } catch (Exception e) {
                Log.e(LOG_TAG, "loadRoomState failed to delete a file : " + e.getMessage());
            }
        }

        return succeed;
    }

    /**
     * Load room state from the file system.
     * @return true if the operation succeeds.
     */
    private boolean loadRoomsState() {
        boolean succeed = true;

        try {
            long start = System.currentTimeMillis();

            String[] filenames = null;

            filenames = mGzStoreRoomsStateFolderFile.list();

            for(int index = 0; succeed && (index < filenames.length); index++) {
                succeed &= loadRoomState(filenames[index]);
            }

            Log.d(LOG_TAG, "loadRoomsState " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");

        } catch (Exception e) {
            succeed = false;
            Log.e(LOG_TAG, "loadRoomsState failed : " + e.getMessage());
        }

        return succeed;
    }

    //================================================================================
    // AccountData management
    //================================================================================

    /**
     * Delete the room account data file.
     * @param roomId the room id.
     */
    private void deleteRoomAccountDataFile(String roomId) {
        File file = new File(mStoreRoomsAccountDataFolderFile, roomId);

        // remove the files
        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception e) {
                Log.e(LOG_TAG, "deleteRoomAccountDataFile failed : " + e.getMessage());
            }
        }
    }

    /**
     * Flush the pending account data.
     */
    private void saveRoomsAccountData() {
        if ((mRoomsToCommitForAccountData.size() > 0) && (null != mFileStoreHandler)) {
            // get the list
            final ArrayList<String> fRoomsToCommitForAccountData = mRoomsToCommitForAccountData;
            mRoomsToCommitForAccountData = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            if (!isKilled()) {
                                committing(true);

                                long start = System.currentTimeMillis();

                                for (String roomId : fRoomsToCommitForAccountData) {
                                    try {
                                        deleteRoomAccountDataFile(roomId);

                                        RoomAccountData accountData = mRoomAccountData.get(roomId);

                                        if (null != accountData) {
                                            File accountDataFile = new File(mStoreRoomsAccountDataFolderFile, roomId);
                                            FileOutputStream fos = new FileOutputStream(accountDataFile);
                                            ObjectOutputStream out = new ObjectOutputStream(fos);
                                            out.writeObject(accountData);
                                            out.close();
                                        }

                                    } catch (Exception e) {
                                        //Toast.makeText(mContext, "saveRoomsAccountData failed " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                        Log.e(LOG_TAG, "saveRoomsAccountData failed : " + e.getMessage());
                                    }
                                }

                                Log.d(LOG_TAG, "saveSummaries : " + fRoomsToCommitForAccountData.size() + " account data in " + (System.currentTimeMillis() - start) + " ms");

                                committing(false);
                            }
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    /***
     * Load the account Data of a dedicated room.
     * @param roomId the room Id
     * @return true if the operation succeeds.
     */
    private boolean loadRoomAccountData(final String roomId) {
        boolean succeeded = true;
        RoomAccountData roomAccountData = null;

        try {
            File accountDataFile = new File(mStoreRoomsAccountDataFolderFile, roomId);

            if (accountDataFile.exists()) {
                FileInputStream fis = new FileInputStream(accountDataFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                roomAccountData = (RoomAccountData) ois.readObject();

                ois.close();
            }
        } catch (Exception e){
            succeeded = false;
            Log.e(LOG_TAG, "loadRoomAccountData failed : " + e.toString());
        }

        // succeeds to extract the message list
        if (null != roomAccountData) {
            Room room = getRoom(roomId);

            if (null != room) {
                room.setAccountData(roomAccountData);
            }
        }

        return succeeded;
    }

    /**
     * Load room accountData from the filesystem.
     * @return true if the operation succeeds.
     */
    private boolean loadRoomsAccountData() {
        boolean succeed = true;

        try {
            // extract the messages list
            String[] filenames = mStoreRoomsAccountDataFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; succeed && (index < filenames.length); index++) {
                succeed &= loadRoomAccountData(filenames[index]);
            }

            if (succeed) {
                Log.d(LOG_TAG, "loadRoomsAccountData : " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");
            }

        } catch (Exception e) {
            succeed = false;
            Log.e(LOG_TAG, "loadRoomsAccountData failed : " + e.getMessage());
        }

        return succeed;
    }

    @Override
    public void storeAccountData(String roomId, RoomAccountData accountData) {
        super.storeAccountData(roomId, accountData);

        if (null != roomId) {
            Room room = mRooms.get(roomId);

            // sanity checks
            if ((room != null) && (null != accountData)) {
                if (mRoomsToCommitForAccountData.indexOf(roomId) < 0) {
                    mRoomsToCommitForAccountData.add(roomId);
                }
            }
        }
    }

    //================================================================================
    // Summary management
    //================================================================================

    /**
     * Delete the room summary file.
     * @param roomId the room id.
     */
    private void deleteRoomSummaryFile(String roomId) {
        // states list
        File statesFile = new File(mStoreRoomsSummaryFolderFile, roomId);

        // remove the files
        if (statesFile.exists()) {
            try {
                statesFile.delete();
            } catch (Exception e) {
                Log.e(LOG_TAG, "deleteRoomSummaryFile failed : " + e.getMessage());
            }
        }
    }

    /**
     * Flush the pending summaries.
     */
    private void saveSummaries() {
        if ((mRoomsToCommitForSummaries.size() > 0) && (null != mFileStoreHandler)) {
            // get the list
            final ArrayList<String> fRoomsToCommitForSummaries = mRoomsToCommitForSummaries;
            mRoomsToCommitForSummaries = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            if (!isKilled()) {
                                committing(true);

                                long start = System.currentTimeMillis();

                                for (String roomId : fRoomsToCommitForSummaries) {
                                    try {
                                        deleteRoomSummaryFile(roomId);

                                        File roomSummaryFile = new File(mStoreRoomsSummaryFolderFile, roomId);
                                        RoomSummary roomSummary = mRoomSummaries.get(roomId);

                                        if (null != roomSummary) {
                                            roomSummary.getLatestEvent().prepareSerialization();

                                            FileOutputStream fos = new FileOutputStream(roomSummaryFile);
                                            ObjectOutputStream out = new ObjectOutputStream(fos);

                                            out.writeObject(roomSummary);
                                            out.close();
                                        }

                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "saveSummaries failed : " + e.getMessage());
                                        // Toast.makeText(mContext, "saveSummaries failed " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }

                                Log.d(LOG_TAG, "saveSummaries : " + fRoomsToCommitForSummaries.size() + " summaries in " + (System.currentTimeMillis() - start) + " ms");

                                committing(false);
                            }
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    /**
     * Load the room summary from the files system.
     * @param roomId the room id.
     * @return true if the operation succeeds;
     */
    private boolean loadSummary(final String roomId) {
        boolean succeed = true;

        // do not check if the room exists here.
        // if the user is invited to a room, the room object is not created until it is joined.
        RoomSummary summary = null;

        try {
            File messagesListFile = new File(mStoreRoomsSummaryFolderFile, roomId);

            FileInputStream fis = new FileInputStream(messagesListFile);
            ObjectInputStream ois = new ObjectInputStream(fis);

            summary = (RoomSummary) ois.readObject();
            ois.close();
        } catch (Exception e){
            succeed = false;
            Log.e(LOG_TAG, "loadSummary failed : " + e.getMessage());
        }

        if (null != summary) {
            summary.getLatestEvent().finalizeDeserialization();

            Room room = getRoom(summary.getRoomId());

            // the room state is not saved in the summary.
            // it is restored from the room
            if (null != room) {
                summary.setLatestRoomState(room.getState());
            }

            mRoomSummaries.put(roomId, summary);
        }

        return succeed;
    }

    /**
     * Load room summaries from the file system.
     * @return true if the operation succeeds.
     */
    private boolean loadSummaries() {
        boolean succeed = true;
        try {
            // extract the room states
            String[] filenames = mStoreRoomsSummaryFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; succeed && (index < filenames.length); index++) {
                succeed &= loadSummary(filenames[index]);
            }

            Log.d(LOG_TAG, "loadSummaries " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");
        }
        catch (Exception e) {
            succeed = false;
            Log.e(LOG_TAG, "loadSummaries failed : " + e.getMessage());
        }

        return succeed;
    }

    //================================================================================
    // Metadata management
    //================================================================================

    /**
     * Load the metadata info from the file system.
     */
    private void loadMetaData() {
        long start = System.currentTimeMillis();

        // init members
        mEventStreamToken = null;
        mMetadata = null;

        try {
            File metaDataFile = new File(mStoreFolderFile, MXFILE_STORE_METADATA_FILE_NAME);

            if (metaDataFile.exists()) {
                FileInputStream fis = new FileInputStream(metaDataFile);
                ObjectInputStream out = new ObjectInputStream(fis);

                mMetadata = (MXFileStoreMetaData)out.readObject();

                // remove pending \n
                if (null != mMetadata.mUserDisplayName) {
                    mMetadata.mUserDisplayName.trim();
                }

                // extract the latest event stream token
                mEventStreamToken = mMetadata.mEventStreamToken;
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "loadMetaData failed : " + e.getMessage());
            mMetadata = null;
            mEventStreamToken = null;
        }

        Log.d(LOG_TAG, "loadMetaData : " + (System.currentTimeMillis() - start) + " ms");
    }

    /**
     * flush the metadata info from the file system.
     */
    private void saveMetaData() {
        if ((mMetaDataHasChanged) && (null != mFileStoreHandler)) {
            mMetaDataHasChanged = false;

            final MXFileStoreMetaData fMetadata = mMetadata.deepCopy();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            if (!mIsKilled) {
                                committing(true);

                                long start = System.currentTimeMillis();

                                try {
                                    File metaDataFile = new File(mStoreFolderFile, MXFILE_STORE_METADATA_FILE_NAME);

                                    if (metaDataFile.exists()) {
                                        metaDataFile.delete();
                                    }

                                    FileOutputStream fos = new FileOutputStream(metaDataFile);
                                    ObjectOutputStream out = new ObjectOutputStream(fos);

                                    out.writeObject(fMetadata);
                                    out.close();
                                } catch (Exception e) {
                                    // Toast.makeText(mContext, "saveMetaData failed  " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    Log.e(LOG_TAG, "saveMetaData failed : " + e.getMessage());
                                }

                                Log.d(LOG_TAG, "saveMetaData : " + (System.currentTimeMillis() - start) + " ms");
                                committing(false);
                            }
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    //================================================================================
    // Event receipts management
    //================================================================================

    /**
     * Store the receipt for an user in a room
     * @param receipt The event
     * @param roomId The roomId
     * @return true if the receipt has been stored
     */
    @Override
    public boolean storeReceipt(ReceiptData receipt, String roomId) {
        boolean res = super.storeReceipt(receipt, roomId);

        if (res) {
            synchronized (this) {
                if (mRoomsToCommitForReceipts.indexOf(roomId) < 0) {
                    mRoomsToCommitForReceipts.add(roomId);
                }
            }
        }

        return res;
    }

    /***
     * Load the events receipts.
     * @param roomId the room Id
     * @return true if the operation succeeds.
     */
    private boolean loadReceipts(String roomId) {
        Map<String, ReceiptData> receipts = null;
        try {
            File file = new File(mStoreRoomsMessagesReceiptsFolderFile, roomId);

            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);

            receipts = (Map<String, ReceiptData>) ois.readObject();
            ois.close();
        } catch (EOFException endOfFile) {
            // there is no read receipt for this room
        }
        catch (Exception e) {
            // Toast.makeText(mContext, "loadReceipts failed" + e, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "loadReceipts failed : " + e.getMessage());
            return false;
        }

        if (null != receipts) {
            mReceiptsByRoomId.put(roomId, receipts);
        }

        return true;
    }

    /**
     * Load event receipts from the file system.
     * @return true if the operation succeeds.
     */
    private boolean loadReceipts() {
        boolean succeed = true;
        try {
            // extract the room states
            String[] filenames = mStoreRoomsMessagesReceiptsFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; succeed && (index < filenames.length); index++) {

                succeed &= loadReceipts(filenames[index]);
            }

            Log.d(LOG_TAG, "loadReceipts " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");
        }
        catch (Exception e) {
            succeed = false;
            //Toast.makeText(mContext, "loadReceipts failed" + e, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "loadReceipts failed : " + e.getMessage());
        }

        return succeed;
    }

    /**
     * Flush the events receipts
     * @param roomId the roomId.
     */
    public void saveReceipts(final String roomId) {
        final Map<String, ReceiptData> receipts = mReceiptsByRoomId.get(roomId);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                mFileStoreHandler.post(new Runnable() {
                    public void run() {
                        if (!mIsKilled) {
                            committing(true);

                            File receiptFile = new File(mStoreRoomsMessagesReceiptsFolderFile, roomId);

                            if (receiptFile.exists()) {
                                receiptFile.delete();
                            }

                            // save only if there is some read receips
                            if ((null != receipts) && (receipts.size() > 0)) {
                                long start = System.currentTimeMillis();

                                try {
                                    FileOutputStream fos = new FileOutputStream(receiptFile);
                                    ObjectOutputStream out = new ObjectOutputStream(fos);
                                    out.writeObject(receipts);
                                    out.close();
                                } catch (Exception e) {
                                    //Toast.makeText(mContext, "saveReceipts failed " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    Log.e(LOG_TAG, "saveReceipts failed : " + e.getMessage());
                                }

                                Log.d(LOG_TAG, "saveReceipts : roomId " + roomId + " eventId : " + (System.currentTimeMillis() - start) + " ms");
                            }

                            committing(false);
                        }
                    }
                });
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    /**
     * Save the events receipts.
     */
    public void saveReceipts() {

        synchronized (this) {
            ArrayList<String> roomsToCommit = mRoomsToCommitForReceipts;

            for (String roomId : roomsToCommit) {
                saveReceipts(roomId);
            }

            mRoomsToCommitForReceipts.clear();
        }
    }

    /**
     * Delete the room receipts
     * @param roomId the room id.
     */
    private void deleteRoomReceiptsFile(String roomId) {
        File receiptsFile = new File(mStoreRoomsMessagesReceiptsFolderFile, roomId);

        // remove the files
        if (receiptsFile.exists()) {
            try {
                receiptsFile.delete();
            } catch (Exception e) {
                Log.d(LOG_TAG,"deleteReceiptsFile - failed " + e.getLocalizedMessage());
            }
        }
    }
}
