package com.careerforge.v2.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/auth")
public class RecoveryController {
    public record EmailInput(@NotBlank @Email @Size(max=255) String email) {}
    public record TokenInput(@NotBlank @Size(max=200) String token) {}
    public record ResetInput(@NotBlank @Size(max=200) String token,@NotBlank @Size(min=8,max=72) String password) {}
    private final RecoveryService recovery;
    private final RequestLimiter limits;
    @Value("${careerforge.seed-demo:true}") private boolean demoEnabled;
    public RecoveryController(RecoveryService recovery,RequestLimiter limits) { this.recovery=recovery; this.limits=limits; }
    @GetMapping("/config")
    public Map<String,Object> config() {
        var result=new HashMap<String,Object>(recovery.config());
        result.put("demoEnabled",demoEnabled);
        return result;
    }
    @PostMapping("/forgot-password")
    public Map<String,String> forgot(@Valid @RequestBody EmailInput input,HttpServletRequest request) {
        limits.check("recovery:"+request.getRemoteAddr(),5,900000); recovery.request(input.email(),"RESET"); return RecoveryService.neutral();
    }
    @PostMapping("/verification")
    public Map<String,String> verification(@Valid @RequestBody EmailInput input,HttpServletRequest request) {
        limits.check("recovery:"+request.getRemoteAddr(),5,900000); recovery.request(input.email(),"VERIFY"); return RecoveryService.neutral();
    }
    @PostMapping("/reset-password")
    public Map<String,String> reset(@Valid @RequestBody ResetInput input,HttpServletRequest request) {
        limits.check("tokens:"+request.getRemoteAddr(),15,900000); recovery.reset(input.token(),input.password());
        var session=request.getSession(false); if(session!=null) session.invalidate();
        return Map.of("message","Password updated. Please sign in again.");
    }
    @PostMapping("/verify-email")
    public Map<String,String> verify(@Valid @RequestBody TokenInput input,HttpServletRequest request) {
        limits.check("tokens:"+request.getRemoteAddr(),15,900000); recovery.verify(input.token());
        return Map.of("message","Email verified.");
    }
}
