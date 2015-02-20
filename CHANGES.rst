Changes in Matrix Android SDK in 0.2.1 (2015-02-20)
===============================================

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







