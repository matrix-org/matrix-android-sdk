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

import android.os.Build;
import android.support.annotation.NonNull;

import org.matrix.androidsdk.rest.client.LoginRestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Object to pass to a /login call of type password.
 */
public class PasswordLoginParams extends LoginParams {
    public static String IDENTIFIER_KEY_TYPE_USER = "m.id.user";
    public static String IDENTIFIER_KEY_TYPE_THIRD_PARTY = "m.id.thirdparty";
    public static String IDENTIFIER_KEY_TYPE_PHONE = "m.id.phone";

    public static String IDENTIFIER_KEY_TYPE = "type";
    public static String IDENTIFIER_KEY_MEDIUM = "medium";
    public static String IDENTIFIER_KEY_ADDRESS = "address";
    public static String IDENTIFIER_KEY_USER = "user";
    public static String IDENTIFIER_KEY_COUNTRY = "country";
    public static String IDENTIFIER_KEY_NUMBER = "number";

    // identifier parameters
    private Map<String, Object> identifier;

    // user name login
    private String user;

    // email login
    private String address;
    private String medium;

    // common
    public String password;

    // A display name to assign to the newly-created device
    public String initial_device_display_name;

    public void setUserIdentifier(@NonNull final String username, @NonNull final String password) {
        identifier = new HashMap<>();
        identifier.put(IDENTIFIER_KEY_TYPE, IDENTIFIER_KEY_TYPE_USER);
        identifier.put(IDENTIFIER_KEY_USER, username);
        // For backward compatibility
        this.user = username;

        setOtherData(password);
    }

    public void setThirdPartyIentifier(@NonNull final String medium, @NonNull final String address, @NonNull final String password) {
        identifier = new HashMap<>();
        identifier.put(IDENTIFIER_KEY_TYPE, IDENTIFIER_KEY_TYPE_THIRD_PARTY);
        identifier.put(IDENTIFIER_KEY_MEDIUM, medium);
        identifier.put(IDENTIFIER_KEY_ADDRESS, address);
        // For backward compatibility
        this.medium = medium;
        this.address = address;

        setOtherData(password);
    }

    public void setPhoneIentifier(@NonNull final String phoneNumber, @NonNull final String countryCode, @NonNull final String password) {
        identifier = new HashMap<>();
        identifier.put(IDENTIFIER_KEY_TYPE, IDENTIFIER_KEY_TYPE_PHONE);
        identifier.put(IDENTIFIER_KEY_NUMBER, phoneNumber);
        identifier.put(IDENTIFIER_KEY_COUNTRY, countryCode);

        setOtherData(password);
    }

    private void setOtherData(@NonNull final String password) {
        this.password = password;
        this.type = LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD;
        this.initial_device_display_name = Build.MODEL.trim();
    }
}
