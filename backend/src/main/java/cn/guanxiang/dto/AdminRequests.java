package cn.guanxiang.dto;

import jakarta.validation.constraints.*;

public final class AdminRequests {
    private AdminRequests() {}
    public record Login(@NotBlank @Size(max=80) String username,@NotBlank @Size(max=72) String password) {}
    public record Test(@Pattern(regexp="text|vision") @NotNull String kind) {}
}
