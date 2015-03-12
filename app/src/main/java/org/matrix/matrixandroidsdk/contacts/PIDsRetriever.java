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

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.Matrix;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * retrieve the contact matrix IDs
 */
public class PIDsRetriever {

    public static interface PIDsRetrieverListener {
        /**
         * Called when the contact PIDs are retrieved
         */
        public void onPIDsRetrieved(Contact contact, boolean has3PIDs);
    }

    // MatrixID <-> email
    private static HashMap<String, String> mMatrixIdsByElement = new HashMap<String, String>();

    // con
    private static PIDsRetrieverListener mListener = null;

    public static void setPIDsRetrieverListener(PIDsRetrieverListener listener) {
        mListener = listener;
    }

    /**
     * Clear the email to matrix id conversion table
     */
    public static void onAppBackgrounded() {
        mMatrixIdsByElement = new HashMap<String, String>();
    }

    /**
     * Retrieve the matrix IDs from the contact fields (only emails are supported by now).
     * Update the contact fields with the found Matrix Ids.
     * The update could require some remote requests : they are done only localUpdateOnly is false.
     * @param context the context.
     * @param contact The contact to update.
     * @param localUpdateOnly true to only support refresh from local information.
     */
    public static void retrieveMatrixIds(Context context, final Contact contact, boolean localUpdateOnly) {
        ArrayList<String> requestedAddresses = new ArrayList<String>();

        // check if the emails have only been checked
        // i.e. requested their match PID to the identity server.
        for(String email : contact.mEmails) {
            if (mMatrixIdsByElement.containsKey(email)) {
               String matrixID = mMatrixIdsByElement.get(email);

                if (matrixID.length() > 0) {
                    contact.mMatrixIdsByElement.put(email,matrixID);
                }
            } else {
                requestedAddresses.add(email);
            }
        }

        // the lookup has not been done on some emails
        if ((requestedAddresses.size() > 0) && (!localUpdateOnly)) {
            ArrayList<String> medias = new ArrayList<String>();

            for (int index = 0; index < requestedAddresses.size(); index++) {
                medias.add("email");
            }

            final ArrayList<String> fRequestedAddresses = requestedAddresses;

            MXSession session = Matrix.getInstance(context.getApplicationContext()).getDefaultSession();
            session.lookup3Pids(fRequestedAddresses, medias, new ApiCallback<ArrayList<String>>() {
                @Override
                public void onSuccess(ArrayList<String> pids) {
                    boolean foundPIDs = false;

                    // update the global dict
                    // and the contact dict
                    for (int i = 0; i < fRequestedAddresses.size(); i++) {
                        String address = fRequestedAddresses.get(i);
                        String pid = pids.get(i);

                        mMatrixIdsByElement.put(address, pid);

                        if (pid.length() != 0) {
                            foundPIDs = true;
                            contact.mMatrixIdsByElement.put(address, pid);
                        }
                    }

                    // warn the listener of the update
                    if (null != mListener) {
                        mListener.onPIDsRetrieved(contact, foundPIDs);
                    }
                }

                // ignore the network errors
                // will be checked again later
                @Override
                public void onNetworkError(Exception e) {

                }
                @Override
                public void onMatrixError(MatrixError e) {

                }
                @Override
                public void onUnexpectedError(Exception e) {

                }
            });
        }

    }
}

