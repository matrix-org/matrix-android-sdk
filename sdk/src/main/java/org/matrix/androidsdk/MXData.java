package org.matrix.androidsdk;

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.RoomMember;
import org.matrix.androidsdk.api.response.User;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All cached data.
 */
public class MXData {
    private static final String LOG_TAG = "MXData";

    private Map<String, Room> mRooms = new ConcurrentHashMap<String, Room>();
    private Map<String, User> mUsers = new ConcurrentHashMap<String, User>();

    /**
     * Callback to implement when data is updated
     */
    public interface DataUpdateListener {
        public void onUpdate();
    }

    /**
     * Callback to implement to receive all events
     */
    public interface OnEventListener {
        public void onEvent(Event event);
    }

    // Data update listeners
    private List<DataUpdateListener> mUserDataListeners = new ArrayList<DataUpdateListener>();
    private List<DataUpdateListener> mGlobalRoomDataListeners = new ArrayList<DataUpdateListener>();
    private Map<String, List<DataUpdateListener>> mRoomDataListeners = new HashMap<String, List<DataUpdateListener>>();

    private List<OnEventListener> mOnEventListeners = new ArrayList<OnEventListener>();

    private Gson mGson;

    public MXData() {
        // The JSON -> object mapper
        mGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    public Collection<Room> getRooms() {
        return mRooms.values();
    }

    public Room getRoom(String roomId) {
        return mRooms.get(roomId);
    }

    public Collection<User> getUsers() {
        return mUsers.values();
    }

    public User getUser(String userId) {
        return mUsers.get(userId);
    }

    public void addUserDataListener(DataUpdateListener listener) {
        mUserDataListeners.add(listener);
    }

    public void removeUserDataListener(DataUpdateListener listener) {
        mUserDataListeners.remove(listener);
    }

    public void addGlobalRoomDataListener(DataUpdateListener listener) {
        mGlobalRoomDataListeners.add(listener);
    }

    public void removeGlobalRoomDataListener(DataUpdateListener listener) {
        mGlobalRoomDataListeners.remove(listener);
    }

    public void addRoomDataListener(String roomId, DataUpdateListener listener) {
        List<DataUpdateListener> listeners = mRoomDataListeners.get(roomId);
        if (listeners == null) {
            listeners = new ArrayList<DataUpdateListener>();
            mRoomDataListeners.put(roomId, listeners);
        }
        listeners.add(listener);
    }

    public void removeRoomDataListener(String roomId, DataUpdateListener listener) {
        List<DataUpdateListener> listeners = mRoomDataListeners.get(roomId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(List<DataUpdateListener> listeners) {
        if (listeners != null) {
            for (DataUpdateListener listener : listeners) {
                listener.onUpdate();
            }
        }
    }

    public void addOnEventListener(OnEventListener listener) {
        mOnEventListeners.add(listener);
    }

    public void removeOnEventListener(OnEventListener listener) {
        mOnEventListeners.remove(listener);
    }

    private void notifyOnEventListeners(Event event) {
        for (OnEventListener listener : mOnEventListeners) {
            listener.onEvent(event);
        }
    }

    public Room addRoom(String roomId) {
        Room room = new Room();
        room.setRoomId(roomId);
        mRooms.put(roomId, room);

        notifyListeners(mGlobalRoomDataListeners);

        return room;
    }

    public void handleEvents(List<? extends Event> events) {
        for (Event event : events) {
            handleEvent(event);
        }
    }

    private void handleEvent(Event event) {
        Log.d(LOG_TAG, "Handling event " + event);

        if ("m.presence".equals(event.type)) {
            User userPresence = mGson.fromJson(event.content, User.class);
            User user = mUsers.get(userPresence.userId);
            if (user == null) {
                user = userPresence;
                mUsers.put(user.userId, user);
            }
            else {
                user.presence = userPresence.presence;
                user.lastActiveAgo = userPresence.lastActiveAgo;
            }

            notifyListeners(mUserDataListeners);
        }

        // Room events
        else if ("m.room.message".equals(event.type)) {
            Room room = mRooms.get(event.roomId);
            if (room != null) {
                room.addMessage(event);
            }

            notifyListeners(mGlobalRoomDataListeners);
            notifyListeners(mRoomDataListeners.get(event.roomId));
        }

        // Room state events
        else if ("m.room.name".equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = getOrCreateRoom(event.roomId);
            room.getRoomState().name = roomState.name;
        }

        else if ("m.room.topic".equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = getOrCreateRoom(event.roomId);
            room.getRoomState().topic = roomState.topic;
        }

        else if ("m.room.create".equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = getOrCreateRoom(event.roomId);
            room.getRoomState().creator = roomState.creator;
        }

        else if ("m.room.join_rules".equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = getOrCreateRoom(event.roomId);
            room.getRoomState().joinRule = roomState.joinRule;
        }

        else if ("m.room.aliases".equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = getOrCreateRoom(event.roomId);
            room.getRoomState().aliases = roomState.aliases;
        }

        else if ("m.room.member".equals(event.type)) {
            RoomMember member = mGson.fromJson(event.content, RoomMember.class);
            Room room = getOrCreateRoom(event.roomId);
            room.setMember(event.userId, member);
        }

        notifyOnEventListeners(event);
    }

    private Room getOrCreateRoom(String roomId) {
        Room room = mRooms.get(roomId);
        if (room == null) {
            room = addRoom(roomId);
        }
        return room;
    }
}
