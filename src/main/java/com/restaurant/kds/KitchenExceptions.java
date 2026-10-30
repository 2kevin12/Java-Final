package com.restaurant.kds;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public class KitchenExceptions {
    // Consolidated module holder: KitchenExceptions
}

@ControllerAdvice
class GlobalExceptionHandler {

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return uri.startsWith("/api/") || (accept != null && accept.contains("application/json"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleNotFound(ResourceNotFoundException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "Not Found");
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/orders";
    }

    @ExceptionHandler(InvalidOrderStateTransitionException.class)
    public Object handleInvalidTransition(InvalidOrderStateTransitionException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "Invalid State Transition");
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/kds";
    }

    @ExceptionHandler(BusinessValidationException.class)
    public Object handleBusinessValidation(BusinessValidationException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "Business Rule Violation");
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/orders";
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String msg = "Conflict: This record was modified by another user concurrently. Please refresh the page.";
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.CONFLICT.value());
            body.put("error", "Concurrency Conflict");
            body.put("message", msg);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        redirectAttributes.addFlashAttribute("errorMessage", msg);
        return "redirect:/kds";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
            errors.put(err.getField(), err.getDefaultMessage())
        );
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "Validation Failed");
            body.put("errors", errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(KitchenOverloadException.class)
    public Object handleKitchenOverload(KitchenOverloadException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
            body.put("error", "Kitchen Overload Emergency");
            body.put("message", ex.getMessage());
            body.put("currentBacklog", ex.getCurrentBacklog());
            body.put("maxCookingCapacity", ex.getMaxCookingCapacity());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/kds";
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneralError(Exception ex, HttpServletRequest request, Model model) {
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            body.put("error", "Internal Server Error");
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }
}

class BusinessValidationException extends RuntimeException {
    public BusinessValidationException(String message) {
        super(message);
    }
}

class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

class InvalidOrderStateTransitionException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    public InvalidOrderStateTransitionException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(String.format("Cannot transition order from status '%s' to '%s'",
                currentStatus != null ? currentStatus.name() : "NULL",
                targetStatus != null ? targetStatus.name() : "NULL"));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public InvalidOrderStateTransitionException(String message) {
        super(message);
        this.currentStatus = null;
        this.targetStatus = null;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }
}

/**
 * Custom exception thrown when emergency order backlogs exceed the maximum
 * allowable kitchen cooking capacity or carousel safety threshold.
 */
class KitchenOverloadException extends Exception {

    private final int currentBacklog;
    private final int maxCookingCapacity;

    public KitchenOverloadException(String message) {
        super(message);
        this.currentBacklog = -1;
        this.maxCookingCapacity = -1;
    }

    public KitchenOverloadException(String message, int currentBacklog, int maxCookingCapacity) {
        super(String.format("%s [Current Backlog: %d, Max Cooking Capacity: %d]", message, currentBacklog, maxCookingCapacity));
        this.currentBacklog = currentBacklog;
        this.maxCookingCapacity = maxCookingCapacity;
    }

    public int getCurrentBacklog() {
        return currentBacklog;
    }

    public int getMaxCookingCapacity() {
        return maxCookingCapacity;
    }
}