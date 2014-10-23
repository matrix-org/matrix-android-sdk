matrix-android-sdk
==================
The [Matrix] SDK for Android wraps the Matrix REST API calls in asynchronous Java methods and provides basic structures for storing and handling data.

It is an Android Studio (gradle) project containing two modules:

 * sdk - The SDK
 * app - The sample app using the SDK

Overview
--------
The Matrix APIs are split into several categories (see [matrix api]).
Basic usage is:

 1. Log in or register to a home server -> get the user's credentials
 2. Start a session with the credentials
 3. Start listening to the event stream
 3. Make matrix API calls

Logging in
----------
To log in, use an instance of the login API client.

```java
new LoginApiClient("https://matrix.org").loginWithPassword("user", "password", callback);
```

If successful, the callback will provide the user credentials to use from then on.

Starting the matrix session
---------------------------
The session represents one user's session with a particular home server. There can potentially be multiple sessions for handling multiple accounts.

```java
MXSession session = new MXSession(credentials);
```

sets up a session for interacting with the home server.

The session gives access to the different APIs through the REST clients:

```session.getEventsRestClient()``` for the events API

```session.getProfileRestClient()``` for the profile API

```session.getPresenceRestClient()``` for the presence API

```session.getRoomsRestClient()``` for the rooms API

For the complete list of methods, please refer to the [Javadoc].

**Example**
Getting the list of members of a chat room would look something like this:

```java
session.getRoomsRestClient().getRoomMembers(<roomId>, callback);
```

The same session object should be used for each request. This may require use
of a singleton, see the ```Matrix``` singleton in the ```app``` module for an
example.

The event stream
----------------
One important part of any Matrix-enabled app will be listening to the event stream, the live flow of events.
This is done by using:

```java
session.startEventStream();
```

This starts the events thread and sets it to send events to a default listener.
It may be useful to use this in conjunction with an Android ```Service``` to
control whether the event stream is running in the background or not.

The data handler
----------------
The data handler provides a layer to help manage data from the events stream:

 * Handles events from the events stream
 * Stores the data in its storage layer
 * Provides the means for an app to get callbacks for data changes

```java
MXDataHandler dataHandler = new MXDataHandler(new MXMemoryStore());
```

creates a data handler with the default in-memory storage implementation.

#### Setting up the session

```java
MXSession session = new MXSession(credentials, dataHandler);
```

#### Starting the event stream

```java
session.startEventStream();
```

#### Registering a listener
To be informed of data updates due to the processing of events, the app needs to implement an event listener.

```java
session.getDataHandler().addListener(eventListener);
```

This listener should subclass ```MXEventListener``` and override the methods as needed:

```onUserPresenceUpdated(user) ```

```onMessageReceived(room, event) ```

```onEventReceived(Event event) ```

```onRoomStateUpdated(room, event, oldVal, newVal) ```

```onInitialSyncComplete() ```

**See the sample app and Javadoc for more details.**

License
-------
Apache 2.0

[Matrix]:http://matrix.org
[matrix api]:http://matrix.org/docs/api/client-server/
