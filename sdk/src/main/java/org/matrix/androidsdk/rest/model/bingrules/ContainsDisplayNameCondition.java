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
package org.matrix.androidsdk.rest.model.bingrules;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.TextMessage;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.regex.Pattern;

/**
 * Bing rule condition that is satisfied when a message body contains the user's current display name.
 */
public class ContainsDisplayNameCondition extends Condition {
    public ContainsDisplayNameCondition() {
        kind = Condition.KIND_CONTAINS_DISPLAY_NAME;
    }

    public boolean isSatisfied(Event event, String myDisplayName) {
        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            Message msg = JsonUtils.toMessage(event.content);
            if (msg instanceof TextMessage) {
                return caseInsensitiveFind(myDisplayName, ((TextMessage) msg).body);
            }
        }
        return false;
    }

    /**
     * Returns whether a string contains an occurrence of another, as a standalone word, regardless of case.
     * @param subString the string to search for
     * @param longString the string to search in
     * @return whether a match was found
     */
    private boolean caseInsensitiveFind(String subString, String longString) {
        Pattern pattern = Pattern.compile("(\\W|^)" + subString + "(\\W|$)", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(longString).find();
    }
}
