Changes to Matrix Android SDK in 0.9.30 (2019-10-09)
=======================================================

Bugfix:
 - App won't start with some custom HS config #499

Translations:
 - Updated translations


Changes to Matrix Android SDK in 0.9.29 (2019-10-04)
=======================================================

Corrective release

Bugfix:
 - Fix / Keysbackup not working (failed to get version)

Changes to Matrix Android SDK in 0.9.28 (2019-10-03)
=======================================================

Improvements:
 - Display correctly the revoked third-party invites.
 - Support optional default STUN server when no ICE provided by HS
 - Use wellknown to discover the IS of a HS (vector-im/riot-android#3283)
 - Make identity server configurable
 - Privacy: MSC2290 (#3300)

API Change:
 - `MXSession.openIdToken()` callback has a more typed parameter
 - DefaultRetrofit2CallbackWrapper has been removed because it does not manage MatrixError. Use RestAdapterCallback instead.
 - IMXEventListener.onAccountDataUpdated() method now has a parameter: the account data which has been updated.
 - Third party identifiers (mail, phone) related calls (add/bind) are now delegated to the IdentityServerManager instead of
   directly from MyUser. Now use mxSession.getIdentityManager().xxx
 - Room#invite now requires the session (to delegate to correct identity server)

Translations:
 - Emoji verification name discrepancy between riot-web and riotX (vector-im/riotX-android#355)

Others:
 - Remove ParentRestClient from crypto module and use a common parent Rest Client (dinsic-pim/tchap-android#539)
 - MXSession: Add doesServerRequireIdentityServerParam() and doesServerAcceptIdentityAccessToken() methods.
 - Remove the bind true flag from 3PID calls on registration (vector-im/riot-android#3252)

Changes to Matrix Android SDK in 0.9.27 (2019-08-28)
=======================================================

/!\ Security:
 - The homeserver access token was incorrectly included in requests sent to the Identity Server, a separate service.
   The client should prompt the user to logout and login again to renew the token, unless the user is happy to trust the Identity Server provider with their access token (e.g. if the homeserver and identity server are operated by the same provider).

Features:
 - Allow Matrix SDK client to configure the filter used for pagination (vector-im/riot-android#3237)

Improvements:
 - Add a TermsManager (vector-im/riot-android#3225)

Bugfix:
 - Stop sending the access token of the homeserver to the identity server
 - VoIP: Stop falling back to Google for STUN (vector-im/riot-android#3223).
 - EventIDs: Add regex to match eventIDs for v4 and v5 rooms
 - Failed to send a message in a new joined room (invited by email)

Others:
 - Remove useless log (vector-im/riot-android#3236)

Build:
 - Migrate to androidx (following https://developer.android.com/jetpack/androidx/migrate)
 - WebRTC: upgrade webrtc library, using the one build along with Jitsi

Changes to Matrix Android SDK in 0.9.26 (2019-07-24)
=======================================================

Build:
 - Downgrade $okhttp_version in config file


Changes to Matrix Android SDK in 0.9.25 (2019-07-24)
=======================================================

Build:
 - Upgraded okhttp to 3.12.1 using global $okhttp_version settings

Changes to Matrix Android SDK in 0.9.24 (2019-07-18)
=======================================================

Features:
 - Add "server_name" parameter to the join room request (vector-im/riot-android#3204)

Improvements:
 - RoomSummary: Add a listener to override the method used to handle the last message of the rooms.
 - RoomCreateContent: Add missing fields (room_version and m.federate)

Bugfix:
 - Fix replies showing mxids instead of display names (vector-im/riot-android#2468)
 - Fix / SAS, don't error if we don't know about some keys (vector-im/riot-android#3184)

Others:
 - SDK has been splitted into 3 modules, to help extracting the crypto part.
 - Rewrite react-native-webrtc dependency to remove the additional react-native dependency.

Build:
 - Upgrade gradle version from 4.10.1 to 5.4.1
 - Ensure Olm library is downloaded from the jitpack repository

Changes to Matrix Android SDK in 0.9.23 (2019-05-03)
=======================================================

Features:
 - E2E: SAS Verification
 - Upgrade olm-sdk.aar from version 3.0.0 to version 3.1.2

Build:
 - olm-sdk.aar is now hosted by jitpack

Changes to Matrix Android SDK in 0.9.22 (2019-04-23)
=======================================================

Others:
 - Move share folder outside of session folder

Changes to Matrix Android SDK in 0.9.21 (2019-04-11)
=======================================================

Bugfix:
 - Fix crash on MXWebRtcView, reported by the PlayStore

Others:
 - Add the possibility to configure room name for empty rooms

Changes to Matrix Android SDK in 0.9.20 (2019-04-01)
=======================================================

Improvements:
 - Upgrade `react-native-webrtc` library to version `1.67.1-jitsi-9`, using Jitsi repository (vector-im/riot-android#2412)
 - Upgrade `react-native-webrtc` library to version `1.69.0-jitsi-799011`, using Jitsi repository (vector-im/riot-android#3096)

Others:
 - Fix wording issue for redacted events (vector-im/riot-android#3033)

Changes to Matrix Android SDK in 0.9.19 (2019-03-25)
=======================================================

Improvements:
 - Fix partially shared session (#446)

Bugfix:
 - Fix regression on ToDevice requests, sent with empty object

Build:
 - Upgrade kotlin, library dependencies, targetSdk and gradle version

Changes to Matrix Android SDK in 0.9.18 (2019-03-07)
=======================================================

Features:
 - .well-known support (vector-im/riot-android#2982)

Improvements:
 - Improve import of keys performance (vector-im/riot-android#2960)

Bugfix:
 - Failed to send a video captured by the native camera. Replace the file scheme "file://" with "file:/" used by some Android devices.
 - Fix / Escape room v3 event ids in permalinks (vector-im/riot-android#2981)

Others:
 - Handle well-known data in the login response (vector-im/riot-android#3002)

Changes to Matrix Android SDK in 0.9.17 (2019-02-21)
=======================================================

Features:
 - Ensure Room V3 eventId format is supported.

Improvements:
 - Crypto: Cancel share request on restore/import (vector-im/riot-android#2928).
 - CreateRoomParams: add `powerLevelContentOverride` param to override the default power level event.
 - KeysBackup: Declare backup trust using new `PUT /room_keys/version/{version}` API (vector-im/riot-android#2921).

Bugfix:
 - Fix DataSaveMode issue in filter
 - CreateRoomParams - setHistoryVisibility: remove existing value if any.
 - Fix issue in Japanese translation (#423)

Others:
 - Create a RealmCryptoStoreModule to allow clients of the Matrix SDK to use Realm

Build:
 - Enforce lint rules check

Changes to Matrix Android SDK in 0.9.16 beta (2019-02-01)
=======================================================

Improvements:
 - MXCrypto: Add key backup passphrase support (vector-im/riot-android#2771).
 - KeysBackup: Do not reset KeysBackup.keysBackupVersion in error states.
 - KeysBackup: Implement the true deleteKeysBackupVersion Client-Server API.

Bugfix:
 - Fix RestClient exception in case of non-ASCII application label (#419)
 - remove un-serializable fields in MatrixError
 - MXCrypto: ensure listeners are called on the UiThread

API Change:
 - Some KeysBackup methods have been renamed for clarity

Others:
 - fix typo in CHANGES.rst (wrong year)

Changes to Matrix Android SDK in 0.9.15 (2019-01-02)
=======================================================

Improvements:
 - isValidRecoveryKey() ignores now all whitespace characters, not only spaces

Bugfix:
 - MXCrypto: Use the last olm session that got a message (vector-im/riot-android#2772).
 - Ensure there is no ghost device in the Realm crypto store (vector-im/riot-android#2784)

Test:
 - New test for recovery key with newlines in it

Changes to Matrix Android SDK in 0.9.14 (2018-12-13)
=======================================================

Features:
 - Add terms model for the register/login flow (vector-im/riot-android#2442)

Improvements:
 - Any Account data element, even if the type is not known is persisted.
 - The crypto store is now implemented using a Realm database. The existing file store will be migrated at first usage (#398)
 - Upgrade olm-sdk.aar from version 2.3.0 to version 3.0.0
 - Implement the backup of the room keys in the KeysBackup class (vector-im/riot-android#2642)

Bugfix:
 - Generate thumbnails for gifs rather than throw an error (#395)
 - Room members who left are listed with the actual members (vector-im/riot-android#2744)
 - I'm not allow to send message in a new joined room (vector-im/riot-android#2743)
 - Matrix Content Scanner: Refresh the server public key on error with "MCS_BAD_DECRYPTION" reason.
 - Fix several issues on Room history and enable LazyLoading on this request.

API Change:
 - new API in CallSoundsManager to allow client to play the specified Ringtone (vector-im/riot-android#827)
 - IMXStore.storeAccountData() has been renamed to IMXStore.storeRoomAccountData()
 - MXCrypto: importRoomKeys methods now return number of imported keys and number of total keys in the Callback.
 - `MXMediasCache` has been renamed to `MXMediaCache` (and `Medias` to `Media`)
 - Remove IconAndTextDialogFragment, it's up to the application to manage UI.

Build:
 - Introduce Kotlin to the SDK

Test:
 - New tests for crypto store, including migration from File store to Realm store
 - New tests for keys backup feature

Changes to Matrix Android SDK in 0.9.13 (2018-11-06)
=======================================================

Improvements:
 - Add RTL support
 - PermalinkUtils is now able to parse a permalink

Bugfix:
 - Fix crash when change visibility room (vector-im/riot-android#2679)
 - Move `invite_room_state` to the UnsignedData object (vector-im/riot-android#2555)

API Change:
 - MXSession.initUserAgent() takes a second parameter for flavor description.

Build:
 - Treat some Lint warnings as errors

Changes to Matrix Android SDK in 0.9.12 (2018-10-18)
=======================================================

Improvements:
 - Improve certificate pinning management for HomeServerConnectionConfig.
 - Room display name is now computed by the Matrix SDK

Bugfix:
 - Fix strip previous reply when they contain new line (vector-im/riot-android#2612)
 - Enable CLEARTEXT communication for http endpoints (vector-im/riot-android#2495)
 - Back paginating in a room with LL makes some avatars to vanish (vector-im/riot-android#2639)

Changes to Matrix Android SDK in 0.9.11 (2018-10-10)
=======================================================

Bugfix:
 - Add a setter to set MXDataHandler to MXFileStore

Changes to Matrix Android SDK in 0.9.10 (2018-10-08)
=======================================================

Features:
 - Handle m.room.pinned_events state event and ServerNoticeUsageLimitContent
 - Manage server_notices tag and server quota notices (vector-im/riot-android#2440)
 - Add handling of filters (#345)

Improvements:
 - Encrypt local data (PR #305)
 - Add GET /versions request to the LoginRestClient

Bugfix:
 - Fix excessive whitespace on quoted messages (#348)
 - Scroll to bottom no longer keeps inertia after position change (#354)

API Change:
 - A Builder has been added to create HomeServerConnectionConfig instances.
 - SentState.UNDELIVERABLE has been renamed to SentState.UNDELIVERED
 - Extract patterns and corresponding methods from MXSession to a dedicated MXPatterns class.
 - MatrixMessageListFragment is now abstract and take an Adapter type as class parameter
 - Parameter guestAccess removed from MxSession.createRoom(). It had no effect.
 - EventTimeline is now exposed as an interface. Use EventTimelineFactory to instantiate it. 

Others:
 - Boolean deserialization is more permissive: "1" or 1 will be handle as a true value (#358)
 - MXSession.setUseDataSaveMode(boolean) is now deprecated. Handle filter-id lookup in your app and use MXSession.setSyncFilterOrFilterId(String)

Changes to Matrix Android SDK in 0.9.9 (2018-08-30)
=======================================================

Improvements:
 - Clear unreachable Url when clearing media cache (vector-im/riot-android#2479)
 - "In reply to" is not clickable on Riot Android yet. Make it a plain text (vector-im/riot-android#2469)

Bugfix:
 - Removing room from 'low priority' or 'favorite' does not work (vector-im/riot-android#2526)
 - MatrixError mResourceLimitExceededError is now managed in MxDataHandler (vector-im/riot-android#2547)

API Change:
 - MxSession constructor is now private. Please use MxSession.Builder() to create a MxSession

Changes to Matrix Android SDK in 0.9.8 (2018-08-27)
=======================================================

Features:
 - Manage server_notices tag and server quota notices (vector-im/riot-android#2440)

Bugfix:
 - Room aliases including the '@' and '=' characters are now recognized as valid (vector-im/riot-android#2079, vector-im/riot-android#2542)
 - Room name and topic can be now set back to empty (vector-im/riot-android#2345)

API Change:
 - Remove PieFractionView class from the Matrix SDK. This class is now in Riot sources (#336)
 - MXMediasCache.createTmpMediaFile() methods are renamed to createTmpDecryptedMediaFile()
 - MXMediasCache.clearTmpCache() method is renamed to clearTmpDecryptedMediaCache()
 - Add MXMediasCache.moveToShareFolder() to move a tmp decrypted file to another folder to prevent deletion during sharing. New API MXMediasCache.clearShareDecryptedMediaCache() can be called when the application is resumed. (vector-im/riot-android#2530)

Changes to Matrix Android SDK in 0.9.7 (2018-08-09)
=======================================================

Features:
 - Add MetricsListener to measure some startup and stats metrics
 - Implements ReplyTo feature. When sending an event, you can now pass another Event to reply to it. (vector-im/riot-android#2390)
 - Manage room versioning 

Improvements:
 - MXCrypto: Encrypt the messages for invited members according to the history visibility (if the option is enabled in MXCryptoConfig).
 - Upgrade olm-sdk.aar from version 2.2.2 to version 2.3.0
 - Add a method to MediaScanRestClient to get the public key of the media scanner server
 - Add support for the scanning and downloading of unencrypted thumbnails
 - Set user agent on manual HttpConnection (i.e. not using a RestClient)
 - Bullet points look esthetically bad (#2462)

Bugfix:
 - Send Access Token as a header instead of a url parameter to upload content (#311)
 - Add API CallSoundsManager.startRingingSilently() to fix issue when incoming call sound is disable (vector-im/riot-android#2417)
 - Use same TxId when resending an event. The eventId is used as a TxId. (vector-im/riot-android#1997)
 - Fix bad bing on '@room' pattern. (vector-im/riot-android#2461)
 - Fix Crash loop reported by RageShake (vector-im/riot-android#2501)

API Change:
 - Parameter historyVisibility removed from MxSession.createRoom(). It had no effect.
 - New API: CreateRoomParams.setHistoryVisibility(String historyVisibility) to force the history visibility during Room creation.
 - Room.getLiveState() has been removed, please use Room.getState() (#310)
 - new API: Room.canReplyTo(Event) to know if replying to this event is supported.
 - New APIs PermalinkUtils.createPermalink() to create matrix permalink for an event, a room, a user, etc.
 - New API: add hasMembership(String membership) to simplify test on room membership

Others:
 - Do not log DEBUG messages in release versions (PR #304)
 - Rename some internal classes to change 'Bing' to 'Push'

Changes to Matrix Android SDK in 0.9.6 (2018-07-03)
=======================================================

Features:
 - ContentManager: support a potential anti-virus scanner (PR #283).
 - HomeServerConnectionConfig: allow configuration of TLS parameters (PR#293).

Improvements:
 - MXCrypto: Add reRequestRoomKeyForEvent to re-request encryption keys to decrypt an event (vector-im/riot-android#2319).
 - MXCrypto: Add MXCryptoConfig class to customize/configure the e2e encryption.

Bugfix:
 - Prevent crash on KitKat
 - Prevent leaking of filenames in uploads to E2EE rooms
 - Prefer message text instead of subject
 - Fix issue with notification count in a RoomSummary
 - Fix NullPointerException reported by GooglePlay (vector-im/riot-android#2382)
 - Fix crash in CallSoundsManager

API Change:
 - New API: add device_id param to LoginRestClient.loginWithUser()
 - API change: Event.isUnkownDevice() as been renamed to Event.isUnknownDevice() (typo)
 - Some APIs has changed to use interface instead of implementation as type (ex: "Map" instead of "HashMap")

Others:
 - Media cache is flushed because of the new format of ids.

Build:
 - Add script to check code quality
 - Travis will now check if CHANGES.rst has been modified for each PR

Test:
 - Crypto tests have been cleaned - All tests are passed


Changes to Matrix Android SDK in 0.9.5 (2018-06-01)
=======================================================

Bugfix:
 - Fix regression on URL preview, along with regression on searching user. (vector-im/riot-android#2264)
 - Fix bad param format on reporting content request (vector-im/riot-android#2301)

API Change:
 - New API in MXSession to deactivate account

Changes to Matrix Android SDK in 0.9.4 (2018-05-25)
=======================================================

Features:
 * Implement 'reply to' feature.
 * Add support to "M_CONSENT_NOT_GIVEN" error.
 * Implement 'send widget' feature.

Improvements:
 * RestClient: Adding request to deactivate an account.
 * Javadoc is removed from the source, it is now available as a Jenkins artifact

Bugfixes:
 * Riot-android sends the wrong content for m.ignored_user_list (vector-im/riot-android#2043)
 * do not allow non-mxc content URLs (#268).

Build:
 * Travis CI has been activated to build the Pull request

Changes to Matrix Android SDK in 0.9.3 (2018-04-20)
=======================================================

Features:
 * Render stickers in the timeline (vector-im/riot-android#2097).

Improvements:
 * MXFileStore: Remove the trick with the huge timestamp to mark an undelivered event (vector-im/riot-android#2081).
 * Handle pending invitations : set the room is ready for invitations.
 * MXSession: Update correctly the Direct Chats. Map when a room is removed from it.
 * RestClient: Send Access-Token as header instead of query param, thanks to @krombel (PR #251).
 
Build:
 * Update to SDK 27.

Changes to Matrix Android SDK in 0.9.2 (2018-03-30)
=======================================================

Improvements:
 * Make state event redaction handling gentler with homeserver (vector-im/riot-android#2117).

Changes to Matrix Android SDK in 0.9.1 (2018-03-14)
=======================================================

Improvements:
 * Room: Add isDirect method.
 * Optimise computation of isDirect chat flag.

Translations:
 * Bulgarian, added thanks to @rbozhkova.

Changes to Matrix Android SDK in 0.9.0 (2018-02-15)
=======================================================

Improvements:
 * Groups: Handle the user's groups and their data (vector-im/riot-meta#114).
 * Groups: Add methods to accept group invite and leave it (vector-im/riot-meta#114).
 * Groups Flair: Handle the publicised groups for the matrix users (vector-im/riot-meta#118).
 * Groups Flair: Support the new state event type `m.room.related_groups`(vector-im/riot-meta#118).
 * Improve media cache (PR #226).
 * Force to save the room events when their states are updated.
 * Do not retry a request if the response is not formatted as expected.
 * Increase the call timeout to reduce the number of failed calls with a slow network.
 * Add configuration errors management.
 * Improve the text extraction from android share feature.
 * Improve the user id regex to supported extended format (vector-im/riot-android#1927).
 * Update the room notifications management (vector-im/riot-meta#9).
 * Saved the incoming key requests in the store (PR #232).
 * Improve isAvatarThumbnailCached() to avoid flickering.
 * Add the global URL preview flag management.
 * Synchronize the room url preview disabled by the user.

Bugfixes:
 * Do kicked rooms appear in the room list? (#1856).
 * Fix a sharekeys issue when the user devices were not downloaded to check if they exist.
 * Messages are not displayed properly (#1805).
 * If an m.room.encryption event is redacted, android thinks the room is no longer encrypted (vector-im/riot-android#1064).
 * Excessive battery use reported by my phones software (vector-im/riot-android#1838).
 * Create a direct chat with an email address is not marked/seen as direct (vector-im/riot-android#1931).
 * F-Droid: can't compile with react-native-webrtc.aar built from source (#227).
 * Fix empty emote case.
 * Fix downloadManagerTask error management.
 * Empty chat history (#1875).
 * Fix a server issue : some group members are duplicated.
 * Fix a sharekeys issue : getKeysClaimed() failed to return the decrypted value.

Translations:
 * Catalan, added thanks to @sim6 and @d1d4c.
 * Arabic, added thanks to @SafaAlfulaij.

Changes to Matrix Android SDK in 0.8.08 (2018-01-16)
=======================================================

Bugfixes:

* #1859 : After a user redacted their own join event from HQ, Android DoSes us with /context requests.
* Update to the latest JITSI libs

Changes to Matrix Android SDK in 0.8.07 (2017-12-18)
=======================================================

Bugfixes:

* Manage string or boolean value for BingRule highlight
* #1799 : Riot often chokes on messages 
* #1802 : Expected status header not present. Restore okhttp*.2.2 until we update to OKHtpp 3.X.

Changes to Matrix Android SDK in 0.8.06 (2017-12-06)
=======================================================

Improvements:

* Report some e2e codes from JS.
* Refactor the Bingrule class.

Bugfixes:

* Fix many issues reported by google analytics.
* Call Room.MarkAllAsRead() after joining a room else the notification counts won't be incremented.

Changes to Matrix Android SDK in 0.8.05 (2017-11-28)
=======================================================

Improvements:

* Improve the room creation methods.

Bugfixes:

* Fix many issues reported by google analytics.
* #1700 : Jump to first unread message didn't jump anywhere, just stayed at the same position where it was before, although there are more unread messages.
* #1722 : duplicated messages in history 
* #1756 : Scrolling breaks badly if there is some server lag

Changes to Matrix Android SDK in 0.8.04 (2017-11-15)
=======================================================

Features:

* Add the e2e keys sharing.

Improvements:

* Refactor the calls management and fix many audio path issues.
* Sanitise the functions description to generate a better javadocs.

Bugfixes:

* Fix many issues reported by google analytics.
* Fix the encrypting messages colour
* Fix a battery draining issue after ending a video call
* #119 : Notifications: implement @room notifications on mobile
* #207 : RoomState - updateRoomName: the provided string `name` is not checked correctly
* #208 : Attached image: `thumbnail_info` and `thumbnail_url` must be moved in `content.info` dictionary
* #1659 : Created a room with only me inside. After writing "test" I left it but it is still on my list with no way of deleting it.
* #1678 : cannot join #Furnet_#S:spydar007.com

Changes to Matrix Android SDK in 0.8.03 (2017-10-05)
=======================================================

Improvements: 

* Improve the initial sync management : the data are stored only when the initial sync data are stored.


Changes to Matrix Android SDK in 0.8.02 (2017-10-03)
=======================================================

Features:

* Add widgets management.
* Add javadoc to the project.
* Add getUrlPreview request.

Improvements: 

* Replace the third party call lib (libJingle by webrtc).
* Increase the initial sync request timeout.
* Increase the incoming call timeout to one minute.

Bugfixes:

* Fix several crashes reported by Google Analytics.
* #1592 Client unable to connect on server after certificate update
* #1603 Stale device lists when users re-join e2e rooms 
* #1613 Phone rings for ever 


Changes to Matrix Android SDK in 0.8.01 (2017-09-04)
=======================================================

Improvements: 

* Remove useless resources
* Adapt the request timeouts to the network speed
* Disable the room state events saving / loading to reduce the used RAM.
* Use the data saver mode to perform the initial sync to reduce the loading time.
* Replace the timer by an alarm to manage the delay between two sync requests.
* Do not retry to send the call invitation if it fails.


Bugfixes:

* Fix many crashes
* Fix crashes when too many asynctasks was started.
* Improve the offline management to avoid sending an "online" status if the application is automatically restarted.
* #1467 : Rotating the device while an image is uploading inserts the image twice.
* #1548 : Unable to decrypt: encryption not enabled 


Changes to Matrix Android SDK in 0.8.00 (2017-08-01)
=======================================================

Features:

* Add the new users search API.
* Remove the default implementation of the messages adapter.
* Add a method to remove older medias.
* Add a beta data saver mode.

Improvements: 

* Improve the catchup synchronisation (reduce the number of stored events)
* Refactor the state events storage format to reduce its size.
* Improve the backward / forward management to avoid having UI lags.

Bugfixes:

* fix many GA issues
* fix read markers issues.
* #1407 : Getting notifications for unrelated messages. 
* #1433 : Riot crashed while opening https://vector.im/develop/#/room/#kekistan:kek.community
* Fix the matrix items regex to support servers with port number (like $111:matrix.org:8080).


Changes to Matrix Android SDK in 0.7.15 (2017-07-25)
=======================================================

Bugfixes:

* Remove server catchup patch (i.e the sync requests were triggered until getting something).
  It used to drain battery on small accounts.
* Fix application resume edge cases (fdroid only)

Changes to Matrix Android SDK in 0.7.14 (2017-07-04)
=======================================================

Features:

* Add the read markers management 

Bugfixes:

* Fix many crashes reported by GA.
* #1297 : Event encrypting was stuck 
* #1331 : The Events service is properly restarted in some race conditions
* #1340 : sync is stuck after the application has been killed in background
* #1347 : Sign out from stopped home server crashes after trying for ages 
* #1371 : Endless trying to sync to the current state.
* #1390 : Phone went to sleep while uploading a photo. Now it cannot send the photo.
* #1392 : unexpected 'mention only" notification when the user name is disambiguoused 

Changes to Matrix Android SDK in 0.7.13 (2017-06-12)
=======================================================

Bugfixes:

* #1302 : No room / few rooms are displayed an application update / first launch

Changes to Matrix Android SDK in 0.7.12 (2017-06-08)
=======================================================

Bugfixes:

* #1291 : don't receive anymore notifications after updating to the 0.6.10 version
* #1292 : No more room after updating the application on 0.6.10 and killing it during the loading

Changes to Matrix Android SDK in 0.7.11 (2017-05-30)
=======================================================

Features:

* Add the new public rooms API.
* Add some languages support.
* Add Room.forget API.

Improvements: 

* Add a dedicated method to mark all messages as read.
* Ignore invalid avatarURL.
* Add plaftform flavor in the request user agent.
* Set the log timestamp to UTC.
* Move the room preview management in a dedicated thread to avoid UI thread lags.
* Improve the network connection detection.

Bugfixes:

* Issues reported by GA.
* Fix some registration issues.
* #1080 : The message sent with QuickReply is not added to the room history if the dedicated room activity is opened.
* #1093 : Cannot decrypt attachments on Android 4.2.X.
* #1129 : App-Name changed from "Riot" to "Matrix Android SDK"
* #1148 : Cannot login when the device language is set to turkish
* #1186 : Infinite back pagination whereas the app is in background
* #1210 : Please don't log encryption payloads in rageshakes.
* Fix double cryptostore  creation.
* Fix some crypto issues.

Changes to Matrix Android SDK in 0.7.10 (2017-03-15)
=======================================================

Features:

* Add the MSDISN support for the registration and the authentification (3Pid).
* Add the e2e keys import/export.
* Add some settings to send encrypted messages to veryfied devices only (for a dedicated room or any room).

Improvements: 

* Improve the session loading time.
* Add a callback to prevent sending messages to unknown devices.
* Add a custom user agent with the application / SDK version.
* Improve the audio attachments support

Bugfixes:

* Fix many cryptography issues.
* Fix many issues reported by GA.
* #929 : Retry schedule is too aggressive for arbitrary endpoints
* #938 : Unbanning users is broken
* #952 : Launch a call in a e2e and 1:1 room with unknown devices make the call fails.

Changes to Matrix Android SDK in 0.7.9 (2017-01-27)
=======================================================

Improvements: 

* Use the new contacts lookup request.

Bugfixes:

* #894 : matrix user id regex does not allow underscore in the name
* Fix backward compatibility issue.

Changes to Matrix Android SDK in 0.7.8 (2017-01-23)
=======================================================

Improvements: 

* Update the olm library.
* Improve the email bunch lookup method

Bugfixes:

* The users were not saved after the login. They were only saved after restarting the application.

Changes to Matrix Android SDK in 0.7.7 (2017-01-17)
=======================================================

Improvements: 

* Video call : The local preview is moveable.
* e2e : The e2e data is now saved synchronously to avoid not being able to read our own messages if the application crashes.
* Use a dedicated logger to avoid having truncated logs.

Bugfixes:

* Fix many crashes reported by Google Analytics.
* Update the olm library (fix the random string generation issue, invalid emoji support...).
* #816 : Custom server URL bug.
* #821 : Room creation with a matrix user from the contacts list creates several empty rooms.
* #841 : Infinite call ringing.

Changes to Matrix Android SDK in 0.7.5 (2016-12-19)
=======================================================

Improvements: 

* The e2e keys are sent by 100 devices chunk

Bugfixes:

* Several issues reported by GA.
* In some edge cases, the read all function does not clear the unread messages counters.

Changes to Matrix Android SDK in 0.7.4 (2016-12-13)
=======================================================

Improvements:

* Many e2e improvements
* Reduce the stores launching times.

Bugfixes:

* Several issues reported by GA.
* #374 : Check if Event.unsigned.age can be used to detect if the event is still valid. 
* #687 : User adress instead of display name in call event
* #723 : Cancelling download of encrypted image does not work

Changes to Matrix Android SDK in 0.7.3 (2016-11-24)
=======================================================

Improvements: 

* reduce the memory use to avoid having out of memory error.

Bugfixes:

* The rest clients did not with http v2 servers.

Changes to Matrix Android SDK in 0.7.2 (2016-11-23)
=======================================================

Features:

* Add room.isDirectChatInvitation method
* Send thumbnail for the image messages
* Update to the attachment encryptions V2

Improvements: 

* Improve the cryptostore management to avoid working on UI thread.
* Improve the crypto store to avoid application logout when the files are corrupted
* Update the olm lib.

Bugfixes:

* #680 : Unsupported TLS protocol version
* #731 : Crypto : Some device informations are not displayed whereas the messages can be decrypted.
* #739 : [e2e] Ringtone from call is different according to the encryption state of the room
* #742 : Unable to send messages in #megolm since build 810: Network error 

Changes to Matrix Android SDK in 0.7.1 (2016-11-21)
=======================================================

Improvements: 

* Improve the cryptostore management to avoid working on UI thread.

Bugfixes:

* Add try / catch block in JSonUtils methods (GA issues)

Changes to Matrix Android SDK in 0.7.0 (2016-11-18)
=======================================================

Features:

* Encryption
* DirectChat management
* Devices list management

Bugfixes:

* GA issues
* #529 : the unread notified messages are not properly cleared when the network connection is lost / unstable
* #540 : All the store data is lost if there is an OOM error while saving it.
* #546 : Invite a left user doesn't display his displayname.
* #558 ! Global search : the back pagination does not work anymore
* #561 : URLs containing $s aren't linkified correctly 
* #562 : Some redacted events were restored at next application launch
* #589 : Login as email is case sensistive 
* #590 : Email validation token is sent even to invalid emails 
* #602 : The 1:1 room avatar must be the other member avatar if no room avatar was set
* #611 : Remove display name event is blank 

Changes to Matrix Android SDK in 0.6.2 (2016-09-19)
=======================================================

Bugfixes:

* Ensure that ended calls are no more seen as active call.	
* #490 : Start a call conference and stop it asap don't stop it
* #501 : [VoIP] crash in caller side when a started video call is stopped asap.
* Some files were sent with an invalid mimetype text/uri-list.

Changes to Matrix Android SDK in 0.6.1 (2016-09-13)
=======================================================

Features:

* #406 : Chat screen: New message(s) notification
* #465 : Chat screen: disable auto scroll to bottom on keyboard presentation 


Bugfixes:

* #386 : Sender picture missing in notification
* #396 : Displayed name should be consistent for all events 
* #397 : Generated avatar should be consistent for all events 
* #404 : The message displayed in a room when a 3pid invited user has registered is not clear 
* #407 : Chat screen: The read receipts from the conference user should be ignored
* #415 : Room Settings: some addresses are missing
* #439 : add markdown support for emotes 
* #445 : Unable to join federated rooms with Android app 
* #455 : Until e2e is impl'd, encrypted msgs should be shown in the UI as unencryptable warning text 
* #473 : Huge text messages are not rendered on some android devices

Changes to Matrix Android SDK in 0.6.0 (2016-08-11)
=======================================================

Improvements:

* #351 : VoIP Checklist (add the end of call reason, receive a call while already in call).

Features:

* Add the attachment upload/download detailled information (progress, mean bitrate, estimated remaining time...)
* Add the conference call management.

Bugfixes:

* #290 : Redacting membership events should immediately reset the displayname & avatar of room members
* #320 : Sanitise the logs to remove private data
* #330 : some media are not downloadable
* #352 : some rooms are not displayed in the recents when the 10 last messages are redacted ones after performing an initial sync 
* #358 : Update the event not found message when clicking on permalink
* #359 : Redacting a video during sending goes wrong 
* #364 : Profile changes shouldn't reorder the room list.

Changes to Matrix Android SDK in 0.5.9 (2016-07-19)
=======================================================

Features:

* The room ids, the room aliases, the event ids are now clickable.

Bugfixes:

* Update the background color of the markdown code.
* #297 : Redact avatar / name update event should remove them from the room history.
* #318 : Some member avatars are wrong.

Changes to Matrix Android SDK in 0.5.8 (2016-07-11)
=======================================================

Improvements:

* Improve file extension retrieving.
* Update to gradle 1.5.0
* Image message in the recents page: display the filename when it is known instead of XX sent an image.

Features:

* Add the requests to add/remove aliases to/from a room aliases.

Bugfixes:

* #262 : The app should not display <img> from HTML formatted_body
* #263 : redactions shouldn't hide auth events (eg bans) from the timeline. they should only hide the human readable bits of content
* #265 : vector-android seems to use display names for join/part when in a room, but not in the latest message display in the rooms list.
* #271 : Accepting an invite does not get full scrollback.

Changes to Matrix Android SDK in 0.5.7 (2016-06-21)
=======================================================

Improvements:

* The room visibility messages are displayed in the room history.
* Do not refresh the turn servers if the HS does not support it.
* RoomState : The events_default and users_default default values are now 0.

Features:

* Add some new room settings management (list in Directory, room access, room history)
* The background sync timeout is now configurable.
* A sleep can be defined between two sync requests.

Bugfixes:

* #206 : There is no space between some avatars (unexpected avatar).
* GA issue : EventTimeLine.mDataHandler is empty whereas it should be.
* onInvalidToken should not be triggered when MatrixError.FORBIDDEN is received.
* #186 : Start chat with a member should use the latest room instead of the first found one.
* Fix a crash with JingleCall class (when the libs are not found on the device).
* The room object was not always initialized when MessagesAdapter is created (tap on a notication whereas the client is not launched).
* Fix a crash when an incoming call is received and the dedicated permissions are not granted.

Changes to Matrix Android SDK in 0.5.6 (2016-06-07)
=======================================================

Bugfixes:

* issue #176 Update the notification text when invited to a chat 
* issue #194 Public room preview : some public rooms have no display name
* issue #180 Some invited emails are stuck (invitation from a non matrix user)
* issue #175 The notifications settings should be dynamically refreshed
* issue #190 Room invitation push rules is disabled for a new account on android but enabled on the webclient interface

Changes to Matrix Android SDK in 0.5.5 (2016-06-03)
=======================================================

Improvements:

* The "table" markdown were badly displayed : use the default Html render
* Remove useless roomSummary error traces (not supported event type)
* Add missing fields in PublicRoom

Features:

* Add ignore users feature.
* Add an API to retrieve the pusher
* Add the room preview management

Bugfixes:

* Fixes several crashes reported by GA.
* Incoming call did not trigger any pushes.

Changes to Matrix Android SDK in 0.5.4 (2016-05-11)
=======================================================

Improvements:

* Add a method to retrieve the SDK version programmatically.
* Add an error callback in the media downloader.
* Improve the room history back pagination management.
* Add method to customize the highlighted pattern in a message.
* Refresh automatically the user account information to avoid having staled one.
* Mark as sent a message when the SEND request succeeds (do not wait anymore the server acknowledge).
* Simplify the room messages layout.
* Add Room.isEventRead to tell if an event has been read.
* Highlight a message if its content fullfills a push rule.
* The room member events are not anymore counted as unread messages
* The messages resending code is factorized in MatrixMessagesListFragment.
* Improve the message html display.
* Warn the application when the credentials are not anymore valid.
* Fix some memory leaks
* Improve the room activity rendering
* Room member events should not be displayed with sender.
* Increase the image thumbnail.

Features:

* Add the currently_active field to User.
* The messages search is now done on server side.
* Add the email login support.
* Add the message context management.
* Add the 3rd party invitation
* Add the markdown support.
* Add the new registration process support.
* Add the emails binding
* Add reset password

Bugfixes:

* The bing rules were sometines not initialized after the application launch.
* SYAND-90 The very first pagination jumps the scroll bar.
* The room spinner was sometime stuck.
* The presense was sometimes invalid.
* MXMediaCache : delete the destinated file if it already exists.
* The back pagination was sometimes stuck after a network error.
* Texts sizes are now defined in SD instead of DP.
* The media message sending did not work properly when the application was in background.
* Fix an issue when a room is left, joined, left and joined again.
* The account info was sometimes resetted after receiving a membership event.
* The filestore was not really cleared after a logout.
* Fix an infinite back pagination while rotating the device.
* Fix a crash when jingle_peerconnection.so is not found.
* The network connection listener did not manage properly the data connection lost.


Changes to Matrix Android SDK in 0.5.3 (2016-02-16)
=======================================================

Improvements:

 * The read receipts are displayed for outgoing and incoming messages.
 * The room members search methods have been improved.
 * The user account data is updated at application launch and resume to speed up account update.
 * The server sync methods are not anymore called in the IU thread.
 * Updates to support the renamed JSON fields (server update).
 * Reduce the number of room backpagination requests when reaching the room history top.

Features:

 * Add new server synchronisation.
 * Add room tags support.
 * Add the mute room notifications methods.
 * Add the remote text search method. 

Bugfixes:

 * Some member avatars were not properly retrieved.
 * The read receipts were not properly saved.
 * The room loading spinner was sometimes stuck when joining a room.
 * Some redacted events were wrongly displayed in the recents (e.g. John:null).
 * Do not try to download an invalid media at each room refresh.
 * A full sync was triggered after failing to send some messages.
 * Fix a null pointer while refresh the messages fragment.
 * Some redacted events were displayed as echoed one (light gray).
 * Fixed some leave - join - leave - join issues.

Changes to Matrix Android SDK in 0.5.2 (2015-11-20)
===================================================

Improvements:

 * Now supports setting a default alias for rooms
 * Rooms can now clear or set ACLs for scrollback 
 * Better SSL support for older devices
 * Improved the recent events display
 * Improved scrolling and update after screen rotation

Features:

 * Read receipts!
 * Added refresh_token support

Bug fixes:

 * Fixed a case where the user got randomly logged out
 * Fixed echo during Android<->Android VOIP calls 

Changes in Matrix Android SDK in 0.5.1 (2015-09-30)
===================================================

Improvements:

 * Add support of file:// in mediaCacheFile.
 * Many UI classes are more customisable (click management, UI fields…).
 * The catchup time should be shorter.
 * The room catchup can be performed while search a pattern.
 * MXFileStore : some files are zipped to reduce the used storage space and to reduce saving time.
 * MXFileStore : Saving thread is now a low priority thread.

Features:

 * Add video and location messages support
 * Add self signed cert support.


Bug fixes:

 * The event lifetime parameter was not checked.
 * The application used to crash while starting a voice/video with a device with no camera or no front camera.
 * Many crashes while logging out.

Changes in Matrix Android SDK in 0.4.4 (2015-09-07)
===================================================

Improvements:

 * Add assert to avoid using released session
 * The RespAdapter callbacks are called in try/catch block to avoid crashing the application.
 * Get thumbnail bitmap file from URL.
 * Share the lastactive delay to string method.
 * Ignore presence events until the initial presences refresh is done.
 * GCM registration : Add the append field management.
 * Add a message header to the room items.
 * The network events are not anymore managed with the pause/unpause commands.
 * Reduce the number of messageAdapter refreshes.
 * The text selection in a chat message is disabled to avoid flickering with long taps. 
 * Allow click on any textual event to copy its content.
 * Update the transaction id for unsent messages.
 * Increase the max number of events stored by room to avoid trigger network requests.
 * room::requestHistory provides 20 events per requests. Room class buffers the storage events to avoid having a huge bunch of events.
 * Improve the storage events management.

Features:

 * Voice/Video call management.

Bug fixes:

 * The displayname was not initialized if the settings page was not opened once.
 * Add mFileStoreHandler sanity check (GA issues).
 * Highlight messages with displayname / userID in room instead of using the push rules.
 * Fix a GA crash while listing the public rooms.
 * Fix a GA crash while listing room members list.
 * Fix a GA crash with caseInsensitiveFind use (empty string case).
 * Fix a GA crash when maxPowerLevel is set to 0.
 * The rooms deletion use to crash the application in some race conditions.
 * The room joining was not properly dispatched when done from another device.
 * The avatar and displayname udpates were not properly saved.
 * The messages are sent with PUT instead of POST to avoid duplicated messages.
 * In some race conditions, the user profile was not properly updated.
 * SYAND-95 Tap on displayname to insert into textbox as poor's man autocomplete
 * SYAND-102 Accepted room invites not properly resolved.


Changes in Matrix Android SDK in 0.4.3 (2015-07-07)
===================================================

Improvements:

 * Display the members presence in the chat activity.


Bug fixes:

 * The 0.4.2 update used to display an empty history.


Changes in Matrix Android SDK in 0.4.2 (2015-07-06)
===================================================

Improvements:

 * Improve the room members listing (it used to be very slow on huge rooms like Matrix HQ).
 * Display the server error messages when available.
 * Multi servers management.
 * Update to the latest robolectric.
 * Add filename param into the media post request to have a valid name while saving with the web client.


Features:

 * Bing rules can now be updated on the client.

Bug fixes:

 * Some rooms were not joined because the roomIds were URL encoded.
 * SYAND-91 : server is not federating - endless load of public room list.
 * Back pagination was sometimes broken with “Invalid token” error. The client should clear the application cache (settings page).
 * The application used to crash when there was an updated of room members meanwhile others members listing action.
 * Thread issue in MXFileStore.

Changes in Matrix Android SDK in 0.4.1 (2015-06-22)
===================================================

Improvements:

 * Automatically resend failed media.

Bug fixes:

 * The matrixMessagesFragment was not properly restarted after have been killed by a low memory.
 * The emotes were not properly displayed.
 * The dataHandler field was not set for "myUser" so displayName update was not properly managed.


Changes in Matrix Android SDK in 0.4.0 (2015-06-19)
===================================================

The SDK and the console application are now split into two git projects.

https://github.com/matrix-org/matrix-android-sdk : The matrix SDK
https://github.com/matrix-org/matrix-android-console : The console application.
Thus, it would be easier to implement a new application.


Improvements:

 * Move AutoScrollDownListView from console to the SDK.
 * Image resizing : use inSampleSize instead of decompressing the image in memory.
 * The image cache should not stored large and very large images.
 * Rotate image with exif if the device has enough memory.
 * Enable largeHeap to be able to manage large images.
 * Move ImageUtils from console to the SDK.
 * Each account has its own media directory (except the member thumbnails).
 * Update the media file name computation to ensure its uniqueness.
 * The media download & upload progress is more linear.
 * Remove the presence and typing events while processing the first events request after application launch.
 * Add onLiveEventsChunkProcessed callback : it is triggered when a bunch of events is managed.
 * IconAndTextAdapter customization. 

Features:

 * Add MXFileStore : The data is now saved in a filesystem cache. It improves the application launching time.
                     The sent messages are also stored when the device is offline.
 * Add GCM registration to a third party server.


Bug fixes:

 * The media download could be stuck on bad/slow network connection.
 * On kitkat or above, the image thumbnails were not properly retrieved.
 * SYAND-80 : image uploading pie chart lies.


Changes in Matrix Android SDK in 0.3.1 (2015-04-24)
===================================================

-----
 SDK
-----
Improvements:

 * Move RoomSummaryAdapter from the application  to the SDK.
 * Move RoomMembersAdapters from the application to the SDK..
 * Large file upload did not warn the user that the media was too large.
 * Do not restart the events listener each 10s if there is no available network. Wait that a network connection is retrieved.

Features:

 * Add multi-accounts management.

Bug fixes:

 * Some unsent messages were not properly automatically resent.
 * The content provider did not provide the mimetype.
 * The application used to randomly crashed on application when there was some network issues.
 * The duplicated member events were not removed;
 * Live state : the left/banned thumbnails were lost.
 * Join a room on the device did not warn the application when the initial sync was done.

-----------------
 Matrix Console
-----------------
Improvements:

 * Re-order the room actions : move from a sliding menu to a standard menu.
 * Do not refresh the room when the application is in background to reduce battery draining.
 * The notice messages are merged as any other messages.
 * Re-order the members list (join first, invite, leave & ban).

Features:

 * Applications can share media with Matrix Console with the "<" button.
 * Matrix console can share media with third party applications like emails.
 * A message can be forwarded to an existing room or to a third party application.
 * The images are not anymore automatically saved when displayed in fullscreen : there is a new menu when tapping on the message. (The media mud have been downloaded once).
 * Add multi-accounts management. Create/Join a room require to select an account.
 * Some push notifications were not triggered when the application was in background.

Bug fixes:

 * A selected GIF image was transformed into a JPG one.
 * The room name was sometimes invalid when the user was invited.
 * SYAND-68 : No hint on display name in settings
 * SYAND-69 : Avatar section in settings
 * SYAND-71 : Cannot view message details of a join.
 * SYAND-72 When an user updates their avatar, the timeline event for the change should reflect the update. 
 * The room cached data was not removed after leaving it.
 * The member display name did not include the matrix Id if several members have the same display name.
 * On some devices, invite members by matrix ID did not work properly because some spaces are automatically appended after a semicolon.


Changes in Matrix Android SDK in 0.3.0 (2015-04-10)
===================================================

-----
 SDK
-----
Improvements:

 * Any request is automatically resent until it succeeds (with a 3 minutes timeline).
 * Remove the dataHandler listeners when logging out to avoid getting unexpected callback call.

-----------------
 Matrix Console
-----------------
Improvements:

 * Add the image watermarks
 * Display the members count in the members list.
 * Can invite several users from the known members list or from their user ids.
 * Hide the image icon until it is fully loaded.
 * Add the hardware search button management (e.g. motorola RAZR).
 * Improve many dialogs (room creation, invitation..).
 * Display leaving rooms.
 * Can send several files at once.
 * Make GCM receiver display notifications and move to own package.
 * Make RoomActivity start the event stream.
 * Add app-global GcmRegistrationManager to register for push services.
 * The bug report contains more details.
 * Add some sliding menus.
 * Include room name in message notifications.
 * Room name will be picked up if passed to GcmIntentService.
 * Add an inliner image preview before sending the message.
 * Ensure that the login parameters are only on one line.
 * Add basic support for Android Auto.
 * Remove tag from notifications (to maintain current behaviour on phones)
 * Scroll the history to the bottom when opening the keyboard.
 * Remove some tags in the logs to avoid displaying the accesstoken.

Features:

 * Supoort Android Lollipop. 
 * Use the material design support.
 * Add the contacts support.
 * Manage the new push rules.
 * Factors the message adapter and fragments to be able to create some new ones without copying too many code.

Bug fixes:

 * SYAND-46 : Crash on launch on my S4 running Android 4.
 * SYAND-51 : New room subscription did not occur in android app.
 * SYAND-54 : Images should be available in gallery apps.
 * SYAND-55 : share multiple images at once.
 * SYAND-58 : scroll in "Invite known user”.
 * SYAND-60 : ” Leave room" should be renamed when you are the latest user in the room.
 * SYAND-62 : Android doesn't seem to specify width/height metadata for images it sends.
 * SYAND-64 : Room name on recents doesn't update.
 * SYAND-65 : Recent entries when leaving rooms
 * SYAND-66 : Auto-capitalisation is not turned on for the main text entry box.
 * SYAND-67 : Screen doesn't turn on for incoming messages.
 * The unread messages counter was invalid after leaving a room.
 * The client synchronisation was not properly managed when the account was shared on several devices.
 * Fix many application crashes while leaving a chat or logging out.
 * The room summaries were not properly sorted when a message sending failed.
 * Some images were partially displayed.
 * The emotes were drawn in magenta.
 * Stop the events thread asap when logging out and ignore received events.
 * Some unexpected typing events were sent.
 * The time zone updates were not properly managed.

Changes in Matrix Android SDK in 0.2.3 (2015-03-10)
===================================================

-----
 SDK
-----
  
-----------------
 Matrix Console
-----------------
Improvements:

 * Avoid refreshing the home page when it is not displayed.
 * Display a piechart while uploading a media.
 * Refresh the display when some messages are automatically resent (after retrieving a data network connection for example).
 * Update the user rename message to be compliant with the web client.
 * Use the local media files instead of downloading them when they are acknowledged (messages sending).
 * Create a media management class.
 * Display the offline status in the members list.
 * Avoid creating new homeActivity instance when joining a room from member details sheet.
 * The public rooms list are now saved in the bundle state : it should avoid having a spinner when rotated the device.
 * The animated GIFs are now supported.

Features:

 * Add the rate limits error management. The server could request to delay the messages sending because they were too many messages sent in a short time (to avoid spam).
 * Can take a photo to send it.
 * A chat room page is automatically paginated to fill. It used to get only the ten latest messages : it displayed half filled page on tablet.
 * Add the sending failure reason in the message details (long tap on a message, “Message details”).
 * The user is not anymore notified it the push rules are not fulfilled.
 * Add some room settings (Display all events, hide unsupported events, sort members by last seen time, display left members, display public rooms in the home page).
 * Add various accessibility tweaks.

Bug fixes:

 * The media downloads/uploads were sometimes stuck.
 * The private room creation was broken.
 * SYAND-33 : number of unread messages disappears when entering another room.
 * The RoomActivity creation used to crash when it was cancelled because the Room id param was not provided.
 * The client used to crash when the home server was invalid but started with http.
 * The account creation used to fail if the home server had a trailing slash.
 * SYAND-44 In progress text entry could be saved across crashes.
 * SYAND-38 Inline image viewer in Android app.


Changes in Matrix Android SDK in 0.2.2 (2015-02-27)
===================================================

-----
 SDK
-----

-----------------
 Matrix Console
-----------------
Improvements:

 * Exif management : the uploaded image is rotated according to the exif metadata (if the device has enough free memory).
 * Add a piechart while downloading an image 
 * Add JSON representation of a message (tap on its row, “Message details”
 * The public rooms list is now sorted according to the number of members.

Features:

 * Add configuration and skeleton classes for receiving GCM messages
 * Add REST client for pushers API with add method for HTTP pushers.
 * Add the account creation.

Bug fixes:

 * Reset the image thumbnail when a row is reused.
 * SYAND-30 Notification should be away when entering a room.
 * Some images thumbnails were downloaded several times.
 * Restore the foreground service
 * The media cache was not cleared after logging out.
 * The client crashed when joining #anime:matrix.org.
 * SYAND-29 Messages in delivery status are not seen
 * Some user display names were their matrix IDs.
 * The room name/ topic were invalid when inviting to a room.



Changes in Matrix Android SDK in 0.2.1 (2015-02-20)
===================================================

-----
 SDK
-----

Features:

 * Add a network connection listener.
 * Unsent messages are automatically resent when a network connection is retrieved.

-----------------
 Matrix Console
-----------------
Improvements:

 * There is no more alert dialog when receiving a new message. They are always displayed in the notifications list.
 * Tap on a member thumbnail opens a dedicated.
 * The message timestamps are always displayed. They used to be displayed/hidden when tapping on the other avatar side.
 * The unsent messages were not saved in the store when leaving a room view.
 * Display a spinner while joining / catching up a room.
 * Unsent images can now be resent. They used to be lost.
 * Add "mark all as read" button.
 * Can select text in a message.
 * A room is highlighted in blue if your display name is in the unread messages.
 * Add support to the identicon server (it displayed squared avatar when the member did not define one).
 * The notifications can be enlarged to display the message with more than one line.
 * Replace the notification icon by a matrix one.

Features:

 * Add the command lines support (see the settings page to have the available command list).
 * Add the typing notifications management.
 * SYAND-24 Respond to IMs directly from push.	

Bug fixes:

 * The image upload failed when using G+-Photos app.
 * Correctly set Content-Length when uploading resource in ContentManager.
 * The user profile was never refreshed when opening the settings activity.
 * The push-rules were not refreshed when the application was debackgrounded.
 * The notice messages (e.g. “Bob left…”) are not anymore merged.
 * Unban was displayed instead of “kicked” in the notice events.
 * The room header was not refreshed when joining a room.
 * The notice events were not summarised in the recents view.
 * The image messages were not properly summarized in the recents.
 * Use scale instead of crop to request thumbnails from content API.
 * Size thumbnail in image message dependent on the size of the view.
 * Joining a room used to perform two or three sync requests.
 * The sound parameter of the push notifications was not managed.
 * SYAND-16 : No feedback when failing to login.
 * SYAND-19 : “My rooms” doesn’t display UTF-8 correctly
 * SYAND-25 : Issues showing the home screen with self-build android app.
 * SYAND-26 : can’t highlight words in message.
 
 
Changes in Matrix Android SDK in 0.2.0 (2015-02-09)
===================================================

-----
 SDK
-----

Features:

 * Added basic support for redacted messages.
 * Added bing rules support.

-----------------
 Matrix Console
-----------------
Improvements:

 * Room messages are merged
 * The oneself messages are displayed at screen right side
 * The images are cached to improve UX.
 * Redacted messages support.
 * The rooms list displays the private and the public ones.  
 * Can search a room by name.
 * The unread messages count are displayed.

Features:

 * Add rageshake to submit a bug report

 Bug fixes:
 
 * SYAND-17 Crash on login on master



=======================================================
+        TEMPLATE WHEN PREPARING A NEW RELEASE        +
=======================================================


Changes to Matrix Android SDK in 0.9.X (2019-XX-XX)
=======================================================

Features:
 -

Improvements:
 -

Bugfix:
 -

API Change:
 -

Translations:
 -

Others:
 -

Build:
 -

Test:
 -
