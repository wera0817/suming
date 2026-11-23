package cn.guanxiang.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ConsultationRequests {
    private ConsultationRequests() {}
    public record Numbers(@NotNull @Size(min=3,max=3) List<@NotNull @Min(1) @Max(999999999) Long> numbers) {}
    public record Create(@NotBlank @Size(max=2000) String question,@NotNull @Size(max=4000) String background,@NotNull @Valid Numbers chart) {}
    public record Follow(@Size(max=2000) String message) {}
}
