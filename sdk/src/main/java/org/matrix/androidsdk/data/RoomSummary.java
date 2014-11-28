package org.matrix.androidsdk.data;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.Collection;

/**
 * Stores summarised information about the room.
 */
public class RoomSummary {

    private String mRoomId;
    private String mName;
    private String mTopic;
    private int mNumMembers;
    private Event mLatestEvent;
    private RoomState mLatestRoomState;
    private Collection<RoomMember> mMembers;
    private String mInviter; // only populated if you've been invited.

    public RoomSummary() {}

    public RoomSummary(String roomId, String name, String topic, Event msg,
                       Collection<RoomMember> members) {
        mLatestEvent = msg;
        mRoomId = roomId;
        mName = name;
        mTopic = topic;
        mMembers = members;
    }

    public String getRoomId() {
        return mRoomId;
    }

    public String getRoomName() {
        return mName;
    }

    public String getRoomTopic() {
        return mTopic;
    }

    public int getNumMembers() {
        return mNumMembers;
    }

    public Event getLatestEvent() {
        return mLatestEvent;
    }

    public RoomState getLatestRoomState() {
        return mLatestRoomState;
    }

    public boolean isInvited() {
        return mInviter != null;
    }

    public String getInviterUserId() {
        return mInviter;
    }

    public Collection<RoomMember> getMembers() {
        return mMembers;
    }

    /**
     * Set the room's members. This can be a subset of members
     * (see {@link RoomSummary#setNumMembers(int)} for a total count).
     * @param members A list of members.
     * @return This summary for chaining calls.
     */
    public RoomSummary setMembers(Collection<RoomMember> members) {
        mMembers = members;
        mNumMembers = mMembers.size();
        return this;
    }

    /**
     * Set the number of members in this room.
     * @param numMembers The number of members
     * @return This summary for chaining calls.
     */
    public RoomSummary setNumMembers(int numMembers) {
        mNumMembers = numMembers;
        return this;
    }

    /**
     * Set the room's {@link org.matrix.androidsdk.rest.model.Event#EVENT_TYPE_STATE_ROOM_TOPIC}.
     * @param topic The topic
     * @return This summary for chaining calls.
     */
    public RoomSummary setTopic(String topic) {
        mTopic = topic;
        return this;
    }

    /**
     * Set the room's {@link org.matrix.androidsdk.rest.model.Event#EVENT_TYPE_STATE_ROOM_NAME}.
     * @param name The name
     * @return This summary for chaining calls.
     */
    public RoomSummary setName(String name) {
        mName = name;
        return this;
    }

    /**
     * Set the room's ID..
     * @param roomId The room ID
     * @return This summary for chaining calls.
     */
    public RoomSummary setRoomId(String roomId) {
        mRoomId = roomId;
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
     * @param event The most-recent event.
     * @return This summary for chaining calls.
     */
    public RoomSummary setLatestEvent(Event event) {
        mLatestEvent = event;
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
     * @param roomState The room state of the latest event.
     * @return This summary for chaining calls.
     */
    public RoomSummary setLatestRoomState(RoomState roomState) {
        mLatestRoomState = roomState;
        return this;
    }

    /**
     * Set the user ID of the person who invited the user to this room.
     * @param inviterUserId The user ID of the inviter
     * @return This summary for chaining calls.
     */
    public RoomSummary setInviterUserId(String inviterUserId) {
        mInviter = inviterUserId;
        return this;
    }
}
