package ru.casebook.dims.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiErrorResponse(ex.getCode(), ex.getMessage(), ex.getDetails(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", "Проверьте обязательные поля формы", fields, Instant.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_JSON", "Запрос содержит некорректные данные", Map.of(), Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleUploadLimit(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "FILE_TOO_LARGE", "Файл превышает лимит 20 МБ", Map.of(), Instant.now()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                "GRAPH_STALE", "Данные изменены другим пользователем. Граф обновлен", Map.of(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled API error {}", errorId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка сервера. Код: " + errorId, Map.of("errorId", errorId), Instant.now()));
    }
}
