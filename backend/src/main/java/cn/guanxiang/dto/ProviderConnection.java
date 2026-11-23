package cn.guanxiang.dto;

import jakarta.validation.constraints.*;

public record ProviderConnection(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=500) String baseUrl,
        @NotNull @Size(max=2000) String apiKey,@NotBlank @Size(max=150) String model) {}
