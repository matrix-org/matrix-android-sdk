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

package org.matrix.androidsdk.data;

import android.os.Handler;
import android.os.Looper;

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

    // refresh status
    private boolean mIsAvatarRefreshed = false;
    private boolean mIsDislayNameRefreshed = false;
    private boolean mAre3PIdsLoaded = false;

    // the account info is refreshed in one row
    // so, if there is a pending refresh the listeners are added to this list.
    private ArrayList<ApiCallback<Void>> mRefreshListeners;

    private Handler mUiHandler;

    // linked emails to the account
    private List<ThirdPartyIdentifier> mThirdPartyIdentifiers;

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
     * Request a validation token for a dedicated 3Pid
     * @param pid the pid to retrieve a token
     * @param callback the callback when the operation is done
     */
    public void requestValidationToken(ThreePid pid, ApiCallback<Void> callback) {
        if (null != pid) {
            pid.requestValidationToken(mDataHandler.getThirdPidRestClient(), callback);
        }
    }

    /**
     * Add a a new pid to the account.
     * @param pid the pid to add.
     * @param bind
     * @param callback the async callback
     */
    public void add3Pid(ThreePid pid, boolean bind, final ApiCallback<Void> callback) {
        if (null != pid) {
            mDataHandler.getProfileRestClient().add3PID(pid, bind, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // refresh the emails list
                    refreshLinkedEmails(callback);
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
     * @return the list of third party identifiers
     */
    public List<ThirdPartyIdentifier> getThirdPartyIdentifiers() {
        List<ThirdPartyIdentifier> list;

        if (mAre3PIdsLoaded) {
            list = mThirdPartyIdentifiers;
        } else {
            list = mDataHandler.getStore().thirdPartyIdentifiers();
        }

        if (null == list) {
            list = new ArrayList<ThirdPartyIdentifier>();
        }

        return list;
    }

    /**
     * @return the list of linked emails
     */
    public List<String> getlinkedEmails() {
        List<ThirdPartyIdentifier> list = getThirdPartyIdentifiers();

        ArrayList<String>emails = new ArrayList<String>();

        for(ThirdPartyIdentifier identier : list) {
            emails.add(identier.address);
        }

        return emails;
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
    public void refreshLinkedEmails(final ApiCallback<Void> callback) {
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
                    mRefreshListeners = new ArrayList<ApiCallback<Void>>();
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

        if (!mIsDislayNameRefreshed) {
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
                onError();
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError();
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

                    mIsDislayNameRefreshed = true;

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
                onError();
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError();
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
                    // local value
                    mThirdPartyIdentifiers = identifiers;

                    // store
                    mDataHandler.getStore().setThirdPartyIdentifiers(identifiers);

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
                onError();
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError();
            }
        });
    }
}
