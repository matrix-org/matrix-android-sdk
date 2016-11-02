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

package org.matrix.androidsdk.crypto.data;

import android.text.TextUtils;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MXKey implements Serializable {
    private static final String LOG_TAG = "MXKey";
    /**
     * Key types.
     */
    public static final String KEY_CURVE_25519_TYPE = "curve25519";
    //public static final String KEY_ED_25519_TYPE = "ed25519";

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
     * Default constructor
     */
    public MXKey() {
    }

    /**
     * Convert a map to a MXKey
     * @param map the map to convert
     */
    public MXKey(Map<String, String> map) {
        if ((null != map) && (map.size() > 0)) {
            List<String> mapKeys = new ArrayList<>(map.keySet());
            setKeyFullId(mapKeys.get(0));
            value = map.get(mapKeys.get(0));
        }
    }

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
    private void setKeyFullId(String keyFullId) {
        if (!TextUtils.isEmpty(keyFullId)) {
            try {
                String[] components = keyFullId.split(":");

                if (components.length == 2) {
                    type = components[0];
                    keyId = components[1];
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## setKeyFullId() failed : " + e.getMessage());
            }
        }
    }
}