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
package org.matrix.androidsdk.rest.model.login;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The user's credentials.
 */
public class Credentials {
    public String userId;
    public String homeServer;
    public String accessToken;

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("user_id", userId);
        json.put("home_server", homeServer);
        json.put("access_token", accessToken);

        return json;
    }

    public static Credentials fromJson(JSONObject obj) throws JSONException {
        Credentials creds = new Credentials();
        creds.userId = obj.getString("user_id");
        creds.homeServer = obj.getString("home_server");
        creds.accessToken = obj.getString("access_token");
        return creds;
    }
}
