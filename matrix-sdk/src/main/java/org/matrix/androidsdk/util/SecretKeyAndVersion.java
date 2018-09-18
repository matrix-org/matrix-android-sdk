/*
 * Copyright 2018 New Vector Ltd
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

package org.matrix.androidsdk.util;

import javax.crypto.SecretKey;

public class SecretKeyAndVersion {
    // The key
    private final SecretKey localProtectionKey;

    // the android version when the key has been generated
    private final int androidVersion;

    public SecretKeyAndVersion(SecretKey localProtectionKey, int androidVersion) {
        this.localProtectionKey = localProtectionKey;
        this.androidVersion = androidVersion;
    }

    public SecretKey getLocalProtectionKey() {
        return localProtectionKey;
    }

    public int getAndroidVersion() {
        return androidVersion;
    }
}
