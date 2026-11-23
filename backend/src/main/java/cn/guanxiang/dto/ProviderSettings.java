package cn.guanxiang.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ProviderSettings(@NotNull @Valid ProviderConnection text,@NotNull @Valid ProviderConnection vision,boolean separateVision,
        @Min(256) @Max(8192) int maxTokens,@Min(10) @Max(120) int timeoutSeconds,
        @NotNull @Pattern(regexp="max_tokens|max_completion_tokens") String tokenParameter) {}
