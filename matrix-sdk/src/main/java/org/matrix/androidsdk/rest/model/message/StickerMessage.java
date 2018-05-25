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

package org.matrix.androidsdk.rest.model.message;


public class StickerMessage extends ImageMessage {

    public StickerMessage() {
        msgtype = MSGTYPE_STICKER_LOCAL;
    }

    public StickerMessage(StickerJsonMessage stickerJsonMessage) {
        this();
        this.info = stickerJsonMessage.info;
        this.url = stickerJsonMessage.url;
        this.body = stickerJsonMessage.body;
        this.format = stickerJsonMessage.format;
    }

    /**
     * Make a deep copy of this StickerMessage.
     *
     * @return the copy
     */
    public StickerMessage deepCopy() {
        StickerMessage copy = new StickerMessage();
        copy.info = info;
        copy.url = url;
        copy.body = body;
        copy.format = format;

        if (null != file) {
            copy.file = file.deepCopy();
        }

        return copy;
    }
}

