package org.matrix.androidsdk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.json.JSONObject;
import org.matrix.androidsdk.api.EventsApi;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.test.RetrofitUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.client.Response;
import retrofit.mime.TypedInput;


public class MXApiServiceTest extends TestCase {

    private static final String BASE_URL = "http://localhost:8008/_matrix/client/api/v1";

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testPublicRooms() {
        Gson gson = new Gson();
        final TokensChunkResponse<PublicRoom> roomsChunk = new TokensChunkResponse<PublicRoom>();
        roomsChunk.chunk = new ArrayList<PublicRoom>();

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
                try {
                    Response response = RetrofitUtils.createJsonResponse(BASE_URL, 200,
                            new JSONObject());
                    callback.success(roomsChunk, response);
                }
                catch (Exception e) {
                    Assert.assertTrue("Exception thrown: "+e, false);
                }

            }

            @Override
            public InitialSyncResponse initialSync(@Query("limit") int limit) {
                return null;
            }
        };


        MXApiService service = new MXApiService(eventsApi);
        service.loadPublicRooms(new MXApiService.LoadPublicRoomsCallback() {
            @Override
            public void onRoomsLoaded(List<PublicRoom> publicRooms) {
                Assert.assertEquals(0, publicRooms.size());
            }
        });

    }
}
