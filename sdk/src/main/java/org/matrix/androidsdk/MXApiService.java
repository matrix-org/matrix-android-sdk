package org.matrix.androidsdk;

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.OkHttpClient;

import org.matrix.androidsdk.api.EventsApi;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit.Callback;
import retrofit.ErrorHandler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;

public class MXApiService {

    private static final String LOG_TAG = "MXApiService";

    private static final String URI_PREFIX = "/_matrix/client/api/v1";
    private static final String PARAM_ACCESS_TOKEN = "access_token";

    private EventsApi mEventsApi;
    private Gson mGson;
    private String mAccessToken;

    public MXApiService(String hsDomain) {
        // The JSON -> object mapper
        mGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        // HTTP client
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(60000, TimeUnit.MILLISECONDS);
        okHttpClient.setReadTimeout(60000, TimeUnit.MILLISECONDS);

        // Rest adapter for turning API interfaces into actual REST-calling objects
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("http://" + hsDomain + URI_PREFIX)
                .setConverter(new GsonConverter(mGson))
                .setClient(new OkClient(okHttpClient))
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestInterceptor.RequestFacade request) {
                        if (mAccessToken != null) {
                            request.addEncodedQueryParam(PARAM_ACCESS_TOKEN, mAccessToken);
                        }
                    }
                })
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError cause) {
                        if (cause.isNetworkError()) {
                            Log.e(LOG_TAG, cause.getMessage());
                            return null;
                        }
                        return cause;
                    }
                })
                .build();

        restAdapter.setLogLevel(RestAdapter.LogLevel.BASIC);

        mEventsApi = restAdapter.create(EventsApi.class);
    }

    public void setAccessToken(String accessToken) {
        mAccessToken = accessToken;
    }

    public interface LoadPublicRoomsCallback {
        public void onRoomsLoaded(List<PublicRoom> publicRooms);
    }

    public void loadPublicRooms(final LoadPublicRoomsCallback callback) {
        mEventsApi.publicRooms(new Callback<TokensChunkResponse<PublicRoom>>() {
            @Override
            public void success(TokensChunkResponse<PublicRoom> typedResponse, Response response) {
                callback.onRoomsLoaded(typedResponse.chunk);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(LOG_TAG, "REST error: " + error.getMessage());
            }
        });
    }

    public InitialSyncResponse initialSync() {
        return mEventsApi.initialSync(1);
    }

    public TokensChunkResponse<Event> events(String fromToken) {
        return mEventsApi.events(fromToken, 30000);
    }
}
