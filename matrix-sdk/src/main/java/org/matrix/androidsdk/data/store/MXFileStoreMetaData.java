/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.data.store;

import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MXFileStoreMetaData implements java.io.Serializable {
    // The obtained user id.
    public String mUserId = null;

    // The access token to create a MXRestClient.
    public String mAccessToken = null;

    //  The token indicating from where to start listening event stream to get live events.
    public String mEventStreamToken = null;

    //The current version of the store.
    public int mVersion = -1;

    /**
     User information
     */
    public String mUserDisplayName = null;
    public String mUserAvatarUrl = null;
    public List<ThirdPartyIdentifier> mThirdPartyIdentifiers = null;
    public List<String> mIgnoredUsers = new ArrayList<>();
    public Map<String, List<String>> mDirectChatRoomsMap = null;

    // crypto
    public boolean mEndToEndDeviceAnnounced = false;

    public MXFileStoreMetaData deepCopy() {
        MXFileStoreMetaData copy = new MXFileStoreMetaData();

        copy.mUserId = mUserId;
        copy.mAccessToken = mAccessToken;
        copy.mEventStreamToken = mEventStreamToken;
        copy.mVersion = mVersion;
        copy.mUserDisplayName = mUserDisplayName;

        if (null != copy.mUserDisplayName) {
            copy.mUserDisplayName.trim();
        }

        copy.mUserAvatarUrl = mUserAvatarUrl;
        copy.mThirdPartyIdentifiers = mThirdPartyIdentifiers;
        copy.mIgnoredUsers = mIgnoredUsers;
        copy.mDirectChatRoomsMap = mDirectChatRoomsMap;
        copy.mEndToEndDeviceAnnounced = mEndToEndDeviceAnnounced;

        return copy;
    }

}
