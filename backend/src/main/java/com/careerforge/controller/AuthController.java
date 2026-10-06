package com.careerforge.controller;

import com.careerforge.dto.Requests.*;
import com.careerforge.model.User;
import com.careerforge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/auth/register")
    public User register(@Valid @RequestBody Registration input,HttpServletRequest request) { return auth.register(input,request); }
    @PostMapping("/auth/login")
    public User login(@Valid @RequestBody Login input,HttpServletRequest request) { return auth.login(input,request); }
    @GetMapping("/me")
    public User me(HttpServletRequest request) { return auth.current(request); }
    @PostMapping("/auth/logout")
    public Map<String,String> logout(HttpServletRequest request) {
        var session=request.getSession(false); if(session!=null) session.invalidate();
        return Map.of("message","Signed out");
    }
}
