package org.matrix.androidsdk.rest.client;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.matrix.androidsdk.api.EventsApi;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.test.JSONUtils;
import org.matrix.androidsdk.test.RetrofitUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;

import static org.mockito.Mockito.*;

/**
 * Unit tests MXApiService.
 */
public class MXApiClientTest extends TestCase {

    private static final String BASE_URL = "http://localhost:8008/_matrix/client/api/v1";
    private static final String PATH = "/publicRooms";

    private Gson mGson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests: MXApiService.loadPublicRooms(LoadPublicRoomsCallback)
     * Summary: Mocks up a single public room in the response and asserts that the callback contains
     * the mocked information.
     */
    public void testPublicRooms() throws Exception {
        final String roomId = "!faifuhew9:localhost";
        final String roomTopic = "This is a test room.";
        final String roomName = "Test Room";
        final int roomMembers = 6;

        JSONArray rooms = new JSONArray();
        final JSONObject json = JSONUtils.createChunk(rooms);

        JSONObject room = new JSONObject().put("name", roomName)
            .put("num_joined_members", roomMembers).put("room_id", roomId).put("topic", roomTopic);
        rooms.put(room);


        final TokensChunkResponse<PublicRoom> roomsChunk = mGson.fromJson(json.toString(),
                new TypeToken<TokensChunkResponse<PublicRoom>>(){}.getType());

        EventsApi eventsApi = mock(EventsApi.class);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Callback<TokensChunkResponse<PublicRoom>> callback =
                        (Callback<TokensChunkResponse<PublicRoom>>)invocation.getArguments()[0];
                Response response = null;
                try {
                    response = RetrofitUtils.createJsonResponse(BASE_URL + PATH, 200,
                            json);
                }
                catch (Exception e) {
                    assertTrue("Exception thrown: "+e, false);
                }
                callback.success(roomsChunk, response);
                return null;
            }
        }).when(eventsApi).publicRooms(any(Callback.class));


        EventsApiClient client = new EventsApiClient(eventsApi);
        EventsApiClient.LoadPublicRoomsCallback cb = mock(EventsApiClient.LoadPublicRoomsCallback.class);

        // run the method being tested
        client.loadPublicRooms(cb);

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

    /**
     * Tests: MXApiService.loadPublicRooms(LoadPublicRoomsCallback)
     * Summary: Fails the public rooms HTTP call.
     */
    public void testPublicRoomsError() throws Exception {
        EventsApi eventsApi = mock(EventsApi.class);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Callback<TokensChunkResponse<PublicRoom>> callback =
                        (Callback<TokensChunkResponse<PublicRoom>>) invocation.getArguments()[0];

                callback.failure(RetrofitUtils.createMatrixError(BASE_URL + PATH,
                        JSONUtils.error(500)));
                return null;
            }
        }).when(eventsApi).publicRooms(any(Callback.class));

        EventsApiClient client = new EventsApiClient(eventsApi);
        EventsApiClient.LoadPublicRoomsCallback cb = mock(EventsApiClient.LoadPublicRoomsCallback.class);

        // run the method being tested
        client.loadPublicRooms(cb);
        verify(cb, times(0)).onRoomsLoaded(any(List.class));
    }
}
