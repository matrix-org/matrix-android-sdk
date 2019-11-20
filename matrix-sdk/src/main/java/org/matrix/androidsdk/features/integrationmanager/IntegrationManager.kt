/*
 * Copyright 2019 New Vector Ltd
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

package org.matrix.androidsdk.features.integrationmanager

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.login.AutoDiscovery
import org.matrix.androidsdk.rest.model.WellKnownManagerConfig
import org.matrix.androidsdk.rest.model.sync.AccountDataElement

/**
 * The integration manager allows to
 *  - Get the Integration Manager that a user has explicitly set for its account (via account data)
 *  - Get the recommended/preferred Integration Manager list as defined by the HomeServer (via wellknown)
 *  - Check if the user has disabled the integration manager feature
 *  - Allow / Disallow Integration manager (propagated to other riot clients)
 *
 *  The integration manager listen to account data, and can notify observer for changes.
 *
 *  The wellknown is refreshed at each application fresh start
 *
 */
class IntegrationManager(val mxSession: MXSession, val context: Context) {


    data class WidgetPermission(
            val stateEventId: String,
            val allowed: Boolean
    )

    data class DomainPermission(
            val domain: String,
            val allowed: Boolean
    )

    /**
     * Return the identity server url, either from AccountData if it has been set, or from the local storage
     * This could return a non null value even if integrationAllowed is false, so always check integrationAllowed
     * before using it
     */
    var integrationServerConfig: IntegrationManagerConfig? = retrieveIntegrationServerConfig()
        private set

    /**
     * Returns false if the user as disabled integration manager feature
     */
    var integrationAllowed = true
        private set

    private var widgetPermissions = emptyList<WidgetPermission>()
    private var videoConfDomainPermissions = emptyList<DomainPermission>()


    fun getWellKnownIntegrationManagerConfigs(): List<WellKnownManagerConfig> {
        //TODO check the 8h refresh for refresh
        return getStoreWellknownIM()
    }

    interface IntegrationManagerManagerListener {
        fun onIntegrationManagerChange(managerConfig: IntegrationManager)
    }

    private val listeners = HashSet<IntegrationManagerManagerListener>()
    fun addListener(listener: IntegrationManagerManagerListener) = synchronized(listeners) { listeners.add(listener) }
    fun removeListener(listener: IntegrationManagerManagerListener) = synchronized(listeners) { listeners.remove(listener) }

    fun enableIntegrationManagerUsage(allowed: Boolean, callback: ApiCallback<Void>) {
        mxSession.enableIntegrationManagerUsage(allowed, object : SimpleApiCallback<Void>(callback) {
            override fun onSuccess(info: Void?) {
                // Optimistic update before account data sync
                if (integrationAllowed != allowed) {
                    integrationAllowed = allowed
                    notifyListeners()
                }
                callback.onSuccess(null)
            }
        })
    }

    fun setWidgetAllowed(stateEventId: String, allowed: Boolean, callback: ApiCallback<Void?>?) {
        val accountDataContent = widgetPermissions
                //transform list to a map
                .groupBy {
                    it.stateEventId
                }
                .mapValues {
                    it.value.first().allowed
                }
                .toMutableMap()
                .also {
                    it[stateEventId] = allowed
                }

        val updatedMap = (
                mxSession.dataHandler.store
                        ?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS)
                        ?.content ?: HashMap()
                ).apply {
            set("widgets", accountDataContent)
        }

        //optimistic update
        widgetPermissions =
                // Remove existing perm for current widget if any, then add updated state
                widgetPermissions.filterNot { it.stateEventId == stateEventId } + listOf(WidgetPermission(stateEventId, allowed))
        notifyListeners()

