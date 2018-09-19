package org.matrix.androidsdk.common;

public class SessionTestParams {

    final boolean withInitialSync;
    final boolean enableCrypto;
    final boolean withLazyLoading;

    private SessionTestParams(final Builder builder) {
        withInitialSync = builder.withInitialSync;
        enableCrypto = builder.enableCrypto;
        withLazyLoading = builder.withLazyLoading;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean withInitialSync;
        private boolean enableCrypto;
        private boolean withLazyLoading;

        public Builder() {
        }

        public Builder withInitialSync(final boolean withInitialSync) {
            this.withInitialSync = withInitialSync;
            return this;
        }

        public Builder enableCrypto(final boolean enableCrypto) {
            this.enableCrypto = enableCrypto;
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
