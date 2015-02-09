Changes in Matrix Android SDK in 0.2.2 (2015-02-09)
===============================================

-----
 SDK
-----
Improvements:
 * MXFileStore stores data on a separated thread to avoid blocking the UI thread.
 * MXRestClient: Callback blocks in all MXRestClient methods are now optional.
 * MXEvent: Cleaned up exposed properties and added a description for each of them.
 
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

 Bug fixes:
 * SYAND-17 Crash on login on master