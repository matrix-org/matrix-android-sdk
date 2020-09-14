/*
 * Copyright 2020 New Vector Ltd
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

package org.matrix.androidsdk.rest.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Class used to parse the content of a m.tagged_events type event.
 * This kind of event defines the tagged events in a room.
 *
 * The content of this event is a tags key whose value is an object mapping the name of each tag
 * to another object. The JSON object associated with each tag is an object where the keys are the
 * event IDs and values give information about the events.
 */
data class TaggedEventsContent (
        @JvmField
        @SerializedName("tags")
        var tags: Map<String, Map<String, TaggedEventInfo>> = emptyMap()
) : Serializable {
    fun getFavouriteEvents(): Map<String, TaggedEventInfo> {
        return tags[TAG_FAVOURITE] ?: emptyMap()
    }

    fun getHiddenEvents(): Map<String, TaggedEventInfo> {
        return tags[TAG_HIDDEN] ?: emptyMap()
    }

    fun tagEvent(eventId: String, info: TaggedEventInfo, tag: String) {
        val tagMap = HashMap<String, TaggedEventInfo>(tags[tag] ?: emptyMap())
        tagMap[eventId] = info

        val updatedTags = HashMap<String, Map<String, TaggedEventInfo>>(tags)
        updatedTags[tag] = tagMap
        tags = updatedTags
    }

    fun untagEvent(eventId: String, tag: String) {
        val tagMap = HashMap<String, TaggedEventInfo>(tags[tag] ?: emptyMap())
        tagMap.remove(eventId)

        val updatedTags = HashMap<String, Map<String, TaggedEventInfo>>(tags)
        updatedTags[tag] = tagMap
        tags = updatedTags
    }

    companion object {
        const val TAGS_KEY = "tags"
        const val TAG_FAVOURITE = "m.favourite"
        const val TAG_HIDDEN = "m.hidden"
    }
}

data class TaggedEventInfo(@JvmField
                           @SerializedName("keywords")
                           var keywords: List<String>? = null,

                           @JvmField
                           @SerializedName("origin_server_ts")
                           var originServerTs: Long? = null,

                           @JvmField
                           @SerializedName("tagged_at")
                           var taggedAt: Long? = null
) : Serializable {
    companion object {
        fun with(keywords: List<String>?, originServerTs: Long?, taggedAt: Long?): TaggedEventInfo {
            return TaggedEventInfo().apply {
                this.keywords = keywords
                this.originServerTs = originServerTs
                this.taggedAt = taggedAt
            }
        }
    }
}
