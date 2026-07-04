package com.twin.furnace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {

        try {
            String username = req.getHeader("X-User-Name");

            if (username != null && !username.isBlank()) {
                Set<SimpleGrantedAuthority> auths = new LinkedHashSet<>();

                addAll(auths, req.getHeader("X-User-Roles"), "ROLE_");
                addAll(auths, req.getHeader("X-User-Perms"), "");

                UsernamePasswordAuthenticationToken token =
                        new UsernamePasswordAuthenticationToken(
                                username.trim(),
                                null,
                                auths
                        );

                SecurityContextHolder.getContext().setAuthentication(token);
            }

            chain.doFilter(req, res);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void addAll(Set<SimpleGrantedAuthority> auths, String csv, String prefix) {
        if (csv == null || csv.isBlank()) {
            return;
        }

        for (String s : csv.split(",")) {
            String v = s.trim();

            if (!v.isEmpty()) {
                auths.add(new SimpleGrantedAuthority(prefix + v));
            }
        }
    }
}
