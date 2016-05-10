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

package org.matrix.androidsdk;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.ssl.Fingerprint;

import java.util.ArrayList;


/**
 * Represents how to connect to a specific Homeserver, may include credentials to use.
 */
public class HomeserverConnectionConfig {
    private Uri mHsUri;
    private Uri mIdentityServerUri;
    private ArrayList<Fingerprint> mAllowedFingerprints = new ArrayList<Fingerprint>();
    private Credentials mCredentials;
    private boolean mPin;

    /**
     * @param hsUri The URI to use to connect to the homeserver
     */
    public HomeserverConnectionConfig(Uri hsUri) {
        this(hsUri, null);
    }

    /**
     * @param hsUri The URI to use to connect to the homeserver
     * @param credentials The credentials to use, if needed. Can be null.
     */
    public HomeserverConnectionConfig(Uri hsUri, Credentials credentials) {
        this(hsUri, null, credentials, new ArrayList<Fingerprint>(), false);
    }

    /**
     * @param hsUri The URI to use to connect to the homeserver
     * @param identityServerUri The URI to use to manage identity
     * @param credentials The credentials to use, if needed. Can be null.
     * @param allowedFingerprints If using SSL, allow server certs that match these fingerprints.
     * @param pin If true only allow certs matching given fingerprints, otherwise fallback to
     *            standard X509 checks.
     */
    public HomeserverConnectionConfig(Uri hsUri, Uri identityServerUri, Credentials credentials, ArrayList<Fingerprint> allowedFingerprints, boolean pin) {
        if (hsUri == null || (!"http".equals(hsUri.getScheme()) && !"https".equals(hsUri.getScheme())) ) {
            throw new RuntimeException("Invalid home server URI: "+hsUri);
        }

        if ((null != identityServerUri) && (!"http".equals(hsUri.getScheme()) && !"https".equals(hsUri.getScheme()))) {
            throw new RuntimeException("Invalid identity server URI: " + identityServerUri);
        }

        this.mHsUri = hsUri;
        this.mIdentityServerUri = identityServerUri;

        if (null != allowedFingerprints) {
            this.mAllowedFingerprints = allowedFingerprints;
        }

        this.mPin = pin;
        this.mCredentials = credentials;
    }

    public void setHomeserverUri(Uri uri) {
        mHsUri = uri;
    }
    public Uri getHomeserverUri() { return mHsUri; }

    public void setIdentityServerUri(Uri uri) {
        mIdentityServerUri = uri;
    }
    public Uri getIdentityServerUri() { return (null == mIdentityServerUri) ? mHsUri : mIdentityServerUri; }

    public ArrayList<Fingerprint> getAllowedFingerprints() { return mAllowedFingerprints; }

    public Credentials getCredentials() { return mCredentials; }
    public void setCredentials(Credentials credentials) { this.mCredentials = credentials; }


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
                "mIdentityServerUri=" + mIdentityServerUri +
                ", mAllowedFingerprints size=" + mAllowedFingerprints.size() +
                ", mCredentials=" + mCredentials +
                ", mPin=" + mPin +
                '}';
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("home_server_url", mHsUri.toString());
        json.put("identity_server_url", getIdentityServerUri().toString());

        json.put("pin", mPin);

        if (mCredentials != null) json.put("credentials", mCredentials.toJson());
        if (mAllowedFingerprints != null) {
            ArrayList<JSONObject> fingerprints = new ArrayList<JSONObject>(mAllowedFingerprints.size());

            for (Fingerprint fingerprint : mAllowedFingerprints) {
                fingerprints.add(fingerprint.toJson());
            }

            json.put("fingerprints", new JSONArray(fingerprints));
        }

        return json;
    }

    public static HomeserverConnectionConfig fromJson(JSONObject obj) throws JSONException {
        JSONArray fingerprintArray = obj.optJSONArray("fingerprints");
        ArrayList<Fingerprint> fingerprints = new ArrayList<Fingerprint>();
        if (fingerprintArray != null) {
            for (int i = 0; i < fingerprintArray.length(); i++) {
                fingerprints.add(Fingerprint.fromJson(fingerprintArray.getJSONObject(i)));
            }
        }

        JSONObject credentialsObj = obj.optJSONObject("credentials");
        Credentials creds = credentialsObj != null ? Credentials.fromJson(credentialsObj) : null;

        HomeserverConnectionConfig config = new HomeserverConnectionConfig(
                Uri.parse(obj.getString("home_server_url")),
                obj.has("identity_server_url") ? Uri.parse(obj.getString("identity_server_url")) : null,
                creds,
                fingerprints,
                obj.optBoolean("pin", false));

        return config;
    }
}
