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
package org.matrix.androidsdk.api.response;

public class Message {
    public static final String MSGTYPE_TEXT = "m.text";
    public static final String MSGTYPE_EMOTE = "m.emote";
    public static final String MSGTYPE_IMAGE = "m.image";
    public static final String MSGTYPE_AUDIO = "m.audio";
    public static final String MSGTYPE_VIDEO = "m.video";
    public static final String MSGTYPE_LOCATION = "m.location";

    public String msgtype;
}
