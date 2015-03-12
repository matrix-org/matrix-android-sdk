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

package org.matrix.matrixandroidsdk.contacts;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A simple contact class
 */
public class Contact {

    public String mContactId;
    public String mDisplayName;
    public String mThumbnailUri;
    public Bitmap mThumbnail = null;

    public ArrayList<String>mPhoneNumbers = new ArrayList<String>();
    public ArrayList<String>mEmails = new ArrayList<String>();
    public HashMap<String, String> mMatrixIdsByElement = null;

    /**
     * Check if some matrix IDs are linked to emails
     * @return true if some matrix IDs have been retrieved
     */
    public boolean hasMatridIds(Context context) {
        Boolean localUpdateOnly = (null != mMatrixIdsByElement);

        // the PIDs are not yet retrieved
        if (null == mMatrixIdsByElement) {
            mMatrixIdsByElement = new HashMap<String, String>();
        }

        if (couldContainMatridIds()) {
            PIDsRetriever.retrieveMatrixIds(context, this, localUpdateOnly);
        }

        return (mMatrixIdsByElement.size() != 0);
    }

    /**
     * Check if the contact could contain some matrix Ids
     * @return true if the contact could contain some matrix IDs
     */
    public boolean couldContainMatridIds() {
        return (0 != mEmails.size());
    }


    /**
     * Returns the first retrieved matrix ID.
     * @return the first retrieved matrix ID.
     */
    public String getFirstMatrixId() {
        if (mMatrixIdsByElement.size() != 0) {
            return mMatrixIdsByElement.values().iterator().next();
        } else {
            return null;
        }
    }
}

