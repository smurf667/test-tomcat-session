package com.example.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class Servlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String KEY = "visits";

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		response.setHeader("Content-Type", "text/plain");
		final PrintWriter writer = response.getWriter();
		writer.append("Welcome");
		final HttpSession session = request.getSession(true);
		writer.append(visit(session));
		writer.flush();
	}

	private String visit(final HttpSession session) {
		AtomicInteger counter = (AtomicInteger) session.getAttribute(KEY);
		if (counter == null) {
			counter = new AtomicInteger();
			session.setAttribute(KEY, counter);
			return ". This is your first visit.";
		}
		return ". You've been seen " + counter.incrementAndGet() + " times before.";
	}

}
