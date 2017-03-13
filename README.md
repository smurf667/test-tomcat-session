# Stable session ID issue

This web application reproduces an issue with the stability of the session ID for initial requests in a pre-authenticated scenario (see this [bug report](https://bz.apache.org/bugzilla/show_bug.cgi?id=60854)).
In the test case the first request to the application authenticates the user and creates a new session. A JSESSIONID cookie is returned. A client sending this cookie back will get _another_ JSESSIONID cookie in the response. This is not expected.

To simply access the application run

	mvn tomcat7:run

The application is available at [http://localhost:8080/demo/protected](http://localhost:8080/demo/protected) and you can log in with `user123` and `pass123` as the password.

To reproduce the issue run the integration test by executing

	mvn verify

Tests demonstrating the issue fail: [SessionBehaviorIT.java](src/test/java/com/example/SessionBehaviorIT.java)

The simplest test is `testDemonstrateProtectedServlet` which shows the session cookie behavior when several sequential requests are made. We expect the session ID to be stable and to not change in this scenario.

## What is the problem?

The default settings for `org.apache.catalina.authenticator.AuthenticatorBase` are `cache=true` (cache the principal object with the session) and `changeSessionIdOnAuthentication=true` (change session ID upon authentication, to prevent session fixation attacks). However, in a scenario where a first (cookieless) request triggers both authentication and session creation, a _subsequent_ request with the session ID cookie triggers as session ID change.

Details: The `org.apache.catalina.authenticator.AuthenticatorBase` caches the principal with the session with the `cache=true` setting (default). On the very first request, the client obviously has no session cookie. No session exists, thus the principal of the request will not be cached with a session. But, in this request the application creates a session. The session is created and the session cookie is sent to the client.
On the next request, the client sends the session cookie. The session is found, but no principal is cached with it. This causes the Tomcat code to call this sequence (it seems to think that authentication just occurred, thus triggering a session ID change):

	...
	at org.apache.catalina.connector.Request.changeSessionId(Request.java)
	at org.apache.catalina.authenticator.AuthenticatorBase.register(AuthenticatorBase.java)
	at org.apache.catalina.authenticator.BasicAuthenticator.authenticate(BasicAuthenticator.java)
	...

Turning off caching actually causes Tomcat to issue new session IDs _for each request_, not sure if that is really intended (probably not).

## How to fix?

The problem is with attempting to cache the principal with the session at a time when the session has not been created yet inside the current request, but will be at a later time.

We prototyped a valve that tries to handle the situation, _assuming caching of principal is used_. See [SessionIDFixerValve.java](src/test/java/com/example/tc7/SessionIDFixerValve.java)

Such a valve is probably only a circumvention. The situation handled by the valve should probably be dealt with in this manner in `AuthenticatorBase` directly (if caching the principal is enabled).

For valve configurations, see [server.xml](src/test/tomcat7/server.xml). The checked in configuration represents the defaults Tomcat ships with. 