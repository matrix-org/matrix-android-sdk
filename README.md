matrix-android-sdk
==================

The Matrix SDK for Android wraps the Matrix REST API calls in asynchronous Java methods and provides basic structures for storing and handling data.

It is a Android Studio (gradle) project containing two modules:

 * sdk - The SDK
 * app - The sample app using the SDK

Introduction
------------
The Matrix APIs are split into several categories (see [matrix api]).
The basic usage is to
 1. Log in or register to a home server -> returns the user's credentials
 2. Start a session with the credentials

Logging in
----------
To log in, get an instance of the login API object.
```java
new LoginApiClient("https//matrix.org").loginWithPassword("user", "password", callback);
```
If successful, the callback will provide the user credentials used from then on.

[matrix api]:http://matrix.org/docs/api/client-server/


