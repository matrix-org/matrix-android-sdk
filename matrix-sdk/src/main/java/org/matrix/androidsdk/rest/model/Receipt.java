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
package org.matrix.androidsdk.rest.model;

import java.util.Comparator;

public class Receipt implements java.io.Serializable {
    public String userId;
    public long originServerTs;

    public Receipt(String anUserId, long aTs) {
        userId = anUserId;
        originServerTs = aTs;
    }

    // comparator to sort from the oldest to the latest.
    public static final Comparator<Receipt> ascComparator = new Comparator<Receipt>() {
        @Override
        public int compare(Receipt receipt1, Receipt receipt2) {
            return (int)(receipt1.originServerTs - receipt2.originServerTs);
        }
    };

    // comparator to sort from the latest to the oldest.
    public static final Comparator<Receipt> descComparator = new Comparator<Receipt>() {
        @Override
        public int compare(Receipt receipt1, Receipt receipt2) {
            return (int)(receipt2.originServerTs - receipt1.originServerTs);
        }
    };



}