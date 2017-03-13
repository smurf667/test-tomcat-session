package com.example;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.http.HttpServletResponse;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.Cookie;

/**
 * Tests the session handling of the server with a simple servlet, in
 * public and protected scenarios.
 */
public class SessionBehaviorIT {
	
	private static final String SESSION_COOKIE_NAME = "JSESSIONID";
	private static final URL PUBLIC_SERVLET_URL;
	private static final URL PROTECTED_SERVLET_URL;

	static {
		try {
			PUBLIC_SERVLET_URL = new URL("http://localhost:8080/demo/public");
			PROTECTED_SERVLET_URL = new URL("http://localhost:8080/demo/protected");
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void testDemonstratePublicServlet() {
		demonstrate(PUBLIC_SERVLET_URL);
	}

	@Test
	public void testDemonstrateProtectedServlet() {
		demonstrate(PROTECTED_SERVLET_URL);
	}

	@Test
	public void testProtectedServletParallel() {
		testParallel(PROTECTED_SERVLET_URL);
	}

	@Test
	public void testPublicServletParallel() {
		testParallel(PROTECTED_SERVLET_URL);
	}

	private void testParallel(final URL url) {
		final WebClient client = createWebClient();
		final Set<String> ids = Collections.synchronizedSet(new HashSet<>());
		final Cookie cookie = sendRequest.apply(client, url).get();
		ids.add(cookie.getValue());
		
		final int threads = 10;
		final ExecutorService service = Executors.newFixedThreadPool(threads);

		// run a number of threads...
		IntStream
			.range(0, threads)
			.forEach( i -> {
				service.submit( () -> {
					final WebClient current = createWebClient();
					current.getCookieManager().addCookie(cookie);
					ids.add(sendRequest.apply(client, url).get().getValue());
				});
			});
		// wait for all threads to end
		try {
			service.shutdown();
			service.awaitTermination(15, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.err.println("interrupted, shutting down now");
		} finally {
			if (!service.isTerminated()) {
				service.shutdownNow();
			}
		}
		Assertions.assertThat(ids).hasSize(1);
	}

	private void demonstrate(final URL url) {
		final WebClient client = createWebClient();
		final Set<String> sessionIDs = IntStream
			.rangeClosed(1, 4)
			.mapToObj(i -> (String) sendRequest.apply(client, url).get().getValue())
			.collect(Collectors.toSet());
		Assertions.assertThat(sessionIDs).hasSize(1);
	}

	/**
	 * Sets up a client for authentication.
	 * @return a new web client, never <code>null</code>.
	 */
	private WebClient createWebClient() {
		final DefaultCredentialsProvider userCredentials = new DefaultCredentialsProvider();
		userCredentials.addCredentials("user123", "pass123");
		final WebClient webClient = new WebClient();
		webClient.setCredentialsProvider(userCredentials);
		webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		return webClient;
	}

	/**
	 * Sends a request to the application and returns the session cookie.
	 */
	private final BiFunction<WebClient, URL, Optional<Cookie>> sendRequest = (client, url) -> {
		try {
			final WebRequest request = new WebRequest(url);
			final WebResponse response = client.loadWebResponse(request);
			final int sc = response.getStatusCode();
			if (sc == HttpServletResponse.SC_OK) {
				return client
					.getCookies(url)
					.stream()
					.filter( cookie -> SESSION_COOKIE_NAME.equals(cookie.getName()) )
					.findFirst();
			}
			return Optional.empty();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	};

}
