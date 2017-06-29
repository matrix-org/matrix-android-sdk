package org.matrix.androidsdk.rest.client;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.matrix.androidsdk.rest.model.PublicRoomsFilter;
import org.matrix.androidsdk.rest.model.PublicRoomsParams;
import org.matrix.androidsdk.rest.model.PublicRoomsResponse;
import org.robolectric.RobolectricTestRunner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.api.EventsApi;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.test.JSONUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests RestClient.
 */
@RunWith(RobolectricTestRunner.class)
public class RestClientTest {

    private static final String BASE_URL = "http://localhost:8008/_matrix/client/api/v1";
    private static final String PATH = "/publicRooms";

    /**
     * Tests: MXApiService.loadPublicRooms(LoadPublicRoomsCallback)
     * Summary: Mocks up a single public room in the response and asserts that the callback contains
     * the mocked information.
     */
    @Test
    public void testPublicRooms() throws Exception {
        /*final String roomId = "!faifuhew9:localhost";
        final String roomTopic = "This is a test room.";
        final String roomName = "Test Room";
        final int roomMembers = 6;

        JSONArray rooms = new JSONArray();

        final JSONObject json = new JSONObject();
        json.put("chunk", rooms);
        json.put("next_batch", "123");

        JSONObject room = new JSONObject().put("name", roomName)
            .put("num_joined_members", roomMembers).put("room_id", roomId).put("topic", roomTopic);

        rooms.put(room);

        final PublicRoomsResponse publicRoomsResponse = mGson.fromJson(json.toString(),
                new TypeToken<PublicRoomsResponse>(){}.getType());

        EventsApi eventsApi = mock(EventsApi.class);

        PublicRoomsParams publicRoomsParams = new PublicRoomsParams();
        publicRoomsParams.server = "dummyServer";
        publicRoomsParams.limit = 10;
        publicRoomsParams.since = null;
        publicRoomsParams.filter = null;

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Callback<PublicRoomsResponse> callback = (Callback<PublicRoomsResponse>)invocation.getArguments()[0];
                Response response = null;
                try {
                    response = RetrofitUtils.createJsonResponse(BASE_URL + PATH, 200, json);
                }
                catch (Exception e) {
                    assertTrue("Exception thrown: "+e, false);
                }
                callback.success(publicRoomsResponse, response);
                return null;
            }
        }).when(eventsApi).publicRooms(publicRoomsParams, any(Callback.class));

        EventsRestClient client = new EventsRestClient(eventsApi);
        ApiCallback<PublicRoomsResponse> cb = mock(ApiCallback.class);

        // run the method being tested
        client.loadPublicRooms("dummyServer", null, null, 10, cb);

        ArgumentCaptor<PublicRoomsResponse> captor = ArgumentCaptor.forClass(PublicRoomsResponse.class);
        verify(cb, times(1)).onSuccess(captor.capture());
        List<PublicRoom> publicRooms = (captor.getValue()).chunk;

        assertEquals(1, publicRooms.size());
        PublicRoom pr = publicRooms.get(0);
        assertEquals(roomName, pr.name);
        assertEquals(roomId, pr.roomId);
        assertEquals(roomTopic, pr.topic);
        assertEquals(roomMembers, pr.numJoinedMembers);*/

    }

    /**
     * Tests: MXApiService.loadPublicRooms(LoadPublicRoomsCallback)
     * Summary: Fails the public rooms HTTP call.
     */
    @Test
    public void testPublicRoomsError() throws Exception {
        /*EventsApi eventsApi = mock(EventsApi.class);

        PublicRoomsParams publicRoomsParams = new PublicRoomsParams();
        publicRoomsParams.server = "dummyServer";
        publicRoomsParams.limit = 10;
        publicRoomsParams.since = null;
        publicRoomsParams.filter = null;

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Callback<PublicRoomsResponse> callback =
                        (Callback<PublicRoomsResponse>) invocation.getArguments()[0];

                callback.failure(RetrofitUtils.createMatrixError(BASE_URL + PATH,
                        JSONUtils.error(500)));
                return null;
            }
        }).when(eventsApi).publicRooms(publicRoomsParams, any(Callback.class));

        EventsRestClient client = new EventsRestClient(eventsApi);
        ApiCallback<PublicRoomsResponse> cb = mock(ApiCallback.class);

        // run the method being tested
        client.loadPublicRooms("dummyServer", null, null, 10, cb);
        verify(cb, times(0)).onSuccess(any(PublicRoomsResponse.class));*/
    }
}
