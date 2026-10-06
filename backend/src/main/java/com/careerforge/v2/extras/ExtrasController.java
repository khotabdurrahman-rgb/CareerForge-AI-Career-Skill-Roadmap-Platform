package com.careerforge.v2.extras;

import com.careerforge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.careerforge.v2.extras.ExtrasDtos.*;

@RestController
@RequestMapping("/api")
public class ExtrasController {
    private final AuthService auth;
    private final ResumeService resumes;
    private final ResumePdfService pdf;
    private final ComparisonService comparisons;
    private final RecommendationService recommendations;
    private final ProgressService progress;
    public ExtrasController(AuthService auth,ResumeService resumes,ResumePdfService pdf,ComparisonService comparisons,
        RecommendationService recommendations,ProgressService progress) {
        this.auth=auth; this.resumes=resumes; this.pdf=pdf; this.comparisons=comparisons;
        this.recommendations=recommendations; this.progress=progress;
    }
    @GetMapping("/resume")
    public ResumeBundle resume(HttpServletRequest request) { return resumes.get(auth.current(request)); }
    @PutMapping("/resume")
    public ResumeBundle resume(@Valid @RequestBody ResumeFields fields,HttpServletRequest request) { return resumes.put(auth.current(request),fields); }
    @GetMapping(value="/resume/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(HttpServletRequest request) {
        byte[] bytes=pdf.render(resumes.get(auth.current(request)));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=careerforge-resume.pdf")
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);
    }
    @GetMapping("/careers/compare")
    public Comparison compare(@RequestParam Long left,@RequestParam Long right,HttpServletRequest request) {
        return comparisons.compare(auth.current(request),left,right);
    }
    @GetMapping("/recommendations")
    public List<Recommendation> recommendations(HttpServletRequest request) { return recommendations.list(auth.current(request)); }
    @PostMapping("/recommendations/{id}/save")
    public Recommendation save(@PathVariable String id,HttpServletRequest request) { return recommendations.bookmark(auth.current(request),id,true); }
    @DeleteMapping("/recommendations/{id}/save")
    public Recommendation unsave(@PathVariable String id,HttpServletRequest request) { return recommendations.bookmark(auth.current(request),id,false); }
    @PostMapping("/recommendations/{id}/portfolio")
    public ProjectView portfolio(@PathVariable String id,HttpServletRequest request) { return recommendations.portfolio(auth.current(request),id); }
    @GetMapping("/progress")
    public Progress progress(HttpServletRequest request) { return progress.history(auth.current(request)); }
}
