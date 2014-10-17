matrix-android-sdk
==================
The [Matrix] SDK for Android wraps the Matrix REST API calls in asynchronous Java methods and provides basic structures for storing and handling data.

It is an Android Studio (gradle) project containing two modules:
 * sdk - The SDK
 * app - The sample app using the SDK

Overview
--------
The Matrix APIs are split into several categories (see [matrix api]).
The basic usage is to
 1. Log in or register to a home server -> get the user's credentials
 2. Start a session with the credentials
 3. Directly start making matrix API calls -or- Set up a matrix data handler for higher-level interaction.

Logging in
----------
To log in, use an instance of the login API client.
```java
new LoginApiClient("https//matrix.org").loginWithPassword("user", "password", callback);
```
If successful, the callback will provide the user credentials to use from then on.

Starting the matrix session
---------------------------
The session represents one user's session with a particular home server. There can potentially be multiple sessions for handling multiple accounts.
```java
MXSession session = new MXSession(credentials);
```
This sets up a minimal session for interacting with the home server; It gives the most control but at the lowest possible level.

API calls are then done through the different API clients provided by the session.
Getting the list of members of a chat room would look something like this:
```java
session.getRooomApiClient().getRoomMembers(<roomId>, callback);
```

### Session with data handler
The recommended usage, however, is to set up the session with a data handler:
```java
MXSession session = new MXSession(credentials, dataHandler);
```
The data handler provides a layer to help manage matrix input and output.
 * It handles common API calls
 * Stores the data in its storage layer
 * Provides the means for an app to get callbacks for data changes


 ```java
MXDataHandler dataHandler = new MXDataHandler(new MXMemoryStore());
```
creates a data handler with the default in-memory storage implementation.

The event stream
----------------
One important part of any Matrix-enabled app will be listening to the event stream, the live flow of events.
This is done by using one particular API call repeatedly in the events thread.

```java
session.startEventStream();
```
starts the events thread and sets it to communicate with the previously defined data handler.
If working without a data handler, a custom listener can also be provided.

License
-------
Apache 2.0

See the sample app and Javadoc for more details.

[Matrix]:http://matrix.org
[matrix api]:http://matrix.org/docs/api/client-server/