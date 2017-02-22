/* 
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

package org.matrix.androidsdk.data;

import android.os.Handler;
import android.os.Looper;

import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Class representing the logged-in user.
 */
public class MyUser extends User {

    private static final String LOG_TAG = "MyUser";

    // refresh status
    private boolean mIsAvatarRefreshed = false;
    private boolean mIsDisplayNameRefreshed = false;
    private boolean mAre3PIdsLoaded = false;

    // the account info is refreshed in one row
    // so, if there is a pending refresh the listeners are added to this list.
    private transient ArrayList<ApiCallback<Void>> mRefreshListeners;

    private transient Handler mUiHandler;

    // linked emails to the account
    private transient List<ThirdPartyIdentifier> mEmailIdentifiers = new ArrayList<>();
    // linked phone number to the account
    private transient List<ThirdPartyIdentifier> mPhoneNumberIdentifiers = new ArrayList<>();

    public MyUser(User user) {
        clone(user);

        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Update the user's display name.
     * @param displayName the new name
     * @param callback the async callback
     */
    public void updateDisplayName(final String displayName, ApiCallback<Void> callback) {
        mDataHandler.getProfileRestClient().updateDisplayname(displayName, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // Update the object member before calling the given callback
                MyUser.this.displayname = displayName;
                MyUser.this.mDataHandler.getStore().setDisplayName(displayName);
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the user's avatar URL.
     * @param avatarUrl the new avatar URL
     * @param callback the async callback
     */
    public void updateAvatarUrl(final String avatarUrl, ApiCallback<Void> callback) {
        mDataHandler.getProfileRestClient().updateAvatarUrl(avatarUrl, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // Update the object member before calling the given callback
                MyUser.this.setAvatarUrl(avatarUrl);
                MyUser.this.mDataHandler.getStore().setAvatarURL(avatarUrl);
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the user's presence information.
     * @param presence the presence
     * @param statusMsg the status message
     * @param callback the async callback
     */
    public void updatePresence(final String presence, final String statusMsg, ApiCallback<Void> callback) {
        mDataHandler.getPresenceRestClient().setPresence(presence, statusMsg, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // Update the object member before calling the given callback
                MyUser.this.presence = presence;
                MyUser.this.statusMsg = statusMsg;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Request a validation token for an email address 3Pid
     * @param pid the pid to retrieve a token
     * @param callback the callback when the operation is done
     */
    public void requestEmailValidationToken(ThreePid pid, ApiCallback<Void> callback) {
        if (null != pid) {
            pid.requestEmailValidationToken(mDataHandler.getThirdPidRestClient(), null, callback);
        }
    }

    /**
     * Request a validation token for a phone number 3Pid
     * @param pid the pid to retrieve a token
     * @param callback the callback when the operation is done
     */
    public void requestPhoneNumberValidationToken(ThreePid pid, ApiCallback<RequestPhoneNumberValidationResponse> callback) {
        if (null != pid) {
            pid.requestPhoneNumberValidationToken(mDataHandler.getThirdPidRestClient(), null, callback);
        }
    }

    /**
     * Add a new pid to the account.
     *
     * @param pid      the pid to add.
     * @param bind     true to add it.
     * @param callback the async callback
     */
    public void add3Pid(final ThreePid pid, final boolean bind, final ApiCallback<Void> callback) {
        if (null != pid) {
            mDataHandler.getProfileRestClient().add3PID(pid, bind, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // refresh the third party identifiers lists
                    refreshThirdPartyIdentifiers(callback);
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }

    /**
     * Delete a 3pid from an account
     *
     * @param pid      the pid to delete
     * @param callback the async callback
     */
    public void delete3Pid(final ThirdPartyIdentifier pid, final ApiCallback<Void> callback){
        if (null != pid) {
            mDataHandler.getProfileRestClient().delete3PID(pid, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // refresh the third party identifiers lists
                    refreshThirdPartyIdentifiers(callback);
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }

    /**
     * Build the lists of identifiers
     */
    private void buildIdentifiersLists() {
        List<ThirdPartyIdentifier> identifiers = mDataHandler.getStore().thirdPartyIdentifiers();
        mEmailIdentifiers = new ArrayList<>();
        mPhoneNumberIdentifiers = new ArrayList<>();
        for (ThirdPartyIdentifier identifier : identifiers) {
            switch (identifier.medium) {
                case ThreePid.MEDIUM_EMAIL:
                    mEmailIdentifiers.add(identifier);
                    break;
                case ThreePid.MEDIUM_MSISDN:
                    mPhoneNumberIdentifiers.add(identifier);
                    break;
            }
        }
    }

    /**
     * @return the list of linked emails
     */
    public List<ThirdPartyIdentifier> getlinkedEmails() {
        if (mEmailIdentifiers == null) {
            buildIdentifiersLists();
        }

        return mEmailIdentifiers;
    }

    /**
     * @return the list of linked emails
     */
    public List<ThirdPartyIdentifier> getlinkedPhoneNumbers() {
        if (mPhoneNumberIdentifiers == null) {
            buildIdentifiersLists();
        }

        return mPhoneNumberIdentifiers;
    }

    //================================================================================
    // Refresh
    //================================================================================

    /**
     * Refresh the user data if it is required
     * @param callback callback when the job is done.
     */
    public void refreshUserInfos(final ApiCallback<Void> callback) {
        refreshUserInfos(false, callback);
    }

    /**
     * Refresh the user data if it is required
     * @param callback callback when the job is done.
     */
    public void refreshThirdPartyIdentifiers(final ApiCallback<Void> callback) {
        mAre3PIdsLoaded = false;
        refreshUserInfos(false, callback);
    }


    /**
     * Refresh the user data if it is required
     * @param skipPendingTest true to do not check if the refreshes started (private use)
     * @param callback callback when the job is done.
     */
    public void refreshUserInfos(boolean skipPendingTest, final ApiCallback<Void> callback) {
        if (!skipPendingTest) {
            boolean isPending;

            synchronized (this) {
                // mRefreshListeners == null => no refresh in progress
                // mRefreshListeners != null -> a refresh is in progress
                isPending = (null != mRefreshListeners);

                if (null == mRefreshListeners) {
                    mRefreshListeners = new ArrayList<>();
                }

                if (null != callback) {
                    mRefreshListeners.add(callback);
                }
            }

            if (isPending) {
                // please wait
                return;
            }
        }

        if (!mIsDisplayNameRefreshed) {
            refreshUserDisplayname();
            return;
        }

        if (!mIsAvatarRefreshed) {
            refreshUserAvatarUrl();
            return;
        }

        if (!mAre3PIdsLoaded) {
            refreshThirdPartyIdentifiers();
            return;
        }

        synchronized (this) {
            if (null != mRefreshListeners) {
                for (ApiCallback<Void> listener : mRefreshListeners) {
                    try {
                        listener.onSuccess(null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshUserInfos() : listener.onSuccess failed " + e.getMessage());
                    }
                }
            }

            // no more pending refreshes
            mRefreshListeners = null;
        }
    }

    /**
     * Refresh the avatar url
     */
    private void refreshUserAvatarUrl() {
        mDataHandler.getProfileRestClient().avatarUrl(user_id, new SimpleApiCallback<String>() {
            @Override
            public void onSuccess(String anAvatarUrl) {
                if (mDataHandler.isAlive()) {
                    // local value
                    setAvatarUrl(anAvatarUrl);
                    // metadata file
                    mDataHandler.getStore().setAvatarURL(anAvatarUrl);
                    // user
                    mDataHandler.getStore().storeUser(MyUser.this);

                    mIsAvatarRefreshed = true;

                    // jump to the next items
                    refreshUserInfos(true, null);
                }
            }

            private void onError() {
                if (mDataHandler.isAlive()) {
                    mUiHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshUserAvatarUrl();
                        }
                    }, 1 * 1000);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                // cannot retrieve this value, jump to the next items
                mIsAvatarRefreshed = true;
                refreshUserInfos(true, null);
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                // cannot retrieve this value, jump to the next items
                mIsAvatarRefreshed = true;
                refreshUserInfos(true, null);
            }
        });
    }

    /**
     * Refresh the displayname.
     */
    private void refreshUserDisplayname() {
        mDataHandler.getProfileRestClient().displayname(user_id, new SimpleApiCallback<String>() {
            @Override
            public void onSuccess(String aDisplayname) {
                if (mDataHandler.isAlive()) {
                    // local value
                    displayname = aDisplayname;
                    // store metadata
                    mDataHandler.getStore().setDisplayName(aDisplayname);

                    mIsDisplayNameRefreshed = true;

                    // jump to the next items
                    refreshUserInfos(true, null);
                }
            }

            private void onError() {
                if (mDataHandler.isAlive()) {
                    mUiHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshUserDisplayname();
                        }
                    }, 1 * 1000);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                // cannot retrieve this value, jump to the next items
                mIsDisplayNameRefreshed = true;
                refreshUserInfos(true, null);
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                // cannot retrieve this value, jump to the next items
                mIsDisplayNameRefreshed = true;
                refreshUserInfos(true, null);
            }
        });
    }

    /**
     * Refresh the Third party identifiers i.e. the linked email to this account
     */
    public void refreshThirdPartyIdentifiers() {
        mDataHandler.getProfileRestClient().threePIDs(new SimpleApiCallback<List<ThirdPartyIdentifier>>() {
            @Override
            public void onSuccess(List<ThirdPartyIdentifier> identifiers) {
                if (mDataHandler.isAlive()) {
                    // store
                    mDataHandler.getStore().setThirdPartyIdentifiers(identifiers);

                    buildIdentifiersLists();

                    mAre3PIdsLoaded = true;

                    // jump to the next items
                    refreshUserInfos(true, null);
                }
            }

            private void onError() {
                if (mDataHandler.isAlive()) {
                    mUiHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshThirdPartyIdentifiers();
                        }
                    }, 1 * 1000);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                // cannot retrieve this value, jump to the next items
                mAre3PIdsLoaded = true;
                refreshUserInfos(true, null);
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                // cannot retrieve this value, jump to the next items
                mAre3PIdsLoaded = true;
                refreshUserInfos(true, null);
            }
        });
    }
}
