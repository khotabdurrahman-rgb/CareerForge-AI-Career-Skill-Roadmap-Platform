package com.careerforge.config;

import com.careerforge.v2.security.RequestLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthRateLimitConfig implements WebMvcConfigurer {
    private final RequestLimiter limits;
    private final MeterRegistry metrics;
    private final int loginLimit,registrationLimit;
    private final long loginWindow,registrationWindow;
    public AuthRateLimitConfig(RequestLimiter limits,MeterRegistry metrics,
            @Value("${careerforge.auth.login-limit:20}") int loginLimit,
            @Value("${careerforge.auth.registration-limit:10}") int registrationLimit,
            @Value("${careerforge.auth.login-window-ms:900000}") long loginWindow,
            @Value("${careerforge.auth.registration-window-ms:3600000}") long registrationWindow) {
        if(loginLimit<1 || registrationLimit<1 || loginWindow<1 || registrationWindow<1)
            throw new IllegalArgumentException("Authentication rate limits must be positive");
        this.limits=limits; this.metrics=metrics; this.loginLimit=loginLimit;
        this.registrationLimit=registrationLimit; this.loginWindow=loginWindow;
        this.registrationWindow=registrationWindow;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if(!"POST".equals(request.getMethod())) return true;
                String path=request.getRequestURI().substring(request.getContextPath().length());
                boolean login=path.equals("/api/auth/login");
                String operation=login ? "login" : "registration";
                try {
                    // Use the servlet client address, not an unvalidated forwarding header.
                    limits.check("auth:"+operation+":"+request.getRemoteAddr(),login ? loginLimit : registrationLimit,
                            login ? loginWindow : registrationWindow);
                } catch(ResponseStatusException error) {
                    metrics.counter("careerforge.auth.rate_limited","operation",operation).increment();
                    throw error;
                }
                return true;
            }
        }).addPathPatterns("/api/auth/login","/api/auth/register");
    }
}