        mxSession.accountDataRestClient.setAccountData(
                mxSession.myUserId,
                AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS,
                updatedMap,
                object : SimpleApiCallback<Void>(callback) {
                    override fun onSuccess(info: Void?) {
                        callback?.onSuccess(null)
                    }
                })
    }

    fun seVideoConfDomainAllowed(domain: String, allowed: Boolean, callback: ApiCallback<Void?>?) {
        val accountDataContent = videoConfDomainPermissions
                //transform list to a map
                .groupBy {
                    it.domain
                }
                .mapValues {
                    it.value.first().allowed
                }
                .toMutableMap()
                .also {
                    it[domain] = allowed
                }

        //Avoid to override delete unknwon keys
        val updatedMap = (
                mxSession.dataHandler.store
                        ?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS)
                        ?.content ?: HashMap()
                ).apply {
            set("jitsi", accountDataContent)
        }

        //optimistic update
        videoConfDomainPermissions =
                // Remove existing perm for current widget if any, then add updated state
                videoConfDomainPermissions.filterNot { it.domain == domain } + listOf(DomainPermission(domain, allowed))
        notifyListeners()

        mxSession.accountDataRestClient.setAccountData(
                mxSession.myUserId,
                AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS,
                updatedMap,
                object : SimpleApiCallback<Void>(callback) {
                    override fun onSuccess(info: Void?) {
                        callback?.onSuccess(null)
                    }
                })
    }

    fun getKnownWidgetPermissions(): List<WidgetPermission> {
        return widgetPermissions
    }

    fun getKnownVideoConfDomainPermissions(): List<DomainPermission> {
        return videoConfDomainPermissions
    }

    fun isVideoConfDomainAllowed(domain: String?): Boolean {
        return videoConfDomainPermissions.find { it.domain == domain }?.allowed ?: false
    }

    private val eventListener = object : MXEventListener() {
        override fun onAccountDataUpdated(accountDataElement: AccountDataElement) {
            if (accountDataElement.type == AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS) {
                // The integration server has been updated
                val accountWidgets =
                        mxSession.dataHandler.store?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS)

                val integrationManager = accountWidgets?.content?.filter {
                    val widgetContent = it.value as? Map<*, *>
                    (widgetContent?.get("content") as? Map<*, *>)?.get("type") == INTEGRATION_MANAGER_WIDGET
                }?.entries?.firstOrNull()

                val config = getConfigFromData(integrationManager)
                if (config != integrationServerConfig) {
                    localSetIntegrationManagerConfig(config)
                }
            } else if (accountDataElement.type == AccountDataElement.ACCOUNT_DATA_TYPE_INTEGRATION_PROVISIONING) {
                val newValue = mxSession.dataHandler
                        .store
                        ?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_INTEGRATION_PROVISIONING)
                        ?.content?.get("enabled") == true
                if (integrationAllowed != newValue) {
                    integrationAllowed = newValue
                    notifyListeners()
                }
            } else if (accountDataElement.type == AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS) {
                // The integration server has been updated
                val allowedWidgetList = extractWidgetPermissionFromAccountData()
                val allowedJitsiDomaintList = extractJitsiDomainPermissionFromAccountData()

                //Check if there has been a change in that list
                val hasChanges = (widgetPermissions + allowedWidgetList)
                        .groupBy { it.stateEventId }
                        .any {
                            //If size is one, that means that this event is in one list but not in the other
                            it.value.size == 1
                                    // If event is in the 2 lists but with different allowed state
                                    || it.value[0].allowed != it.value[1].allowed
                        }
                        || (videoConfDomainPermissions + allowedJitsiDomaintList)
                        .groupBy { it.domain }
                        .any {
                            //If size is one, that means that this event is in one list but not in the other
                            it.value.size == 1
                                    // If event is in the 2 lists but with different allowed state
                                    || it.value[0].allowed != it.value[1].allowed
                        }

                if (hasChanges) {
                    widgetPermissions = allowedWidgetList
                    videoConfDomainPermissions = allowedJitsiDomaintList
                    notifyListeners()
                }
            }
        }

        override fun onStoreReady() {
            localSetIntegrationManagerConfig(retrieveIntegrationServerConfig(), false)
            widgetPermissions = extractWidgetPermissionFromAccountData()
            notifyListeners()
        }
    }

    init {

        //All listeners are cleared when session is closed, so no need to release this?
        mxSession.dataHandler.addListener(eventListener)

        //Refresh wellknown im if needed
        AutoDiscovery().getServerPreferredIntegrationManagers(mxSession.homeServerConfig.homeserverUri.toString(),
                object : ApiCallback<List<WellKnownManagerConfig>> {
                    override fun onSuccess(info: List<WellKnownManagerConfig>) {
                        setStoreWellknownIM(info)
                    }

                    override fun onUnexpectedError(e: Exception?) {
                    }

                    override fun onNetworkError(e: Exception?) {
                    }

                    override fun onMatrixError(e: MatrixError?) {
                    }

                })
    }

    private fun extractWidgetPermissionFromAccountData(): List<WidgetPermission> {
        val widgets = mxSession.dataHandler
                .store
                ?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS)
                ?.content
                ?.get("widgets")
        return (widgets as? Map<*, *>)
                ?.mapNotNull {
                    (it.key as? String)?.let { eventId ->
                        (it.value as? Boolean)?.let { allowed ->
                            WidgetPermission(
                                    stateEventId = eventId,
                                    allowed = allowed
                            )
                        }
                    }
                } ?: emptyList()
    }

    private fun extractJitsiDomainPermissionFromAccountData(): List<DomainPermission> {
        val jitsiDomain = mxSession.dataHandler
                .store
                ?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS)
                ?.content
                ?.get("jitsi")
        return (jitsiDomain as? Map<*, *>)
                ?.mapNotNull {
                    (it.key as? String)?.let { domain ->
                        (it.value as? Boolean)?.let { allowed ->
                            DomainPermission(
                                    domain = domain,
                                    allowed = allowed
                            )
                        }
                    }
                } ?: emptyList()
    }


    private fun getStoreWellknownIM(): List<WellKnownManagerConfig> {
        val prefs = context.getSharedPreferences(PREFS_IM, Context.MODE_PRIVATE)
        return prefs.getString(WELLKNOWN_KEY, null)?.let {
            try {
                Gson().fromJson<List<WellKnownManagerConfig>>(it,
                        object : TypeToken<List<WellKnownManagerConfig>>() {}.type)
            } catch (any: Throwable) {
                emptyList<WellKnownManagerConfig>()
            }
        } ?: emptyList<WellKnownManagerConfig>()

    }

    private fun setStoreWellknownIM(list: List<WellKnownManagerConfig>) {
        val prefs = context.getSharedPreferences(PREFS_IM, Context.MODE_PRIVATE)
        try {
            val serialized = Gson().toJson(list)
            prefs.edit().putString(WELLKNOWN_KEY, serialized).apply()
        } catch (any: Throwable) {
            //nop
        }
    }


    private fun localSetIntegrationManagerConfig(config: IntegrationManagerConfig?, notify: Boolean = true) {
        integrationServerConfig = config
        if (notify) notifyListeners()
    }

    private fun notifyListeners() {
        synchronized(listeners) {
            listeners.forEach {
                try {
                    it.onIntegrationManagerChange(this)
                } catch (t: Throwable) {
                    Log.e(LOG_TAG, "Failed to notify integration mgr listener", t)
                }
            }
        }
    }

    private fun retrieveIntegrationServerConfig(): IntegrationManagerConfig? {
        val accountWidgets =
                mxSession.dataHandler.store?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS)

        val integrationManager = accountWidgets?.content?.filter {
            val widgetContent = it.value as? Map<*, *>
            (widgetContent?.get("content") as? Map<*, *>)?.get("type") == INTEGRATION_MANAGER_WIDGET
        }?.entries?.firstOrNull()

        return getConfigFromData(integrationManager)

        /*
            "integration_manager_1570191637240": {
                  "content": {
                    "type": "m.integration_manager",
                    "url": "https://scalar-staging.vector.im",
                    "name": "Integration Manager: scalar-staging.vector.im",
                    "data": {
                      "api_url": "https://scalar-staging.vector.im/api"
                    }
                  },
                  "sender": "@valere35:matrix.org",
                  "state_key": "integration_manager_1570191637240",
                  "type": "m.widget",
                  "id": "integration_manager_1570191637240"
            }
         */

    }

    private fun getConfigFromData(integrationManager: Map.Entry<String, Any>?): IntegrationManagerConfig? {
        ((integrationManager?.value as? Map<*, *>)?.get("content") as? Map<*, *>)?.let {
            val uiUrl = it["url"] as? String
            val apiUrl = (it["data"] as? Map<*, *>)?.get("api_url") as? String
            if (uiUrl.isNullOrBlank().not()) {
                return IntegrationManagerConfig(uiUrl!!, apiUrl ?: uiUrl)
            } else {
                return null
            }
        }
        return null
    }

    companion object {
        private const val INTEGRATION_MANAGER_WIDGET = "m.integration_manager"
        private const val PREFS_IM = "IntegrationManager.Storage"
        private const val WELLKNOWN_KEY = "WellKnown"

        private val LOG_TAG = IntegrationManager::class.java.simpleName

    }

}