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
package org.matrix.androidsdk.rest.callback;

/**
 * Generic callback interface for asynchronously returning information.
 *
 * @param <T> the type of information to return on success
 */
public interface ApiCallback<T> extends ApiFailureCallback {

    /**
     * Called if the API call is successful.
     *
     * @param info the returned information
     */
    void onSuccess(T info);
}