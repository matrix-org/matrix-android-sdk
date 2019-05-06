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

package org.matrix.androidsdk.rest.model.sync;

import org.matrix.androidsdk.crypto.model.rest.DeviceInfo;
import org.matrix.androidsdk.data.comparator.Comparators;

import java.util.Collections;
import java.util.List;

public class DeviceInfoUtil {
    /**
     * Sort a devices list by their presences from the most recent to the oldest one.
     *
     * @param deviceInfos the deviceinfo list
     */
    public static void sortByLastSeen(List<DeviceInfo> deviceInfos) {
        if (null != deviceInfos) {
            Collections.sort(deviceInfos, Comparators.descComparator);
        }
    }
}
