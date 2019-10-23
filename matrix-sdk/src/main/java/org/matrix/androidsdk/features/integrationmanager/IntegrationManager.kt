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
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.sync.AccountDataElement


class IntegrationManager(val mxSession: MXSession, context: Context) {

    /**
     * Return the identity server url, either from AccountData if it has been set, or from the local storage
     */
    var integrationServerConfig: IntegrationManagerConfig? = retrieveIntegrationServerConfig()
        private set


    interface IntegrationManagerManagerListener {
        fun onIntegrationManagerChange(managerConfig: IntegrationManager)
    }

    private val listeners = HashSet<IntegrationManagerManagerListener>()
    fun addListener(listener: IntegrationManagerManagerListener) = synchronized(listeners) { listeners.add(listener) }
    fun removeListener(listener: IntegrationManagerManagerListener) = synchronized(listeners) { listeners.remove(listener) }

    init {
        mxSession.dataHandler.addListener(object : MXEventListener() {
            override fun onAccountDataUpdated(accountDataElement: AccountDataElement) {
                if (accountDataElement.type == AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS) {
                    // The integration server has been updated
                    val accountWidgets =
                            mxSession.dataHandler.store?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS)

                    val integrationManager = accountWidgets?.content?.filter {
                        val widgetContent = it.value as? Map<*, *>
                        (widgetContent?.get("content") as? Map<*, *>)?.get("type") == INTEGRATION_MANAGER_WIDGET
                    }?.entries?.first()

                    val config = getConfigFromData(integrationManager)
                    if (config != integrationServerConfig) {
                        localSetIntegrationManagerConfig(config)
                    }
                }
            }

            override fun onStoreReady() {
                localSetIntegrationManagerConfig(retrieveIntegrationServerConfig())
            }
        })
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
        }?.entries?.first()

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
                return EmptyIntegrationManagerConfig
            }
        }
        return null
    }

    companion object {
        private const val INTEGRATION_MANAGER_WIDGET = "m.integration_manager"

        private val LOG_TAG = IntegrationManager::class.java.simpleName

        val EmptyIntegrationManagerConfig = IntegrationManagerConfig("", "")

    }

}
fun IntegrationManagerConfig.isEmptyConfig(): Boolean {
    return this == IntegrationManager.EmptyIntegrationManagerConfig
}