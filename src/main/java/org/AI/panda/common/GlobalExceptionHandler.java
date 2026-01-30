package org.AI.panda.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage() != null ? e.getMessage() : "Bad Request");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace(); // Log the error
        return Result.error(500, e.getMessage() != null ? e.getMessage() : "Internal Server Error");
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<String> handleNoHandlerFoundException(NoHandlerFoundException e) {
        return Result.error(404, "Path not found: " + e.getRequestURL());
    }
}
