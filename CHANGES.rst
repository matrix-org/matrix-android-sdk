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
 