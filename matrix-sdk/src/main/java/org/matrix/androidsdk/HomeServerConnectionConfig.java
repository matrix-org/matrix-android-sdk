/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk;

import android.net.Uri;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.ssl.Fingerprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents how to connect to a specific Homeserver, may include credentials to use.
 */
public class HomeServerConnectionConfig {

    // the home server URI
    private Uri mHsUri;
    // the identity server URI
    private Uri mIdentityServerUri;
    // the anti-virus server URI
    private Uri mAntiVirusServerUri;
    // allowed fingerprints
    private List<Fingerprint> mAllowedFingerprints = new ArrayList<>();
    // the credentials
    private Credentials mCredentials;
    // tell whether we should reject X509 certs that were issued by trusts CAs and only trustcerts with matching fingerprints.
    private boolean mPin;

    /**
     * @param hsUri The URI to use to connect to the homeserver
     */
    public HomeServerConnectionConfig(Uri hsUri) {
        this(hsUri, null);
    }

    /**
     * @param hsUri       The URI to use to connect to the homeserver
     * @param credentials The credentials to use, if needed.
     */
    public HomeServerConnectionConfig(Uri hsUri, @Nullable Credentials credentials) {
        this(hsUri, null, credentials, new ArrayList<Fingerprint>(), false);
    }

    /**
     * @param hsUri               The URI to use to connect to the homeserver
     * @param identityServerUri   The URI to use to manage identity
     * @param credentials         The credentials to use, if needed. Can be null.
     * @param allowedFingerprints If using SSL, allow server certs that match these fingerprints.
     * @param pin                 If true only allow certs matching given fingerprints, otherwise fallback to
     *                            standard X509 checks.
     */
    public HomeServerConnectionConfig(Uri hsUri, @Nullable Uri identityServerUri, @Nullable Credentials credentials, List<Fingerprint> allowedFingerprints, boolean pin) {
        if (hsUri == null || (!"http".equals(hsUri.getScheme()) && !"https".equals(hsUri.getScheme()))) {
            throw new RuntimeException("Invalid home server URI: " + hsUri);
        }

        if ((null != identityServerUri) && (!"http".equals(hsUri.getScheme()) && !"https".equals(hsUri.getScheme()))) {
            throw new RuntimeException("Invalid identity server URI: " + identityServerUri);
        }

        // remove trailing /
        if (hsUri.toString().endsWith("/")) {
            try {
                String url = hsUri.toString();
                hsUri = Uri.parse(url.substring(0, url.length() - 1));
            } catch (Exception e) {
                throw new RuntimeException("Invalid home server URI: " + hsUri);
            }
        }

        // remove trailing /
        if ((null != identityServerUri) && identityServerUri.toString().endsWith("/")) {
            try {
                String url = identityServerUri.toString();
                identityServerUri = Uri.parse(url.substring(0, url.length() - 1));
            } catch (Exception e) {
                throw new RuntimeException("Invalid identity server URI: " + identityServerUri);
            }
        }

        mHsUri = hsUri;
        mIdentityServerUri = identityServerUri;
        mAntiVirusServerUri = null;

        if (null != allowedFingerprints) {
            mAllowedFingerprints = allowedFingerprints;
        }

        mPin = pin;
        mCredentials = credentials;
    }

    /**
     * Update the home server URI.
     *
     * @param uri the new HS uri
     */
    public void setHomeserverUri(Uri uri) {
        mHsUri = uri;
    }

    /**
     * @return the home server uri
     */
    public Uri getHomeserverUri() {
        return mHsUri;
    }

    /**
     * Update the identity server uri.
     *
     * @param uri the new identity server uri
     */
    public void setIdentityServerUri(Uri uri) {
        mIdentityServerUri = uri;
    }

    /**
     * @return the identity server uri
     */
    public Uri getIdentityServerUri() {
        return (null == mIdentityServerUri) ? mHsUri : mIdentityServerUri;
    }

    /**
     * Update the anti-virus server URI.
     *
     * @param uri the new anti-virus uri
     */
    public void setAntiVirusServerUri(Uri uri) {
        mAntiVirusServerUri = uri;
    }

    /**
     * @return the anti-virus server uri
     */
    public Uri getAntiVirusServerUri() {
        // Consider the HS uri by default.
        return (null == mAntiVirusServerUri) ? mHsUri : mAntiVirusServerUri;
    }

    /**
     * @return the allowed fingerprints.
     */
    public List<Fingerprint> getAllowedFingerprints() {
        return mAllowedFingerprints;
    }

    /**
     * @return the credentials
     */
    public Credentials getCredentials() {
        return mCredentials;
    }

    /**
     * Update the credentials.
     *
     * @param credentials the new credentials
     */
    public void setCredentials(Credentials credentials) {
        mCredentials = credentials;
    }

    /**
     * @return whether we should reject X509 certs that were issued by trusts CAs and only trust
     * certs with matching fingerprints.
     */
    public boolean shouldPin() {
        return mPin;
    }

    @Override
    public String toString() {
        return "HomeserverConnectionConfig{" +
                "mHsUri=" + mHsUri +
                ", mIdentityServerUri=" + mIdentityServerUri +
                ", mAntiVirusServerUri=" + mAntiVirusServerUri +
                ", mAllowedFingerprints size=" + mAllowedFingerprints.size() +
                ", mCredentials=" + mCredentials +
                ", mPin=" + mPin +
                '}';
    }

    /**
     * Convert the object instance into a JSon object
     *
     * @return the JSon representation
     * @throws JSONException the JSON conversion failure reason
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("home_server_url", mHsUri.toString());
        json.put("identity_server_url", getIdentityServerUri().toString());
        if (mAntiVirusServerUri != null) json.put("antivirus_server_url", mAntiVirusServerUri.toString());

        json.put("pin", mPin);

        if (mCredentials != null) json.put("credentials", mCredentials.toJson());
        if (mAllowedFingerprints != null) {
            ArrayList<JSONObject> fingerprints = new ArrayList<>(mAllowedFingerprints.size());

            for (Fingerprint fingerprint : mAllowedFingerprints) {
                fingerprints.add(fingerprint.toJson());
            }

            json.put("fingerprints", new JSONArray(fingerprints));
        }

        return json;
    }

    /**
     * Create an object instance from the json object.
     *
     * @param jsonObject the json object
     * @return a HomeServerConnectionConfig instance
     * @throws JSONException the conversion failure reason
     */
    public static HomeServerConnectionConfig fromJson(JSONObject jsonObject) throws JSONException {
        JSONArray fingerprintArray = jsonObject.optJSONArray("fingerprints");
        ArrayList<Fingerprint> fingerprints = new ArrayList<>();
        if (fingerprintArray != null) {
            for (int i = 0; i < fingerprintArray.length(); i++) {
                fingerprints.add(Fingerprint.fromJson(fingerprintArray.getJSONObject(i)));
            }
        }

        JSONObject credentialsObj = jsonObject.optJSONObject("credentials");
        Credentials creds = credentialsObj != null ? Credentials.fromJson(credentialsObj) : null;

        HomeServerConnectionConfig config = new HomeServerConnectionConfig(
                Uri.parse(jsonObject.getString("home_server_url")),
                jsonObject.has("identity_server_url") ? Uri.parse(jsonObject.getString("identity_server_url")) : null,
                creds,
                fingerprints,
                jsonObject.optBoolean("pin", false));

        // Set the anti-virus server uri if any
        if (jsonObject.has("antivirus_server_url")) {
            config.setAntiVirusServerUri(Uri.parse(jsonObject.getString("antivirus_server_url")));
        }

        return config;
    }
}
