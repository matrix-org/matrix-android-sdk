Changes in Matrix Android SDK in 0.2.2 (2015-02-27)
===============================================

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




