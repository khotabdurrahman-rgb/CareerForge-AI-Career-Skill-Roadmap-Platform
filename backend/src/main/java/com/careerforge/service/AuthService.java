package com.careerforge.service;

import com.careerforge.dto.Requests.*;
import com.careerforge.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@Service
public class AuthService {
    private final CareerService data;
    private final com.careerforge.v2.security.RecoveryService recovery;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    public AuthService(CareerService data,com.careerforge.v2.security.RecoveryService recovery) { this.data=data; this.recovery=recovery; }
    public String hash(String password) { return encoder.encode(password); }
    public User current(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long id))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in to continue");
        User user=data.user(id);
        if(!(session.getAttribute("sessionVersion") instanceof Long version) || version!=user.sessionVersion) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in again.");
        }
        return user;
    }
    public User admin(HttpServletRequest request) {
        User user=current(request);
        if(!"ADMIN".equals(user.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required");
        return user;
    }
    private User signIn(User user,HttpServletRequest request) {
        var previous=request.getSession(false);
        if(previous!=null) previous.invalidate();
        var session=request.getSession(true);
        session.setAttribute("userId",user.id); session.setAttribute("sessionVersion",user.sessionVersion); return user;
    }
    @Transactional
    public User register(Registration input,HttpServletRequest request) {
        String email=input.email().trim().toLowerCase(Locale.ROOT);
        if(data.users().existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw CareerService.bad("Password must be at most 72 UTF-8 bytes");
        User user=new User(); user.name=input.name().trim(); user.email=email; user.passwordHash=hash(input.password());
        user.careerId=data.careers().findAll().stream().findFirst().orElseThrow(()->CareerService.bad("No career paths are configured")).id;
        data.users().save(user); data.generateRoadmap(user);
        recovery.request(email,"VERIFY");
        return signIn(user,request);
    }
    public User login(Login input,HttpServletRequest request) {
        User user=data.users().findByEmail(input.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect email or password"));
        if(!encoder.matches(input.password(),user.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect email or password");
        return signIn(user,request);
    }
}
