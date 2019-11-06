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
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.login.AutoDiscovery
import org.matrix.androidsdk.rest.model.WellKnownManagerConfig
import org.matrix.androidsdk.rest.model.sync.AccountDataElement


class IntegrationManager(val mxSession: MXSession, val context: Context) {


    private val PREFS_IM = "IntegrationManager.Storage"
    private val WELLKNOWN_KEY = "WellKnown"

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
            }

            if (accountDataElement.type == AccountDataElement.ACCOUNT_DATA_TYPE_INTEGRATION_PROVISIONING) {
                val newValue = mxSession.dataHandler
                        .store
                        ?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_INTEGRATION_PROVISIONING)
                        ?.content?.get("enabled") == true
                if (integrationAllowed != newValue) {
                    integrationAllowed = newValue
                    notifyListeners()
                }

            }
        }

        override fun onStoreReady() {
            localSetIntegrationManagerConfig(retrieveIntegrationServerConfig())
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


    private fun localSetIntegrationManagerConfig(config: IntegrationManagerConfig?) {
        integrationServerConfig = config
        notifyListeners()
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

        private val LOG_TAG = IntegrationManager::class.java.simpleName

    }

}
