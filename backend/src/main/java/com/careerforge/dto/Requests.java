package com.careerforge.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
    private Requests() {}
    public record Login(@NotBlank @Email String email,@NotBlank String password) {}
    public record Registration(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=255) String email,
                               @NotBlank @Size(min=8,max=72) String password) {}
    public record Profile(@NotBlank @Size(max=100) String name,@Size(max=255) String course,
                          @Size(max=255) String college,@Size(max=50) String currentYear,@NotNull Long careerId,
                          @Size(max=255) String timezone) {}
    public record SkillInput(@NotNull Long skillId,@NotBlank String level) {}
    public record Status(@NotBlank String status) {}
    public record ProjectInput(@NotBlank @Size(max=150) String name,@Size(max=2000) String description,
                               @Size(max=255) String technology,@Size(max=1000) String githubUrl,
                               @NotBlank String status) {}
    public record CareerInput(@NotBlank @Size(max=150) String name,@NotBlank @Size(max=2000) String description,
                              @NotEmpty List<@NotNull Long> skillIds) {}
    public record ResourceInput(@NotNull Long skillId,@NotBlank @Size(max=255) String title,
                                @NotBlank @Size(max=1000) String url,@NotBlank @Size(max=50) String type) {}
}
