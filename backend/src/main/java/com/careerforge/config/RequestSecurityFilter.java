package com.careerforge.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSecurityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
        if(request.getServletPath().startsWith("/api/") || request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")) {
            response.setHeader("Cache-Control","no-store");
            String method=request.getMethod();
            if(!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
                String origin=request.getHeader("Origin");
                boolean forbidden="cross-site".equals(request.getHeader("Sec-Fetch-Site"));
                if(origin!=null) {
                    try {
                        URI source=URI.create(origin);
                        int port=source.getPort()<0 ? ("https".equals(source.getScheme()) ? 443 : 80) : source.getPort();
                        forbidden|=!request.getScheme().equals(source.getScheme())
                                || !request.getServerName().equalsIgnoreCase(source.getHost())
                                || request.getServerPort()!=port;
                    } catch(IllegalArgumentException e) { forbidden=true; }
                }
                if(forbidden) {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Cross-site requests are not allowed\"}");
                    return;
                }
            }
        }
        chain.doFilter(request,response);
    }
}
