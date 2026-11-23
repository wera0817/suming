package cn.guanxiang.dto;

import jakarta.validation.constraints.*;

public record KnowledgeDoc(String id,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=20000) String body,
        @NotNull @Pattern(regexp="meihua|palm|face") String category,@NotBlank @Size(max=100) String school,
        @NotBlank @Size(max=500) String source,@NotBlank @Size(max=200) String edition,
        @NotBlank @Size(max=500) String rights,boolean verified,boolean enabled) {}
