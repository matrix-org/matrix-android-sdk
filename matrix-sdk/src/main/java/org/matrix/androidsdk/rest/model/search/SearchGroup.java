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
package org.matrix.androidsdk.rest.model.search;

import java.util.HashMap;

/**
 * subclass representing a search API response
 */
public class SearchGroup {
    /**
     * Total number of results found.
     * The key is "room_id" (TODO_SEARCH) , the value the group.
     */
    public HashMap<String, SearchGroupContent> group;
}
