/* 
 * Copyright 2014 OpenMarket Ltd
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

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ThirdPidApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.PidResponse;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.ArrayList;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class ThirdPidRestClient extends RestClient<ThirdPidApi> {

    /**
     * {@inheritDoc}
     */
    public ThirdPidRestClient(Credentials credentials) {
        super(credentials, ThirdPidApi.class, RestClient.URI_IDENTITY_PREFIX);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param address 3rd party id
     * @param medium the media.
     * @param callback the 3rd party callback
     */
    public void lookup3Pid(String address, String medium, final ApiCallback<String> callback) {
        mApi.lookup3Pid(address, medium, new Callback<PidResponse>() {
            @Override
            public void success(PidResponse pidResponse, Response response) {
                callback.onSuccess((null == pidResponse.mxid) ? "" : pidResponse.mxid);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param addresses 3rd party ids
     * @param mediums the medias.
     * @param callback the 3rd parties callback
     */
    public void lookup3Pids(ArrayList<String> addresses, ArrayList<String> mediums, ApiCallback<ArrayList<String>> callback) {
        // check if the sizes match
        if ((addresses.size() == mediums.size()) && (addresses.size() != 0)) {
            lookup3Pids(addresses, mediums, 0, new ArrayList<String>(), callback);
        }
    }

    // recursive method to get the mids
    private void lookup3Pids(final ArrayList<String> addresses, final ArrayList<String> mediums, final int index, final ArrayList<String> mids,  final ApiCallback<ArrayList<String>> callback) {
        if (index >= addresses.size()) {
            callback.onSuccess(mids);
        }

        mApi.lookup3Pid(addresses.get(index), mediums.get(index), new Callback<PidResponse>() {
            @Override
            public void success(PidResponse pidResponse, Response response) {

                mids.add((null == pidResponse.mxid) ? "" : pidResponse.mxid);

                if ((index+1) == addresses.size()) {
                    callback.onSuccess(mids);
                } else {
                    lookup3Pids(addresses, mediums, index + 1, mids, callback);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }
}
