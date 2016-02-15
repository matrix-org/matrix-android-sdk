Changes to Matrix Android Console in 0.5.3 (2016-02-16)
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
 * Automatically resend failed medias.

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
 * Each account has its own medias directory (except the member thumbnails).
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
 * Applications can share medias with Matrix Console with the "<" button.
 * Matrix console can share medias with third party applications like emails.
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
 * Create a medias management class.
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
 * The medias cache was not cleared after logging out.
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
 
