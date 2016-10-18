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

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MXUsersDevicesMap<E> implements Serializable {
    //
    private static final String LOG_TAG = "MXUsersDevicesMap";

    // The device keys as returned by the homeserver: a map of a map (userId -> deviceId -> Object).
    private final HashMap<String, HashMap<String, E>> mMap;

    /**
     * @return the inner map
     */
    public HashMap<String, HashMap<String, E>> getMap() {
        return mMap;
    }

    /**
     * The constructor
     * @param map the map
     */
    public MXUsersDevicesMap(Map<String, Map<String, E>> map) {
        mMap = new HashMap<>();

        if (null != map) {
            Set<String> keys = map.keySet();

            for(String key : keys) {
                mMap.put(key, new HashMap<>(map.get(key)));
            }
        }
    }

    /**
     * @return a deep copy
     */
    public MXUsersDevicesMap<E> deepCopy() {
        MXUsersDevicesMap<E> copy = new MXUsersDevicesMap<>(null);

        Set<String> keys = mMap.keySet();

        for(String key : keys) {
            copy.mMap.put(key, new HashMap<>(mMap.get(key)));
        }

        return copy;
    }

    /**
     * @return the user Ids
     */
    public Set<String> userIds() {
        if (null != mMap) {
            return mMap.keySet();
        }

        return null;
    }

    /**
     * Provides the device ids list for an user id
     * @param userId the user id
     * @return the device ids list
     */
    public Set<String> deviceIdsForUser(String userId) {
        if ((null != mMap) && !TextUtils.isEmpty(userId) && mMap.containsKey(userId)) {
            return mMap.get(userId).keySet();
        }

        return null;
    }

    /**
     * Provides the object for a device id and an user Id
     * @param deviceId the device id
     * @param userId the object id
     * @return the object
     */
    public E objectForDevice(String deviceId, String userId) {
        if ((null != mMap) && !TextUtils.isEmpty(userId) && mMap.containsKey(userId) && !TextUtils.isEmpty(deviceId)) {
            return mMap.get(userId).get(deviceId);
        }

        return null;
    }

    /**
     * Set an object for a dedicated user Id and device Id
     * @param object the object to set
     * @param userId the user Id
     * @param deviceId the device id
     */
    public void setObject(E object, String userId, String deviceId) {
        if ((null != object) && (null != mMap) && !TextUtils.isEmpty(userId) && !TextUtils.isEmpty(deviceId)) {
            HashMap<String, E> subMap = mMap.get(userId);

            if (null == subMap) {
                subMap = new HashMap<>();
                mMap.put(userId, subMap);
            }

            subMap.put(deviceId, object);
        }
    }

    /**
     * Defines the objects map for an user Id
     * @param objectsPerDevices the objects maps
     * @param userId the user id
     */
    public void setObjects(Map<String, E> objectsPerDevices, String userId) {
        if ((null != mMap) && !TextUtils.isEmpty(userId)) {
            if (null == objectsPerDevices) {
                mMap.remove(userId);
            } else {
                mMap.put(userId, new HashMap<>(objectsPerDevices));
            }
        }
    }

    /**
     * Removes objects for a dedicated user
     * @param userId the user id.
     */
    public void removeObjectsForUser(String userId) {
        if ((null != mMap) && !TextUtils.isEmpty(userId)) {
            mMap.remove(userId);
        }
    }

    @Override
    public java.lang.String toString() {
        if (null != mMap) {
            return "MXUsersDevicesMap " + mMap.toString();
        } else {
            return "MXDeviceInfo : null map";
        }
    }
}