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

/**
 * An interface for listening the store events
 */
public interface IMXStoreListener {
    /**
     * The store has loaded its internal data.
     * Let any post processing data management.
     * It is called in the store thread before calling onStoreReady.
     * @param accountId the account id
     */
    void postProcess(String accountId);

    /**
     * Called when the store is initialized
     */
    void onStoreReady(String accountId);

    /**
     * Called when the store initialization fails.
     */
    void onStoreCorrupted(String accountId, String description);

    /**
     * Called when the store has no more memory
     */
    void onStoreOOM(String accountId, String description);
}
