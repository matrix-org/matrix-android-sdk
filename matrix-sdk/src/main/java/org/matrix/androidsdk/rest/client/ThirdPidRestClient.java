/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ThirdPidApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.DefaultRetrofit2ResponseHandler;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BulkLookupParams;
import org.matrix.androidsdk.rest.model.BulkLookupResponse;
import org.matrix.androidsdk.rest.model.HttpError;
import org.matrix.androidsdk.rest.model.HttpException;
import org.matrix.androidsdk.rest.model.PidResponse;
import org.matrix.androidsdk.rest.model.RequestEmailValidationResponse;
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ThirdPidRestClient extends RestClient<ThirdPidApi> {

    private static final String KEY_SUBMIT_TOKEN_SUCCESS = "success";

    /**
     * {@inheritDoc}
     */
    public ThirdPidRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, ThirdPidApi.class, URI_API_PREFIX_IDENTITY, false, true);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param address 3rd party id
     * @param medium the media.
     * @param callback the 3rd party callback
     */
    public void lookup3Pid(String address, String medium, final ApiCallback<String> callback) {
        mApi.lookup3Pid(address, medium).enqueue(new Callback<PidResponse>() {
            @Override
            public void onResponse(Call<PidResponse> call, Response<PidResponse> response) {
                try {
                    handleLookup3PidResponse(response, callback);
                } catch (IOException e) {
                    onFailure(call, e);
                }
            }

            @Override public void onFailure(Call<PidResponse> call, Throwable t) {
                callback.onUnexpectedError((Exception) t);
            }
        });
    }

    private void handleLookup3PidResponse(
        Response<PidResponse> response,
        final ApiCallback<String> callback
    ) throws IOException {
        DefaultRetrofit2ResponseHandler.handleResponse(
            response,
            new DefaultRetrofit2ResponseHandler.Listener<PidResponse>() {
                @Override public void onSuccess(Response<PidResponse> response) {
                    PidResponse pidResponse = response.body();
                    callback.onSuccess((null == pidResponse.mxid) ? "" : pidResponse.mxid);
                }

                @Override public void onHttpError(HttpError httpError) {
                    callback.onNetworkError(new HttpException(httpError));
                }
            }
        );
    }

    /**
     * Request an email validation token.
     * @param address the email address
     * @param clientSecret the client secret number
     * @param attempt the attempt count
     * @param nextLink the next link.
     * @param callback the callback.
     */
    public void requestEmailValidationToken(final String address, final String clientSecret, final int attempt,
                                            final String nextLink, final ApiCallback<RequestEmailValidationResponse> callback) {
        final String description = "requestEmailValidationToken";

        mApi.requestEmailValidation(clientSecret, address, attempt, nextLink).enqueue(new RestAdapterCallback<RequestEmailValidationResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        requestEmailValidationToken(address, clientSecret, attempt, nextLink, callback);
                    }
                }
        ) {
            @Override
            public void success(RequestEmailValidationResponse requestEmailValidationResponse, Response response) {
                onEventSent();
                requestEmailValidationResponse.email = address;
                requestEmailValidationResponse.clientSecret = clientSecret;
                requestEmailValidationResponse.sendAttempt = attempt;

                callback.onSuccess(requestEmailValidationResponse);
            }
        });
    }

    /**
     * Request a phone number validation token.
     * @param phoneNumber the phone number
     * @param countryCode the country code of the phone number
     * @param clientSecret the client secret number
     * @param attempt the attempt count
     * @param nextLink the next link.
     * @param callback the callback.
     */
    public void requestPhoneNumberValidationToken(final String phoneNumber, final String countryCode,
                                                  final String clientSecret, final int attempt, final String nextLink,
                                                  final ApiCallback<RequestPhoneNumberValidationResponse> callback) {
        final String description = "requestPhoneNUmberValidationToken";

        mApi.requestPhoneNumberValidation(clientSecret, phoneNumber, countryCode, attempt, nextLink).enqueue(new RestAdapterCallback<RequestPhoneNumberValidationResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        requestPhoneNumberValidationToken(phoneNumber, countryCode, clientSecret, attempt, nextLink, callback);
                    }
                }
        ) {
            @Override
            public void success(RequestPhoneNumberValidationResponse requestPhoneNumberValidationResponse, Response response) {
                onEventSent();
                requestPhoneNumberValidationResponse.clientSecret = clientSecret;
                requestPhoneNumberValidationResponse.sendAttempt = attempt;

                callback.onSuccess(requestPhoneNumberValidationResponse);
            }
        });
    }

    /**
     * Request the ownership validation of an email address or a phone number previously set
     * by {@link #requestEmailValidationToken(String, String, int, String, ApiCallback)}.
     * @param medium the medium of the 3pid
     * @param token the token generated by the requestEmailValidationToken call
     * @param clientSecret the client secret which was supplied in the requestEmailValidationToken call
     * @param sid the sid for the session
     * @param callback asynchronous callback response
     */
    public void submitValidationToken(final String medium, final String token, final String clientSecret, final String sid, final ApiCallback<Boolean> callback) {
        mApi.requestOwnershipValidation(medium, token, clientSecret, sid).enqueue(new Callback<Map<String,Object>> () {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                try {
                    handleSubmitValidationTokenResponse(response, callback);
                } catch (IOException e) {
                    callback.onUnexpectedError(e);
                }
            }

            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onUnexpectedError((Exception) t);
            }
        });
    }

    private void handleSubmitValidationTokenResponse(
        Response<Map<String, Object>> response,
        final ApiCallback<Boolean> callback
    ) throws IOException {
        DefaultRetrofit2ResponseHandler.handleResponse(
            response,
            new DefaultRetrofit2ResponseHandler.Listener<Map<String, Object>>() {
                @Override public void onSuccess(Response<Map<String, Object>> response) {
                    Map<String, Object> aDataRespMap = response.body();
                    if (aDataRespMap.containsKey(KEY_SUBMIT_TOKEN_SUCCESS)) {
                        callback.onSuccess((Boolean) aDataRespMap.get(KEY_SUBMIT_TOKEN_SUCCESS));
                    } else {
                        callback.onSuccess(false);
                    }
                }

                @Override public void onHttpError(HttpError httpError) {
                    callback.onNetworkError(new HttpException(httpError));
                }
            }
        );
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param addresses 3rd party ids
     * @param mediums the medias.
     * @param callback the 3rd parties callback
     */
    public void lookup3Pids(final List<String> addresses, final List<String> mediums, final ApiCallback<List<String>> callback) {
        // sanity checks
        if ((null == addresses) || (null == mediums) || (addresses.size() != mediums.size())) {
            callback.onUnexpectedError(new Exception("invalid params"));
            return;
        }

        // nothing to check
        if (0 == mediums.size()) {
            callback.onSuccess(new ArrayList<String>());
            return;
        }

        BulkLookupParams threePidsParams = new BulkLookupParams();

        ArrayList<List<String>> list = new ArrayList<>();

        for(int i = 0; i < addresses.size(); i++) {
            list.add(Arrays.asList(mediums.get(i), addresses.get(i)));
        }

        threePidsParams.threepids = list;

        mApi.bulkLookup(threePidsParams).enqueue(new Callback<BulkLookupResponse>() {
            @Override
            public void onResponse(Call<BulkLookupResponse> call, Response<BulkLookupResponse> response) {
                try {
                    handleBulkLookupResponse(response, addresses, callback);
                } catch (IOException e) {
                    callback.onUnexpectedError(e);
                }
            }

            @Override public void onFailure(Call<BulkLookupResponse> call, Throwable t) {
                callback.onUnexpectedError((Exception) t);
            }
        });
    }

    private void handleBulkLookupResponse(
        Response<BulkLookupResponse> response,
        final List<String> addresses,
        final ApiCallback<List<String>> callback
    ) throws IOException {
        DefaultRetrofit2ResponseHandler.handleResponse(
            response,
            new DefaultRetrofit2ResponseHandler.Listener<BulkLookupResponse>() {
                @Override public void onSuccess(Response<BulkLookupResponse> response) {
                    handleBulkLookupSuccess(response, addresses, callback);
                }

                @Override public void onHttpError(HttpError httpError) {
                    callback.onNetworkError(new HttpException(httpError));
                }
            }
        );
    }

    private void handleBulkLookupSuccess(
        Response<BulkLookupResponse> response,
        List<String> addresses,
        ApiCallback<List<String>> callback
    ) {
        BulkLookupResponse bulkLookupResponse = response.body();
        HashMap<String, String> mxidByAddress = new HashMap<>();

        if (null != bulkLookupResponse.threepids) {
            for (int i = 0; i < bulkLookupResponse.threepids.size(); i++) {
                List<String> items = bulkLookupResponse.threepids.get(i);
                // [0] : medium
                // [1] : address
                // [2] : matrix id
                mxidByAddress.put(items.get(1), items.get(2));
            }
        }

        ArrayList<String> matrixIds = new ArrayList<>();

        for(String address : addresses) {
            if (mxidByAddress.containsKey(address)) {
                matrixIds.add(mxidByAddress.get(address));
            } else {
                matrixIds.add("");
            }
        }

        callback.onSuccess(matrixIds);
    }
}
