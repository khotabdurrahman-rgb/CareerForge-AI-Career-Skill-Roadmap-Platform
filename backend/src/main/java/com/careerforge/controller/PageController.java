package com.careerforge.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@Controller
public class PageController {
    private static final Map<String,String> ROUTES=Map.ofEntries(
            Map.entry("login","login"),Map.entry("register","register"),Map.entry("dashboard","dashboard"),
            Map.entry("skills","skills"),Map.entry("careers","careers"),Map.entry("skill-gap","gap"),
            Map.entry("roadmap","roadmap"),Map.entry("projects","projects"),Map.entry("resources","resources"),
            Map.entry("profile","profile"),Map.entry("admin","admin"));

    @GetMapping({"/login.html","/register.html","/dashboard.html","/skills.html","/careers.html",
            "/skill-gap.html","/roadmap.html","/projects.html","/resources.html","/profile.html","/admin.html"})
    public String page(HttpServletRequest request) {
        String path=request.getRequestURI();
        String name=path.substring(1,path.length()-5);
        return "redirect:/#"+ROUTES.get(name);
    }
}
