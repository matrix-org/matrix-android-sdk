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

package org.matrix.androidsdk.common;

public class SessionTestParams {

    final boolean withInitialSync;
    final boolean withCryptoEnabled;
    final boolean withLazyLoading;

    private SessionTestParams(final Builder builder) {
        withInitialSync = builder.withInitialSync;
        withCryptoEnabled = builder.withCryptoEnabled;
        withLazyLoading = builder.withLazyLoading;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean withInitialSync;
        private boolean withCryptoEnabled;
        private boolean withLazyLoading;

        public Builder() {
        }

        public Builder withInitialSync(final boolean withInitialSync) {
            this.withInitialSync = withInitialSync;
            return this;
        }

        public Builder withCryptoEnabled(final boolean withCryptoEnabled) {
            this.withCryptoEnabled = withCryptoEnabled;
            return this;
        }

        public Builder withLazyLoading(final boolean withLazyLoading) {
            this.withLazyLoading = withLazyLoading;
            return this;
        }

        public SessionTestParams build() {
            return new SessionTestParams(this);
        }
    }
}
