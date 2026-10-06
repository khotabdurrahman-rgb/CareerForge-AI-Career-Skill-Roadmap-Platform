package com.careerforge.controller;

import com.careerforge.dto.Requests.*;
import com.careerforge.model.*;
import com.careerforge.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final CareerService data;
    private final AuthService auth;
    public AdminController(CareerService data,AuthService auth) { this.data=data; this.auth=auth; }
    @GetMapping("/users")
    public List<User> users(HttpServletRequest request) { auth.admin(request); return data.users().findAll(); }
    @PostMapping("/careers")
    public Career addCareer(@Valid @RequestBody CareerInput input,HttpServletRequest request) { auth.admin(request); return data.saveCareer(null,input); }
    @PutMapping("/careers/{id}")
    public Career updateCareer(@PathVariable Long id,@Valid @RequestBody CareerInput input,HttpServletRequest request) { auth.admin(request); return data.saveCareer(id,input); }
    @DeleteMapping("/careers/{id}")
    public Map<String,String> deleteCareer(@PathVariable Long id,HttpServletRequest request) {
        auth.admin(request); data.removeCareer(id); return Map.of("message","Career removed");
    }
    @PostMapping("/resources")
    public Resource resource(@Valid @RequestBody ResourceInput input,HttpServletRequest request) { auth.admin(request); return data.saveResource(null,input); }
    @PutMapping("/resources/{id}")
    public Resource updateResource(@PathVariable Long id,@Valid @RequestBody ResourceInput input,HttpServletRequest request) { auth.admin(request); return data.saveResource(id,input); }
    @DeleteMapping("/resources/{id}")
    public Map<String,String> deleteResource(@PathVariable Long id,HttpServletRequest request) {
        auth.admin(request); data.removeResource(id); return Map.of("message","Resource removed");
    }
}
