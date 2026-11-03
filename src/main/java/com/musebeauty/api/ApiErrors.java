package com.musebeauty.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    HttpStatus status=HttpStatus.valueOf(e.getStatusCode().value());
    return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now(),"status",status.value(),"error",status.getReasonPhrase(),"message",e.getReason()==null?"Request failed":e.getReason()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
    String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+" "+x.getDefaultMessage()).orElse("Invalid request");
    return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"status",400,"error","Bad Request","message",message));
  }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("timestamp",Instant.now(),"status",409,"error","Conflict","message","The request conflicts with an existing record or database constraint"));
  }
}
