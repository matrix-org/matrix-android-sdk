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

package org.matrix.androidsdk.features.identityserver

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.MXPatterns
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.HttpException
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.features.terms.TermsNotSignedException
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.client.IdentityAuthRestClient
import org.matrix.androidsdk.rest.client.IdentityPingRestClient
import org.matrix.androidsdk.rest.client.ThirdPidRestClient
import org.matrix.androidsdk.rest.model.RequestEmailValidationResponse
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse
import org.matrix.androidsdk.rest.model.SuccessResult
import org.matrix.androidsdk.rest.model.identityserver.HashDetailResponse
import org.matrix.androidsdk.rest.model.identityserver.IdentityServerRegisterResponse
import org.matrix.androidsdk.rest.model.login.AuthParams
import org.matrix.androidsdk.rest.model.openid.RequestOpenIdTokenResponse
import org.matrix.androidsdk.rest.model.pid.Invite3Pid
import org.matrix.androidsdk.rest.model.pid.ThirdPartyIdentifier
import org.matrix.androidsdk.rest.model.pid.ThreePid
import org.matrix.androidsdk.rest.model.sync.AccountDataElement
import java.security.InvalidParameterException
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.HashSet

class IdentityServerManager(val mxSession: MXSession,
                            context: Context) {

    interface IdentityServerManagerListener {
        fun onIdentityServerChange()
    }

    private val listeners = HashSet<IdentityServerManagerListener>()

    fun addListener(listener: IdentityServerManagerListener) = synchronized(listeners) { listeners.add(listener) }
    fun removeListener(listener: IdentityServerManagerListener) = synchronized(listeners) { listeners.remove(listener) }

    var doesServerRequiresIdentityServer: Boolean = true
    var doesServerAcceptIdentityAccessToken: Boolean = false
    var doesServerSeparatesAddAndBind: Boolean = true

    enum class SupportedFlowResult {
        SUPPORTED,
        NOT_SUPPORTED,
        INTERACTIVE_AUTH_NOT_SUPPORTED
    }


    init {
        localSetIdentityServerUrl(retrieveIdentityServerUrl())

        // TODO Release the listener somewhere?
        mxSession.dataHandler.addListener(object : MXEventListener() {
            override fun onAccountDataUpdated(accountDataElement: AccountDataElement) {
                if (accountDataElement.type == AccountDataElement.ACCOUNT_DATA_TYPE_IDENTITY_SERVER) {
                    // The identity server has been updated
                    val accountDataIdentityServer =
                            mxSession.dataHandler.store?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_IDENTITY_SERVER)

                    accountDataIdentityServer?.content?.let {
                        localSetIdentityServerUrl(it[AccountDataElement.ACCOUNT_DATA_KEY_IDENTITY_SERVER_BASE_URL] as String?)
                    }
                }
            }

            override fun onStoreReady() {
                localSetIdentityServerUrl(retrieveIdentityServerUrl())
            }
        })

        mxSession.doesServerRequireIdentityServerParam(object : SimpleApiCallback<Boolean>() {
            override fun onSuccess(info: Boolean) {
                doesServerRequiresIdentityServer = info
            }
        })

        mxSession.doesServerAcceptIdentityAccessToken(object : SimpleApiCallback<Boolean>() {
            override fun onSuccess(info: Boolean) {
                doesServerAcceptIdentityAccessToken = info
            }
        })

        mxSession.doesServerSeparatesAddAndBind(object : SimpleApiCallback<Boolean>() {
            override fun onSuccess(info: Boolean) {
                doesServerSeparatesAddAndBind = info
            }
        })

    }

    private val identityServerTokensStore = IdentityServerTokensStore(context)

    /**
     * Return the identity server url, either from AccountData if it has been set, or from the local storage
     */
    var identityServerUrl: String? = retrieveIdentityServerUrl()
        private set

    // Rest clients
    private var identityAuthRestClient: IdentityAuthRestClient? = null
    private var thirdPidRestClient: ThirdPidRestClient? = null

    /**
     * Return the identity server url, either from AccountData if it has been set, or from the local storage
     */
    private fun retrieveIdentityServerUrl(): String? {
        val accountDataIdentityServer =
                mxSession.dataHandler.store?.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_TYPE_IDENTITY_SERVER)

        accountDataIdentityServer?.content?.let {
            return it[AccountDataElement.ACCOUNT_DATA_KEY_IDENTITY_SERVER_BASE_URL] as String?
        }

        // Default: use local storage
        return mxSession.homeServerConfig.identityServerUri?.toString()
    }

    private fun identityServerStripProtocol(): String? {
        return identityServerUrl?.let {
            if (it.startsWith("http://")) {
                it.substring("http://".length)
            } else if (it.startsWith("https://")) {
                it.substring("https://".length)
            } else it
        }
    }

    /**
     * Disconnect an identity server
     */
    fun disconnect(callback: ApiCallback<Void?>) {
        setIdentityServerUrl(null, callback)
    }

    /**
     * Update the identity server Url.
     * @param newUrl   the new identity server url. Can be null (or empty) to disconnect the identity server and do not use an
     *                 identity server anymore
     * @param callback callback called when account data has been updated successfully
     */
    fun setIdentityServerUrl(newUrl: String?, callback: ApiCallback<Void?>) {
        var newIdentityServer = newUrl
        if (!newIdentityServer.isNullOrBlank() && !newIdentityServer.startsWith("http")) {
            newIdentityServer = "https://$newIdentityServer"
        }

        if (identityServerUrl == newIdentityServer) {
            // No change
            callback.onSuccess(null)
            return
        }

        if (newIdentityServer.isNullOrBlank()) {
            // User want to remove the identity server
            disconnectPreviousIdentityServer(null, callback)
        } else {
            val uri = Uri.parse(newIdentityServer)
            val hsConfig = try {
                // Check that this is a valid Identity server
                HomeServerConnectionConfig.Builder()
                        .withHomeServerUri(uri)
                        .withIdentityServerUri(uri)
                        .build()
            } catch (e: Exception) {
                callback.onUnexpectedError(InvalidParameterException("Invalid url"))
                return
            }

            IdentityPingRestClient(hsConfig).ping(object : ApiCallback<Void> {
                override fun onSuccess(info: Void?) {
                    // Ok, this is an identity server
                    disconnectPreviousIdentityServer(newIdentityServer, callback)
                }

                override fun onUnexpectedError(e: Exception?) {
                    callback.onUnexpectedError(InvalidParameterException("Invalid identity server"))
                }

                override fun onNetworkError(e: Exception?) {
                    callback.onUnexpectedError(InvalidParameterException("Invalid identity server"))
                }

                override fun onMatrixError(e: MatrixError?) {
                    callback.onUnexpectedError(InvalidParameterException("Invalid identity server"))
                }
            })
        }
    }

    private fun disconnectPreviousIdentityServer(newUrl: String?, callback: ApiCallback<Void?>) {
        if (identityAuthRestClient == null) {
            // No previous identity server, go to next step
            updateAccountData(newUrl, callback)
        } else {
            // Disconnect old identity server first
            val storedToken = identityServerTokensStore
                    .getToken(mxSession.myUserId, identityServerUrl ?: "")

            if (storedToken.isNullOrBlank()) {
                // Previous identity server was not logged in, go to next step
                updateAccountData(newUrl, callback)
            } else {
                identityAuthRestClient?.logout(storedToken, object : ApiCallback<Unit> {
                    override fun onUnexpectedError(e: java.lang.Exception?) {
                        // Ignore any error
                        onSuccess(Unit)
                    }

                    override fun onNetworkError(e: java.lang.Exception?) {
                        // Ignore any error
                        onSuccess(Unit)
                    }

                    override fun onMatrixError(e: MatrixError?) {
                        // Ignore any error
                        onSuccess(Unit)
                    }

                    override fun onSuccess(info: Unit) {
                        identityServerTokensStore
                                .resetToken(mxSession.myUserId, identityServerUrl ?: "")

                        updateAccountData(newUrl, callback)
                    }
                })
            }
        }
    }

    private fun updateAccountData(newUrl: String?, callback: ApiCallback<Void?>) {
        // Update AccountData
        val updatedIdentityServerDict = mapOf(AccountDataElement.ACCOUNT_DATA_KEY_IDENTITY_SERVER_BASE_URL to newUrl)

        mxSession.accountDataRestClient.setAccountData(mxSession.myUserId,
                AccountDataElement.ACCOUNT_DATA_TYPE_IDENTITY_SERVER,
                updatedIdentityServerDict,
                object : SimpleApiCallback<Void?>(callback) {
                    override fun onSuccess(info: Void?) {
                        // Note that this code will also be executed by onAccountDataUpdated(), but we do not want to wait for it
                        localSetIdentityServerUrl(newUrl)
                        callback.onSuccess(null)
                    }
                }
        )
    }

    private fun localSetIdentityServerUrl(newUrl: String?) {
        identityServerUrl = newUrl

        if (newUrl.isNullOrBlank()) {
            identityAuthRestClient = null
            thirdPidRestClient = null
        } else {
            try {
                val alteredHsConfig = HomeServerConnectionConfig.Builder(mxSession.homeServerConfig)
                        .withIdentityServerUri(Uri.parse(newUrl))
                        .build()

                identityAuthRestClient = IdentityAuthRestClient(alteredHsConfig)
                thirdPidRestClient = ThirdPidRestClient(alteredHsConfig)
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Failed to create IS Rest clients", t)
                //What to do from there? this IS is invalid
                return
            }
        }

        synchronized(listeners) { listeners.forEach { it.onIdentityServerChange() } }
    }

    /**
     * Get the token from the store, or request one
     */
    private fun getToken(callback: ApiCallback<String>) {
        val storeToken = identityServerTokensStore
                .getToken(mxSession.myUserId, identityServerUrl ?: "")

        if (storeToken.isNullOrEmpty().not()) {
            callback.onSuccess(storeToken)
        } else {
            // Request a new token
            mxSession.openIdToken(object : SimpleApiCallback<RequestOpenIdTokenResponse>(callback) {
                override fun onSuccess(info: RequestOpenIdTokenResponse) {
                    requestIdentityServerToken(info, callback)
                }

            })
        }
    }

    private fun requestIdentityServerToken(requestOpenIdTokenResponse: RequestOpenIdTokenResponse, callback: ApiCallback<String>) {
        if (identityAuthRestClient == null) {
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }

        identityAuthRestClient?.register(requestOpenIdTokenResponse, object : SimpleApiCallback<IdentityServerRegisterResponse>(callback) {
            override fun onSuccess(info: IdentityServerRegisterResponse) {
                if (info.identityServerAccessToken == null) {
                    callback.onUnexpectedError(Exception("Missing Access Token"))
                    return
                }
                // Store the token for next time
                identityServerTokensStore.setToken(mxSession.myUserId, identityServerUrl!!, info.identityServerAccessToken)

                callback.onSuccess(info.identityServerAccessToken)
            }

            override fun onMatrixError(e: MatrixError) {
                // Handle 404
                if (e.mStatus == 404) {
                    callback.onUnexpectedError(IdentityServerV2ApiNotAvailable())
                } else {
                    super.onMatrixError(e)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                if (e is HttpException && e.httpError.httpCode == 404) {
                    callback.onUnexpectedError(IdentityServerV2ApiNotAvailable())
                } else {
                    super.onUnexpectedError(e)
                }
            }
        })
    }

    fun lookup3Pids(addresses: List<String>, mediums: List<String>, callback: ApiCallback<List<String>>) {
        val client = thirdPidRestClient
        if (client == null) {
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
        } else {
            getToken(object : SimpleApiCallback<String>(callback) {
                override fun onSuccess(info: String) {
                    client.setAccessToken(info)

                    // We need the look up param
                    client.getLookupParam(object : SimpleApiCallback<HashDetailResponse>(callback) {
                        override fun onSuccess(info: HashDetailResponse) {
                            client.lookup3PidsV2(info, addresses, mediums, callback)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            if (e.mStatus == HttpsURLConnection.HTTP_UNAUTHORIZED /* 401 */) {
                                // 401 -> Renew token
                                // Reset the token we know and start again
                                identityServerTokensStore
                                        .resetToken(mxSession.myUserId, identityServerUrl ?: "")
                                lookup3Pids(addresses, mediums, callback)
                            } else if (e.mStatus == HttpsURLConnection.HTTP_FORBIDDEN /* 403 */
                                    && e.errcode == MatrixError.TERMS_NOT_SIGNED) {
                                callback.onUnexpectedError(TermsNotSignedException(info))
                            } else {
                                super.onMatrixError(e)
                            }
                        }
                    })
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is IdentityServerV2ApiNotAvailable) {
                        // Fallback to v1 API
                        client.lookup3Pids(addresses, mediums, callback)
                    } else {
                        super.onUnexpectedError(e)
                    }
                }
            })
        }
    }


    fun submitValidationToken(threePid: ThreePid, token: String, callback: ApiCallback<SuccessResult>) {
        val submitURL = threePid.submitUrl
        if (submitURL != null) {
            mxSession.profileApiClient.submitToken(submitURL, threePid, token, callback)
        } else {
            //Submit to the current id server?
            val client = thirdPidRestClient
            val idServer = identityServerStripProtocol()
            if (idServer == null || client == null) {
                callback.onUnexpectedError(IdentityServerNotConfiguredException())
                return
            }

            client.submitValidationToken(threePid.medium, token, threePid.clientSecret, threePid.sid,
                    object : SimpleApiCallback<Boolean>(callback) {
                        override fun onSuccess(info: Boolean) {
                            callback.onSuccess(SuccessResult(true))
                        }

                        override fun onMatrixError(e: MatrixError) {
                            super.onMatrixError(e)
                        }
                    })

        }

    }


    /**
     * Invite some users to this room.
     *
     * @param identifiers the identifiers iterator
     * @param callback    the callback for when done
     */
    fun inviteInRoom(room: Room, identifiers: Iterator<String>, callback: ApiCallback<Void>) {
        if (!identifiers.hasNext()) {
            callback.onSuccess(null)
            return
        }

        val localCallback = object : SimpleApiCallback<Void>(callback) {
            override fun onSuccess(info: Void?) {
                inviteInRoom(room, identifiers, callback)
            }
        }

        val identifier = identifiers.next()

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            val idServer = identityServerStripProtocol()
            if (idServer == null) {
                //Error case
                callback.onUnexpectedError(IdentityServerNotConfiguredException())
                return
            }

            if (doesServerAcceptIdentityAccessToken) {
                getToken(object : SimpleApiCallback<String>(callback) {
                    override fun onSuccess(token: String) {
                        mxSession.dataHandler.dataRetriever.roomsRestClient.inviteByEmailToRoom(
                                idServer,
                                token,
                                room.roomId, identifier, localCallback)
                    }
                })
            } else {
                mxSession.dataHandler.dataRetriever.roomsRestClient.inviteByEmailToRoom(
                        idServer,
                        null,
                        room.roomId, identifier, localCallback)
            }
        } else {
            mxSession.dataHandler.dataRetriever.roomsRestClient.inviteUserToRoom(room.roomId, identifier, localCallback)
        }
    }


    /**
     *
     */
    @Throws(IdentityServerNotConfiguredException::class)
    fun getInvite3pid(currentUserId: String, ids: List<String>): Pair<List<Invite3Pid>?, List<String>?> {
        val invite3pids = ArrayList<Invite3Pid>()
        val invitedUserIds = ArrayList<String>()
        for (id in ids) {
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(id).matches()) {
                val pid = Invite3Pid()

                if (identityServerStripProtocol() == null) {
                    throw IdentityServerNotConfiguredException()
                }

                pid.id_server = identityServerStripProtocol()
                if (doesServerAcceptIdentityAccessToken) {
                    //XXX what if we don't have yet a token
                    pid.id_access_token = identityServerTokensStore
                            .getToken(mxSession.myUserId, identityServerUrl ?: "")
                    if (pid.id_access_token == null) {
                        Log.w(LOG_TAG, "Server requires id access token, but none is available")
                    }
                }
                pid.medium = ThreePid.MEDIUM_EMAIL
                pid.address = id

                invite3pids.add(pid)
            } else if (MXPatterns.isUserId(id)) {
                // do not invite oneself
                if (!TextUtils.equals(currentUserId, id)) {
                    invitedUserIds.add(id)
                }

            } // TODO add phonenumbers when it will be available
        }
        return invite3pids.takeIf { invite3pids.isEmpty().not() } to invitedUserIds.takeIf { invitedUserIds.isEmpty().not() }
    }


    fun startAddSessionForPhoneNumber(threePid: ThreePid, nextLink: String?, callback: ApiCallback<ThreePid>) {
        val idServer = identityServerStripProtocol()
        if (idServer == null && doesServerRequiresIdentityServer) {
            //we need an id server
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }
        mxSession.profileApiClient.requestPhoneNumberValidationToken(
                idServer.takeIf { doesServerRequiresIdentityServer },
                threePid.phoneNumber,
                threePid.country,
                threePid.clientSecret,
                threePid.sendAttempt,
                false,
                object : SimpleApiCallback<RequestPhoneNumberValidationResponse>(callback) {
                    override fun onSuccess(info: RequestPhoneNumberValidationResponse?) {
                        threePid.sid = info?.sid
                        threePid.submitUrl = info?.submit_url
                        callback.onSuccess(threePid)
                    }
                })

    }

    fun startAddSessionForEmail(threePid: ThreePid, nextLink: String?, callback: ApiCallback<ThreePid>) {
        val idServer = identityServerStripProtocol()
        if (idServer == null && doesServerRequiresIdentityServer) {
            //we need an id server
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }
        mxSession.profileApiClient.requestEmailValidationToken(
                idServer.takeIf { doesServerRequiresIdentityServer },
                threePid.emailAddress,
                threePid.clientSecret,
                threePid.sendAttempt,
                nextLink,
                false,
                object : SimpleApiCallback<RequestEmailValidationResponse>(callback) {
                    override fun onSuccess(info: RequestEmailValidationResponse?) {
                        threePid.sid = info?.sid
                        callback.onSuccess(threePid)
                    }
                })

    }

    /**
     * Check the server flags and call the correct API to add a 3pid.
     * @param auth Recent server API will require the user to authenticate again to perform this action.
     */
    fun finalize3pidAddSession(threePid: ThreePid, auth: AuthParams?, callback: ApiCallback<Void?>) {
        val idServer = identityServerStripProtocol()
        if (idServer == null && doesServerRequiresIdentityServer) {
            //we need an id server
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }
        if (doesServerSeparatesAddAndBind) {
            /// Make a first request to start user-interactive authentication
            mxSession.profileApiClient.add3PID(threePid, auth?.takeIf { auth.session != null },
                    object : SimpleApiCallback<Void?>(callback) {
                        override fun onSuccess(info: Void?) {
                            mxSession.myUser.refreshThirdPartyIdentifiers()
                            callback.onSuccess(null)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            if (auth != null
                                    /* Avoid infinite loop */
                                    && auth.session.isNullOrEmpty()
                                    && e.mStatus == HttpsURLConnection.HTTP_UNAUTHORIZED /* 401 */
                                    && e.errcode == null
                                    && e.mErrorBodyAsString.isNullOrBlank().not()) {
                                JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString).session?.let {
                                    // Retry but authenticated
                                    auth.session = it
                                    finalize3pidAddSession(threePid, auth, callback)
                                    return
                                }
                            }
                            super.onMatrixError(e)
                        }
                    })
        } else {
            mxSession.profileApiClient.add3PIDLegacy(identityServerStripProtocol(), threePid, false,
                    object : SimpleApiCallback<Void?>(callback) {
                        override fun onSuccess(info: Void?) {
                            mxSession.myUser.refreshThirdPartyIdentifiers()
                            callback.onSuccess(null)
                        }
                    })
        }
    }

    /**
     * Use this to check if a given authentication flow is supported by your homeserver for adding 3pid
     * @param stages eg list of stages to check eg listOf(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD)
     * @param done returns #SUPPORTED/#NOT_SUPPORTED if the flow is supported/not supported and #INTERACTIVE_AUTH_NOT_SUPPORTED
     * if the server does not require the user to authenticate again to add 3pid
     */
    fun checkAdd3pidInteractiveFlow(stages: List<String>, callback: ApiCallback<SupportedFlowResult>) {
        if (doesServerSeparatesAddAndBind) {
            mxSession.profileApiClient.add3PID(ThreePid("", "", sid = ""), null,
                    object : SimpleApiCallback<Void?>(callback) {
                        override fun onSuccess(info: Void?) {
                            callback.onUnexpectedError(Exception(""))
                        }

                        override fun onMatrixError(e: MatrixError) {
                            if (e.mStatus == 401 && e.mErrorBodyAsString.isNullOrBlank().not()) {
                                JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString).flows?.let { flowList ->
                                    callback.onSuccess(
                                            if (flowList.any { it.stages == stages }) {
                                                SupportedFlowResult.SUPPORTED
                                            } else {
                                                SupportedFlowResult.NOT_SUPPORTED
                                            }
                                    )
                                    return
                                }
                            }
                            callback.onSuccess(SupportedFlowResult.INTERACTIVE_AUTH_NOT_SUPPORTED)
                        }
                    })
        } else {
            callback.onSuccess(SupportedFlowResult.INTERACTIVE_AUTH_NOT_SUPPORTED)
        }
    }

    /**
     * Starts a bind session for an email.
     * In order to bind a 3pid, an ownership request need to be performed (for email users will receive a mail
     * with a validation link)
     * Returns true if an email validation is needed
     */
    fun startBindSessionForEmail(email: String, nextLink: String?, callback: ApiCallback<ThreePid>) {

        val idServer = identityServerStripProtocol()
        if (idServer == null) {
            //we need an id server
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }
        val threePid = ThreePid.fromEmail(email)
        if (doesServerSeparatesAddAndBind) {
            getToken(object : SimpleApiCallback<String>() {
                override fun onSuccess(info: String) {
                    thirdPidRestClient?.setAccessToken(info)
                    thirdPidRestClient?.requestEmailValidationToken(threePid, nextLink,
                            object : SimpleApiCallback<Void?>(callback) {
                                override fun onSuccess(info: Void?) {
                                    callback.onSuccess(threePid)
                                }
                            })
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is IdentityServerV2ApiNotAvailable) {
                        //mm ? request to HS?
                        legacyDeleteAndRequestToken(threePid, callback, idServer)
                    } else {
                        super.onUnexpectedError(e)
                    }
                }
            })

        } else {
            legacyDeleteAndRequestToken(threePid, callback, idServer)
        }
    }

    private fun legacyDeleteAndRequestToken(threePid: ThreePid, callback: ApiCallback<ThreePid>, idServer: String?) {
        //It's the legacy flow, we need to remove then proxy the id call to the HS
        val param = ThirdPartyIdentifier().apply {
            address = threePid.emailAddress
            medium = threePid.medium
        }

        mxSession.myUser?.delete3Pid(param, object : SimpleApiCallback<Void>(callback) {
            override fun onSuccess(info: Void?) {

                mxSession.profileApiClient.requestEmailValidationToken(idServer,
                        threePid.emailAddress,
                        threePid.clientSecret,
                        threePid.sendAttempt,
                        null,
                        false,
                        object : SimpleApiCallback<RequestEmailValidationResponse>(callback) {
                            override fun onSuccess(info: RequestEmailValidationResponse?) {
                                threePid.sid = info?.sid
                                mxSession.myUser?.refreshThirdPartyIdentifiers()
                                callback.onSuccess(threePid)
                            }
                        })
            }
        })
    }

    fun startBindSessionForPhoneNumber(msisdn: String, countryCode: String, nextLink: String?, callback: ApiCallback<ThreePid>) {

        val idServer = identityServerStripProtocol()
        if (idServer == null) {
            //we need an id server
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }
        val threePid = ThreePid.fromPhoneNumber(msisdn, countryCode)
        if (doesServerSeparatesAddAndBind) {
            getToken(object : SimpleApiCallback<String>() {
                override fun onSuccess(info: String) {
                    thirdPidRestClient?.setAccessToken(info)
                    thirdPidRestClient?.requestPhoneNumberValidationToken(threePid, nextLink,
                            object : SimpleApiCallback<Void?>(callback) {
                                override fun onSuccess(info: Void?) {
                                    callback.onSuccess(threePid)
                                }
                            })
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is IdentityServerV2ApiNotAvailable) {
                        //mm ? request to HS?
                        legacyDeleteAndAddMsisdn(threePid, callback, idServer)
                    } else {
                        super.onUnexpectedError(e)
                    }
                }
            })

        } else {
            legacyDeleteAndAddMsisdn(threePid, callback, idServer)
        }
    }

    private fun legacyDeleteAndAddMsisdn(threePid: ThreePid, callback: ApiCallback<ThreePid>, idServer: String?) {
        //It's the legacy flow, we need to remove then proxy the id call to the HS
        val param = ThirdPartyIdentifier().apply {
            address = threePid.emailAddress
            medium = threePid.medium
        }

        mxSession.myUser?.delete3Pid(param, object : SimpleApiCallback<Void>(callback) {
            override fun onSuccess(info: Void?) {

                mxSession.profileApiClient.requestPhoneNumberValidationToken(idServer,
                        threePid.phoneNumber,
                        threePid.country,
                        threePid.clientSecret,
                        threePid.sendAttempt,
                        false,
                        object : SimpleApiCallback<RequestPhoneNumberValidationResponse>(callback) {
                            override fun onSuccess(info: RequestPhoneNumberValidationResponse?) {
                                threePid.sid = info?.sid
                                mxSession.myUser?.refreshThirdPartyIdentifiers()
                                callback.onSuccess(threePid)
                            }
                        })
            }
        })
    }

    /**
     * Returns true if an email/phone validation is needed
     */
    fun startUnBindSession(medium: String, address: String, countryCode: String? = null, callback: ApiCallback<Pair<Boolean, ThreePid?>>) {

        val idServer = identityServerStripProtocol()
        if (idServer == null) {
            //we need an id server
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }

        if (doesServerSeparatesAddAndBind) {
            mxSession.profileApiClient?.unbind3PID(address, medium, idServer,
                    object : SimpleApiCallback<Void?>(callback) {
                        override fun onSuccess(info: Void?) {
                            callback.onSuccess(false to null)
                        }
                    })
        } else {
            //It's the legacy flow, we need to remove then proxy the id call to the HS
            val param = ThirdPartyIdentifier().apply {
                this.address = address
                this.medium = medium
            }

            mxSession.myUser?.delete3Pid(param, object : SimpleApiCallback<Void>(callback) {
                override fun onSuccess(info: Void?) {

                    if (medium == ThreePid.MEDIUM_EMAIL) {
                        val threePid = ThreePid.fromEmail(address)
                        mxSession.profileApiClient.requestEmailValidationToken(idServer,
                                threePid.emailAddress,
                                threePid.clientSecret,
                                threePid.sendAttempt,
                                null,
                                false,
                                object : SimpleApiCallback<RequestEmailValidationResponse>(callback) {
                                    override fun onSuccess(info: RequestEmailValidationResponse?) {
                                        threePid.sid = info?.sid
                                        callback.onSuccess(true to threePid)
                                    }
                                })
                    } else {
                        val threePid = ThreePid.fromPhoneNumber(address, countryCode)
                        mxSession.profileApiClient.requestPhoneNumberValidationToken(idServer,
                                threePid.phoneNumber,
                                threePid.country,
                                threePid.clientSecret,
                                threePid.sendAttempt,
                                false,
                                object : SimpleApiCallback<RequestPhoneNumberValidationResponse>(callback) {
                                    override fun onSuccess(info: RequestPhoneNumberValidationResponse?) {
                                        threePid.sid = info?.sid
                                        threePid.submitUrl = info?.submit_url
                                        callback.onSuccess(true to threePid)
                                    }
                                })
                    }
                }
            })
        }
    }


    fun finalizeBindSessionFor3PID(threePid: ThreePid, callback: ApiCallback<Void?>) {
        val idServer = identityServerStripProtocol()
        if (idServer == null) {
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
            return
        }

        if (doesServerSeparatesAddAndBind) {
            getToken(object : SimpleApiCallback<String>(callback) {
                override fun onSuccess(token: String) {
                    mxSession.profileApiClient.bind3PID(threePid, idServer, token, callback);
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is IdentityServerV2ApiNotAvailable) {
                        //mm ? request to HS?
                        mxSession.profileApiClient.add3PIDLegacy(idServer, threePid, true, callback)
                    } else {
                        super.onUnexpectedError(e)
                    }
                }

            })
        } else {
            //we need to call old api on HS to add with bind true
            mxSession.profileApiClient.add3PIDLegacy(idServer, threePid, true, callback)
        }
    }

    companion object {
        private val LOG_TAG = IdentityServerManager::class.java.simpleName

        fun removeProtocol(serverUrl: String?): String? {
            return if (serverUrl?.startsWith("http://") == true) {
                serverUrl.substring("http://".length)
            } else if (serverUrl?.startsWith("https://") == true) {
                serverUrl.substring("https://".length)
            } else serverUrl
        }
    }
}
