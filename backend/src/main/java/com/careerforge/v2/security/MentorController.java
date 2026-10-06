package com.careerforge.v2.security;

import com.careerforge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/mentor")
public class MentorController {
    public record MessageInput(@NotBlank @Size(max=1000) String message) {}
    private final MentorService mentor;
    private final AuthService auth;
    public MentorController(MentorService mentor,AuthService auth) { this.mentor=mentor; this.auth=auth; }
    @GetMapping
    public Map<String,Object> overview(HttpServletRequest request) { return mentor.overview(auth.current(request)); }
    @PostMapping
    public Map<String,Object> ask(@Valid @RequestBody MessageInput input,HttpServletRequest request) { return mentor.ask(auth.current(request),input.message()); }
    @DeleteMapping("/history")
    public Map<String,String> clear(HttpServletRequest request) { mentor.clear(auth.current(request)); return Map.of("message","Mentor history cleared."); }
}
