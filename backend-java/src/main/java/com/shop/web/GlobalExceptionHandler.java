package com.shop.web;

import com.shop.errors.ApiException;
import com.shop.errors.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一异常处理，响应结构与 Python 版一致：
 * {"error": {"code": "...", "message": "...", "details": [...]}}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", e.getCode());
        error.put("message", e.getMessage());
        if (e instanceof ValidationException) {
            error.put("details", ((ValidationException) e).getDetails());
        }
        return ResponseEntity.status(e.getStatus()).body(singletonError(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return validation("请求体格式不正确", field("body", "请求体格式不正确", "type_error"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return validation("缺少必填参数：" + e.getParameterName(),
                field("query", e.getParameterName(), "value_error.missing"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation("参数类型不正确：" + e.getName(),
                field("query", e.getName(), "type_error"));
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(
            org.springframework.web.multipart.support.MissingServletRequestPartException e) {
        return validation("缺少上传文件：file", field("body", "file", "value_error.missing"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSize(MaxUploadSizeExceededException e) {
        ApiException error = new ApiException(400, "IMAGE_TOO_LARGE", "图片大小不能超过 5MB");
        return handleApi(error);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", "Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        ApiException error = new ApiException(500, "INTERNAL_ERROR", "服务器内部错误");
        return handleApi(error);
    }

    private ResponseEntity<Map<String, Object>> validation(String message, Map<String, Object> detail) {
        ValidationException error = new ValidationException(message, Collections.singletonList(detail));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.getCode());
        body.put("message", error.getMessage());
        body.put("details", error.getDetails());
        return ResponseEntity.status(422).body(singletonError(body));
    }

    private Map<String, Object> singletonError(Map<String, Object> error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        return body;
    }

    public static Map<String, Object> field(String location, String name, String type) {
        Map<String, Object> detail = new LinkedHashMap<>();
        List<String> loc = new ArrayList<>();
        loc.add(location);
        loc.add(name);
        detail.put("loc", loc);
        detail.put("msg", name + " 不合法");
        detail.put("type", type);
        return detail;
    }
}
