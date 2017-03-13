package com.example.tc7;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.ServletException;

import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

public class SessionIDFixerValve extends ValveBase {

	@Override
	public void invoke(final Request request, final Response response) throws IOException, ServletException {
		final boolean hadSession = request.getSessionInternal(false) != null;
		try {
			// proceed to next value...
			getNext().invoke(request, response);
		} finally {
			final Session session = request.getSessionInternal(false);
			if (!hadSession && session != null) {
				// we did not have a session, but have one now:
				// cache the principal in the session to avoid
				// issuing a new session ID value on the next request
				// by the client of this request
				final Principal principal = request.getPrincipal();
				if (principal != null) {
					// this "only" makes sense if org.apache.catalina.authenticator.AuthenticatorBase.getCache() == true - unclear how to determine this setting?
					session.setPrincipal(principal);
					session.setAuthType(request.getAuthType());
				}
			}
		}
	}

}
