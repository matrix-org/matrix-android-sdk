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

package org.matrix.matrixandroidsdk.db;


import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

public class ConsoleLatestChatMessageCache {

    private static final String LOG_TAG = "ConsoleLatestChatMessageCache";
    private static final String FILENAME = "ConsoleLatestChatMessageCache";

    private static HashMap<String, String> mLatestMesssageByRoomId = null;

    /**
     * Clear the text caches.
     * @param context The application context to use.
     */
    public static void clearCache(Activity context) {
        String[] filesList = context.fileList();

        for(String file : filesList) {
            try {
                context.deleteFile(file);
            } catch (Exception e) {

            }
        }

        mLatestMesssageByRoomId = null;
    }

    /**
     * Open the texts cache file.
     * @param context the context.
     */
    private static void openLatestMessagesDict(Context context) {
        try
        {
            FileInputStream fis = context.openFileInput(FILENAME.hashCode() + "");
            ObjectInputStream ois = new ObjectInputStream(fis);
            mLatestMesssageByRoomId = (HashMap) ois.readObject();
            ois.close();
            fis.close();
        }catch(Exception e) {
            mLatestMesssageByRoomId = new HashMap<String, String>();
        }
    }

    /**
     * Get the latest written text for a dedicated room.
     * @param context the context.
     * @param roomId the roomId
     * @return the latest message
     */
    public static String getLatestText(Context context, String roomId) {
        if (null == mLatestMesssageByRoomId) {
            openLatestMessagesDict(context);
        }

        if (TextUtils.isEmpty(roomId)) {
            return "";
        }

        if (mLatestMesssageByRoomId.containsKey(roomId)) {
            return mLatestMesssageByRoomId.get(roomId);
        }

        return "";
    }

    /**
     * Update the latest message dictionnary.
     * @param context the context.
     */
    private static void saveLatestMessagesDict(Context context) {
        try
        {
            FileOutputStream fos = context.openFileOutput(FILENAME.hashCode() + "", Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(mLatestMesssageByRoomId);
            oos.close();
            fos.close();
        } catch(Exception e) {
        }
    }

    /**
     * Update the latest message for a dedicated roomId.
     * @param context the context.
     * @param roomId the roomId.
     * @param message the message.
     */
    public static void updateLatestMessage(Context context, String roomId, String message) {
        if (null == mLatestMesssageByRoomId) {
            openLatestMessagesDict(context);
        }

        if (TextUtils.isEmpty(message)) {
            mLatestMesssageByRoomId.remove(roomId);
        }

        mLatestMesssageByRoomId.put(roomId, message);
        saveLatestMessagesDict(context);
    }
}
