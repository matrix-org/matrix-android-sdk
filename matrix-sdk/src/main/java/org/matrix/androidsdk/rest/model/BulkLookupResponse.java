/*
 * Copyright 2017 OpenMarket Ltd
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
package org.matrix.androidsdk.rest.model;

import java.util.List;

/**
 * 3 pids request response
 */
public class BulkLookupResponse {
    /**
     * Required. An array of array containing the 3PID Types with the medium in first position,
     * the address in second position and Matrix user ID in third position.
     */
    //  List of [medium, value, mxid]
    public List<List<String>> threepids;
}
