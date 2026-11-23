package cn.guanxiang.common;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Problem.class)
    org.springframework.http.ResponseEntity<?> problem(Problem e) {
        return org.springframework.http.ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalid(Exception e) { return Map.of("code", "INVALID_INPUT", "message", "请检查输入格式、长度和必填项"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    Map<String, String> large() { return Map.of("code", "IMAGE_TOO_LARGE", "message", "图片不能超过 10 MB"); }
    @ExceptionHandler(Exception.class)
    org.springframework.http.ResponseEntity<?> unknown(Exception e) {
        if(e instanceof org.springframework.web.ErrorResponse framework)
            return org.springframework.http.ResponseEntity.status(framework.getStatusCode()).body(Map.of("code","REQUEST_REJECTED","message","请求无法处理，请检查地址、参数与文件格式"));
        return org.springframework.http.ResponseEntity.internalServerError().body(Map.of("code", "INTERNAL_ERROR", "message", "服务暂时不可用，请稍后重试"));
    }
}
