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
import android.os.HandlerThread;
import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;


/**
 * An in-file IMXStore.
 */
public class MXFileStore extends MXMemoryStore {
    private static final String LOG_TAG = "MXFileStore";

    // some constant values
    final int MXFILE_VERSION = 1;

    final String MXFILE_STORE_FOLDER = "MXFileStore";
    final String MXFILE_STORE_METADATA_FILE_NAME = "MXFileStore";

    final String MXFILE_STORE_ROOMS_MESSAGES_FOLDER = "messages";
    final String MXFILE_STORE_ROOMS_TOKENS_FOLDER = "tokens";
    final String MXFILE_STORE_ROOMS_STATE_FOLDER = "state";
    final String MXFILE_STORE_ROOMS_SUMMARY_FOLDER = "summary";

    private Context mContext = null;

    private android.os.Handler mFileStoreHandler;

    // the data is read from the file system
    private boolean mIsReady = false;

    // the store is currently opening
    private boolean mIsOpening = false;

    private MXStoreListener mListener = null;

    // List of rooms to save on [MXStore commit]
    private ArrayList<String> mRoomsToCommitForMessages;

    private ArrayList<String> mRoomsToCommitForStates;
    private ArrayList<String> mRoomsToCommitForSummaries;

    // Flag to indicate metaData needs to be store
    private boolean mMetaDataHasChanged = false;

    // The path of the MXFileStore folders
    private File mStoreFolderFile = null;
    private File mStoreRoomsMessagesFolderFile = null;
    private File mStoreRoomsTokensFolderFile = null;
    private File mStoreRoomsStateFolderFile = null;
    private File mStoreRoomsSummaryFolderFile = null;

    // the background thread
    private HandlerThread mHandlerThread;

    /**
     * Create the file store dirtree
     */
    private void createDirTree(String userId) {
        // data path
        // MXFileStore/userID/
        // MXFileStore/userID/MXFileStore
        // MXFileStore/userID/Messages/
        // MXFileStore/userID/Tokens/
        // MXFileStore/userID/States/
        // MXFileStore/userID/Summaries/

        // create the dirtree
        mStoreFolderFile = new File(new File(mContext.getApplicationContext().getFilesDir(), MXFILE_STORE_FOLDER), userId);

        if (!mStoreFolderFile.exists()) {
            mStoreFolderFile.mkdirs();
        }

        mStoreRoomsMessagesFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_MESSAGES_FOLDER);
        if (!mStoreRoomsMessagesFolderFile.exists()) {
            mStoreRoomsMessagesFolderFile.mkdirs();
        }

        mStoreRoomsTokensFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_TOKENS_FOLDER);
        if (!mStoreRoomsTokensFolderFile.exists()) {
            mStoreRoomsTokensFolderFile.mkdirs();
        }

        mStoreRoomsStateFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_STATE_FOLDER);
        if (!mStoreRoomsStateFolderFile.exists()) {
            mStoreRoomsStateFolderFile.mkdirs();
        }

        mStoreRoomsSummaryFolderFile = new File(mStoreFolderFile, MXFILE_STORE_ROOMS_SUMMARY_FOLDER);
        if (!mStoreRoomsSummaryFolderFile.exists()) {
            mStoreRoomsSummaryFolderFile.mkdirs();
        }
    }

    /**
     * Default constructor
     * @param credentials the expected credentials
     */
    public MXFileStore(Credentials credentials, Context context) {
        initCommon();
        mContext = context;
        mIsReady = false;
        mCredentials = credentials;

        mHandlerThread = new HandlerThread("MyBackgroundThread");

        createDirTree(credentials.userId);

        // updated data
        mRoomsToCommitForMessages = new ArrayList<String>();
        mRoomsToCommitForStates = new ArrayList<String>();
        mRoomsToCommitForSummaries = new ArrayList<String>();

        // check if the metadata file exists and if it is valid
        loadMetaData();

        if ( (null == mMetadata) ||
             (mMetadata.mVersion != MXFILE_VERSION) ||
             !mMetadata.mHomeServer.equals(credentials.homeServer) ||
             !mMetadata.mUserId.equals(credentials.userId) ||
             !mMetadata.mAccessToken.equals(credentials.accessToken)) {
            deleteAllData();
        }

        // create the medatata file if it does not exist
        if (null == mMetadata) {
            mMetadata = new MXFileStoreMetaData();
            mMetadata.mHomeServer = credentials.homeServer;
            mMetadata.mUserId = credentials.userId;
            mMetadata.mAccessToken = credentials.accessToken;
            mMetadata.mVersion = MXFILE_VERSION;
            mMetaDataHasChanged = true;
            saveMetaData();

            // nothing to load so ready to work
            mIsReady = true;
        }
    }

    /**
     * Save changes in the store.
     * If the store uses permanent storage like database or file, it is the optimised time
     * to commit the last changes.
     */
    @Override
    public void commit() {
        // Save data only if metaData exists
        if (null != mMetadata) {
            saveRoomsMessages();
            saveRoomsState();
            saveMetaData();
            saveSummaries();
        }
    }

    /**
     * Open the store.
     */
    public void open() {
        super.open();

        if (!mIsReady && !mIsOpening && (mMetadata != null)) {
            mIsOpening = true;

            mHandlerThread.start();
            mFileStoreHandler = new android.os.Handler(mHandlerThread.getLooper());

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            boolean succeed = true;

                            succeed &= loadRoomsMessages();
                            succeed &= loadRoomsState();
                            succeed &= loadSummaries();

                            mIsReady = true;
                            mIsOpening = false;

                            // do not expect having empty list
                            // assume that something is corrupted
                            if (!succeed) {
                                deleteAllData();

                                mRoomsToCommitForMessages = new ArrayList<String>();
                                mRoomsToCommitForStates = new ArrayList<String>();
                                mRoomsToCommitForSummaries = new ArrayList<String>();

                                mMetadata = new MXFileStoreMetaData();
                                mMetadata.mHomeServer = mCredentials.homeServer;
                                mMetadata.mUserId = mCredentials.userId;
                                mMetadata.mAccessToken = mCredentials.accessToken;
                                mMetadata.mVersion = MXFILE_VERSION;
                                mMetaDataHasChanged = true;
                                saveMetaData();
                            }

                            if (null != mListener) {
                                mListener.onStoreReady(mCredentials.userId);
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
     * Close the store.
     * Any pending operation must be complete in this call.
     */
    @Override
    public void close() {
        super.close();
        mHandlerThread.quit();
    }

    private void deleteAllData()
    {
        // delete the dedicated directories
        try {
            mStoreFolderFile.delete();
            createDirTree(mCredentials.userId);
        } catch(Exception e) {
        }

        initCommon();
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
        return mIsReady;
    }

    /**
     * Set the event stream token.
     * @param token the event stream token
     */
    @Override
    public void setEventStreamToken(String token) {
        super.setEventStreamToken(token);
        mMetaDataHasChanged = true;
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
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction) {
        super.storeRoomEvents(roomId, eventsResponse, direction);

        if (mRoomsToCommitForMessages.indexOf(roomId) < 0) {
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
    public boolean updateEventContent(String roomId, String eventId, JsonObject newContent) {
        Boolean isReplaced = super.updateEventContent(roomId, eventId, newContent);

        if (isReplaced) {
            if (mRoomsToCommitForMessages.indexOf(roomId) < 0) {
                mRoomsToCommitForMessages.add(roomId);
            }
        }

        return isReplaced;
    }

    @Override
    public void deleteEvent(Event event) {
        super.deleteEvent(event);

        if (mRoomsToCommitForMessages.indexOf(event.roomId) < 0) {
            mRoomsToCommitForMessages.add(event.roomId);
        }
    }

    private void clearRoomMessagesFiles(String roomId) {
        // messages list
        File messagesListFile = new File(mStoreRoomsMessagesFolderFile, roomId);
        File tokenFile = new File(mStoreRoomsTokensFolderFile, roomId);

        // remove the files
        if (messagesListFile.exists()) {
            try {
                messagesListFile.delete();
            } catch (Exception e) {
            }
        }

        if (tokenFile.exists()) {
            try {
                tokenFile.delete();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void deleteRoom(String roomId) {
        super.deleteRoom(roomId);
        clearRoomMessagesFiles(roomId);
    }


    @Override
    public void storeStatesForRoom(String roomId) {
        super.storeStatesForRoom(roomId);

        if (mRoomsToCommitForStates.indexOf(roomId) < 0) {
            mRoomsToCommitForStates.add(roomId);
        }
    }

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
    public void storeSummary(String matrixId, String roomId, Event event, RoomState roomState, String selfUserId) {
        super.storeSummary(matrixId, roomId, event, roomState, selfUserId);

        if (mRoomsToCommitForSummaries.indexOf(roomId) < 0) {
            mRoomsToCommitForSummaries.add(roomId);
        }
    }

    /**
     * Save updates rooms messages list
     */
    private void saveRoomsMessages() {
        // some updated rooms ?
        if  (mRoomsToCommitForMessages.size() > 0) {
            // get the list
            final ArrayList<String> fRoomsToCommitForMessages = mRoomsToCommitForMessages;
            mRoomsToCommitForMessages = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            long start = System.currentTimeMillis();

                            for(String roomId : fRoomsToCommitForMessages) {
                                try {
                                    clearRoomMessagesFiles(roomId);

                                    // messages list
                                    File messagesListFile = new File(mStoreRoomsMessagesFolderFile, roomId);
                                    File tokenFile = new File(mStoreRoomsTokensFolderFile, roomId);

                                    LinkedHashMap<String, Event> value = mRoomEvents.get(roomId);
                                    String token = mRoomTokens.get(roomId);

                                    // the list exists ?
                                    if ((null != value) && (null != token)) {
                                        FileOutputStream fos = new FileOutputStream(messagesListFile);
                                        ObjectOutputStream out = new ObjectOutputStream(fos);

                                        for (Event event : value.values()) {
                                            event.prepareSerialization();
                                        }

                                        out.writeObject(value);
                                        out.close();

                                        fos = new FileOutputStream(tokenFile);
                                        out = new ObjectOutputStream(fos);
                                        out.writeObject(token);
                                        out.close();
                                    }
                                } catch (Exception e) {
                                }
                            }

                            Log.e(LOG_TAG, "saveRoomsMessages : " + fRoomsToCommitForMessages.size() + " rooms in " + (System.currentTimeMillis() - start) + " ms");
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    private boolean loadRoomMessages(final String roomId) {
        Boolean succeeded = true;

        LinkedHashMap<String, Event> events = null;

        try {
            File messagesListFile = new File(mStoreRoomsMessagesFolderFile, roomId);

            FileInputStream fis = new FileInputStream(messagesListFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            events = (LinkedHashMap<String, Event>) ois.readObject();

            for (Event event : events.values()) {
                event.finalizeDeserialization();
            }

            ois.close();
        } catch (Exception e){
            succeeded = false;
            Log.e(LOG_TAG, "loadRoomMessages : " + e.getMessage());
        }

        // succeeds to extract the message list
        if (null != events) {
            // create the room object
            Room room = new Room();
            room.setRoomId(roomId);
            // do not wait that the live state update
            room.setReadyState(true);
            storeRoom(room);

            mRoomEvents.put(roomId, events);
        }

        return succeeded;
    }

    private Boolean loadRoomToken(final String roomId) {
        Boolean succeed = true;

        Room room = getRoom(roomId);

        // should always be true
        if (null != room) {
            String token = null;

            try {
                File messagesListFile = new File(mStoreRoomsTokensFolderFile, roomId);

                FileInputStream fis = new FileInputStream(messagesListFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                token = (String) ois.readObject();

                ois.close();
            } catch (Exception e) {
                succeed = false;
                Log.e(LOG_TAG, "loadRoomToken : " + e.getMessage());
            }

            if (null != token) {
                mRoomTokens.put(roomId, token);
            } else {
                deleteRoom(roomId);
            }
        }

        return succeed;
    }

    /**
     * Load room messages
     */
    private boolean loadRoomsMessages() {
        Boolean succeed = true;

        try {
            // extract the messages list
            String[] filenames = mStoreRoomsMessagesFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; index < filenames.length; index++) {
                succeed &= loadRoomMessages(filenames[index]);
            }

            Log.e(LOG_TAG, "loadRoomMessages : " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");

            // extract the tokens list
            filenames = mStoreRoomsTokensFolderFile.list();

            start = System.currentTimeMillis();

            for(int index = 0; index < filenames.length; index++) {
                succeed &= loadRoomToken(filenames[index]);
            }

            Log.e(LOG_TAG, "loadRoomToken : " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");


        } catch (Exception e) {
        }

        return succeed;
    }

    private void clearRoomStatesFiles(String roomId) {
        // states list
        File statesFile = new File(mStoreRoomsStateFolderFile, roomId);

        // remove the files
        if (statesFile.exists()) {
            try {
                statesFile.delete();
            } catch (Exception e) {
            }
        }
    }

    private void saveRoomsState() {
        if (mRoomsToCommitForStates.size() > 0) {

            // get the list
            final ArrayList<String> fRoomsToCommitForStates = mRoomsToCommitForStates;
            mRoomsToCommitForStates = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            long start = System.currentTimeMillis();

                            for(String roomId : fRoomsToCommitForStates) {
                                try {
                                    clearRoomStatesFiles(roomId);

                                    File metaDataFile = new File(mStoreRoomsStateFolderFile, roomId);
                                    Room room = mRooms.get(roomId);

                                    if (null != room) {

                                        FileOutputStream fos = new FileOutputStream(metaDataFile);
                                        ObjectOutputStream out = new ObjectOutputStream(fos);

                                        out.writeObject(room.getLiveState());
                                        out.writeObject(room.getBackState());
                                        out.close();
                                    }

                                } catch (Exception e) {
                                    e = e;
                                }
                            }

                            Log.e(LOG_TAG, "saveRoomsState : " + fRoomsToCommitForStates.size() + " rooms in " + (System.currentTimeMillis() - start) + " ms");
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    private boolean loadRoomState(final String roomId) {
        Boolean succeed = true;
        Room room = getRoom(roomId);

        // should always be true
        if (null != room) {
            RoomState liveState = null;
            RoomState backState = null;

            try {
                File messagesListFile = new File(mStoreRoomsStateFolderFile, roomId);

                FileInputStream fis = new FileInputStream(messagesListFile);
                ObjectInputStream ois = new ObjectInputStream(fis);

                liveState = (RoomState) ois.readObject();
                backState = (RoomState) ois.readObject();

                ois.close();
            } catch (Exception e) {
                succeed = false;
                Log.e(LOG_TAG, "loadRoomState : " + e.getMessage());
            }

            if ((null != liveState) && (null != backState)) {
                room.setLiveState(liveState);
                room.setBackState(backState);
            } else {
                deleteRoom(roomId);
            }
        }

        return succeed;
    }

    /**
     * Load room messages
     */
    private boolean loadRoomsState() {
        Boolean succeed = true;

        try {
            // extract the room states
            String[] filenames = mStoreRoomsStateFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; index < filenames.length; index++) {
                succeed &= loadRoomState(filenames[index]);
            }

            Log.e(LOG_TAG, "loadRoomsState " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");

        } catch (Exception e) {
        }

        return succeed;
    }

    private void clearRoomSummaryFiles(String roomId) {
        // states list
        File statesFile = new File(mStoreRoomsSummaryFolderFile, roomId);

        // remove the files
        if (statesFile.exists()) {
            try {
                statesFile.delete();
            } catch (Exception e) {
            }
        }
    }

    private void saveSummaries() {
        if (mRoomsToCommitForSummaries.size() > 0) {
            // get the list
            final ArrayList<String> fRoomsToCommitForSummaries = mRoomsToCommitForSummaries;
            mRoomsToCommitForSummaries = new ArrayList<String>();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
                            long start = System.currentTimeMillis();

                            for(String roomId : fRoomsToCommitForSummaries) {
                                try {
                                    clearRoomSummaryFiles(roomId);

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
                                    e = e;
                                }
                            }

                            Log.e(LOG_TAG, "saveSummaries : " + fRoomsToCommitForSummaries.size() + " summaries in " + (System.currentTimeMillis() - start) + " ms");
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    private boolean loadSummary(final String roomId) {
        Boolean succeed = true;
        Room room = getRoom(roomId);

        // should always be true
        if (null != room) {
            RoomSummary summary = null;

            try {
                File messagesListFile = new File(mStoreRoomsSummaryFolderFile, roomId);

                FileInputStream fis = new FileInputStream(messagesListFile);
                ObjectInputStream ois = new ObjectInputStream(fis);

                summary = (RoomSummary) ois.readObject();
                ois.close();
            } catch (Exception e){
                succeed = false;
                Log.e(LOG_TAG, "loadSummary : " + e.getMessage());
            }

            if (null != summary) {
                summary.getLatestEvent().finalizeDeserialization();
                mRoomSummaries.put(roomId, summary);
            } else {
                deleteRoom(roomId);
            }
        }

        return succeed;
    }
    /**
     * Load room summaries
     */
    private Boolean loadSummaries() {
        Boolean succeed = true;
        try {
            // extract the room states
            String[] filenames = mStoreRoomsSummaryFolderFile.list();

            long start = System.currentTimeMillis();

            for(int index = 0; index < filenames.length; index++) {
                succeed &= loadSummary(filenames[index]);
            }

            Log.e(LOG_TAG, "loadSummaries " + filenames.length + " rooms in " + (System.currentTimeMillis() - start) + " ms");

        }
        catch (Exception e) {
        }

        return succeed;
    }

    private void loadMetaData() {
        if (false) {
            return;
        }

        long start = System.currentTimeMillis();

        try {
            File metaDataFile = new File(mStoreFolderFile, MXFILE_STORE_METADATA_FILE_NAME);

            if (metaDataFile.exists()) {
                FileInputStream fis = new FileInputStream(metaDataFile);
                ObjectInputStream out = new ObjectInputStream(fis);

                mMetadata = (MXFileStoreMetaData)out.readObject();
                out.close();

                // extract the latest event stream token
                mEventStreamToken = mMetadata.mEventStreamToken;
            }

        } catch (Exception e) {
            mMetadata = null;
        }

        Log.e(LOG_TAG, "loadMetaData : " + (System.currentTimeMillis() - start) + " ms");
    }

    private void saveMetaData() {
        if (mMetaDataHasChanged) {
            mMetaDataHasChanged = false;

            final MXFileStoreMetaData fMetadata = mMetadata.deepCopy();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mFileStoreHandler.post(new Runnable() {
                        public void run() {
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
                            }

                            Log.e(LOG_TAG, "saveMetaData : " + (System.currentTimeMillis() - start) + " ms");
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }
}
