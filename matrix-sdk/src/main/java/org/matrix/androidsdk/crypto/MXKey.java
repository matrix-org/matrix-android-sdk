/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto;

import android.text.TextUtils;

import java.io.Serializable;

public class MXKey implements Serializable {
    public static final String LOG_TAG = "MXKey";

    /**
     * Key types.
     */
    public static final String KEY_CURVE_25519_TYPE = "curve25519";
    public static final String KEY_ED_25519_TYPE = "ed25519";

    /**
     * The type of the key.
     */
    public String type;

    /**
     * The id of the key.
     */
    public String keyId;

    /**
     * The key.
     */
    public String value;


    /**
     * @return the key full id
     */
    public String getKeyFullId() {
        return type + ":" + keyId;
    }

    /**
     * Update the key fields with a key full id
     * @param keyFullId the key full id
     */
    public void setKeyFullId(String keyFullId) {

        if (!TextUtils.isEmpty(keyFullId)) {
            String[] components = keyFullId.split(":");

            if ((null != components) && (components.length == 2)) {
                type = components[0];
                keyId = components[1];
            }
        }
    }
}