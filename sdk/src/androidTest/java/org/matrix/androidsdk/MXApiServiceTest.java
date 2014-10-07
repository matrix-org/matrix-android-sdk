package org.matrix.androidsdk;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.matrix.androidsdk.api.EventsApi;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.test.RetrofitUtils;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

import static org.mockito.Mockito.*;

public class MXApiServiceTest extends TestCase {

    private static final String BASE_URL = "http://localhost:8008/_matrix/client/api/v1";

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testPublicRooms() throws Exception {
        final String roomId = "!faifuhew9:localhost";
        final String roomTopic = "This is a test room.";
        final String roomName = "Test Room";
        final int roomMembers = 6;

        final JSONObject json = new JSONObject();
        json.put("start","abc");
        json.put("end", "def");
        JSONArray rooms = new JSONArray();

        JSONObject room = new JSONObject().put("name", roomName).put("num_joined_members", roomMembers)
            .put("room_id", roomId).put("topic", roomTopic);
        rooms.put(room);
        json.put("chunk", rooms);

        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        final TokensChunkResponse<PublicRoom> roomsChunk = gson.fromJson(json.toString(),
                new TypeToken<TokensChunkResponse<PublicRoom>>(){}.getType());

        EventsApi eventsApi = new EventsApi() {

            @Override
            public TokensChunkResponse<Event> events(@Query("from") String from,
                                                     @Query("timeout") int timeout) {
                return null;
            }

            @Override
            public JsonObject events(@Path("eventId") String eventId) {
                return null;
            }

            @Override
            public void publicRooms(Callback<TokensChunkResponse<PublicRoom>> callback) {
                Response response = null;
                try {
                    response = RetrofitUtils.createJsonResponse(BASE_URL, 200,
                                json);
                }
                catch (Exception e) {
                    assertTrue("Exception thrown: "+e, false);
                }
                callback.success(roomsChunk, response);
            }

            @Override
            public InitialSyncResponse initialSync(@Query("limit") int limit) {
                return null;
            }
        };


        MXApiService service = new MXApiService(eventsApi);
        MXApiService.LoadPublicRoomsCallback cb = mock(MXApiService.LoadPublicRoomsCallback.class);

        // run the method being tested
        service.loadPublicRooms(cb);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(cb, times(1)).onRoomsLoaded(captor.capture());
        List<PublicRoom> publicRooms = (List<PublicRoom>) captor.getValue();

        assertEquals(1, publicRooms.size());
        PublicRoom pr = publicRooms.get(0);
        assertEquals(roomName, pr.name);
        assertEquals(roomId, pr.roomId);
        assertEquals(roomTopic, pr.topic);
        assertEquals(roomMembers, pr.numJoinedMembers);

    }
}
