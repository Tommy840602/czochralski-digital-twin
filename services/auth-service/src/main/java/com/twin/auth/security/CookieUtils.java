package com.twin.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

public final class CookieUtils {

    private CookieUtils() {}

    public static Optional<Cookie> get(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (c.getName().equals(name)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public static void add(HttpServletResponse response, String name, String value, int maxAgeSec) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAgeSec);
        response.addCookie(cookie);
    }

    public static void delete(HttpServletRequest request, HttpServletResponse response, String name) {
        get(request, name).ifPresent(c -> {
            Cookie cleared = new Cookie(name, "");
            cleared.setPath("/");
            cleared.setHttpOnly(true);
            cleared.setMaxAge(0);
            response.addCookie(cleared);
        });
    }
}
