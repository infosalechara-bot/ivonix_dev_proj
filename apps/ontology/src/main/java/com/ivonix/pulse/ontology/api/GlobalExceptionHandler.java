package com.ivonix.pulse.ontology.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex){Map<String,String> errors=new LinkedHashMap<>();ex.getBindingResult().getFieldErrors().forEach(e->errors.put(e.getField(),e.getDefaultMessage()));return ResponseEntity.badRequest().body(Map.of("error","validation_failed","fields",errors));}
 @ExceptionHandler({IllegalArgumentException.class,ConstraintViolationException.class})
 ResponseEntity<Map<String,Object>> bad(Exception ex){return ResponseEntity.badRequest().body(Map.of("error","bad_request","message",Objects.toString(ex.getMessage(),"Invalid request")));}
 @ExceptionHandler(SecurityException.class)
 ResponseEntity<Map<String,Object>> forbidden(SecurityException ex){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","forbidden","message","Access denied"));}
}
