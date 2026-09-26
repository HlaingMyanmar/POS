package org.sspd.servicemgmt.exceptionhandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.sspd.servicemgmt.api.ApiResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.sspd.servicemgmt.stockoptions.productoptions.service.B2VideoStorageService.B2StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleB2Video(
            org.sspd.servicemgmt.stockoptions.productoptions.service.B2VideoStorageService.B2StorageException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiResponse<>(false, ex.getMessage(), null));
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(false,
                        ex.getMessage() != null && !ex.getMessage().isBlank()
                                ? ex.getMessage()
                                : "Username သို့မဟုတ် Password မှားနေပါသည်",
                        null));
    }

    @ExceptionHandler(org.sspd.servicemgmt.authoption.AuthSessionException.class)
    public ResponseEntity<Map<String, Object>> handleAuthSession(
            org.sspd.servicemgmt.authoption.AuthSessionException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Session error");
        body.put("error", ex.getErrorCode());
        body.put("data", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockedAccount(LockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new ApiResponse<>(false,
                        ex.getMessage() != null && !ex.getMessage().isBlank()
                                ? ex.getMessage()
                                : "Account temporarily locked due to failed login attempts.",
                        null));
    }

    // ၁။ @NotBlank စတဲ့ Validation Error တွေကို ဖမ်းဖို့
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleValidationException(MethodArgumentNotValidException ex){
        Map<String,String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
            errors.put(fieldError.getField(),fieldError.getDefaultMessage())
        );
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage() != null ? ex.getMessage() : "သင့်မှာ ခွင့်ပြုချက်မရှိပါ။",
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Data integrity", ex);
        return new ResponseEntity<>(
                new ErrorResponse(HttpStatus.CONFLICT.value(), "ဤဖုန်း သို့မဟုတ် အကောင့် ရှိပြီးသား ဖြစ်သည်", System.currentTimeMillis()),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage() != null ? ex.getMessage() : "သင့်မှာ ခွင့်ပြုချက်မရှိပါ။",
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * SockJS fallback transports establish their own response content type (for example
     * application/javascript or text/event-stream) and may already have committed the
     * response when a write fails. Returning our normal JSON ErrorResponse here causes a
     * second conversion failure and hides the original transport error.
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public void handleMessageNotWritable(
            HttpMessageNotWritableException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String requestUri = request.getRequestURI();
        boolean socketTransport = requestUri.startsWith("/ws-clinic/")
                || requestUri.startsWith("/ws-native/");

        if (response.isCommitted() || socketTransport) {
            log.debug("Skipping JSON error rendering for WebSocket transport {}", requestUri, ex);
            if (!response.isCommitted()) {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
            return;
        }

        log.error("Failed to serialize response for {}", requestUri, ex);
        response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Response serialization failed");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        if (isUnexpected(ex)) {
            log.error("Unhandled server error", ex);
            return new ResponseEntity<>(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error", System.currentTimeMillis()),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        log.warn("Request rejected", ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage() != null ? ex.getMessage() : "Request failed",
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    private boolean isUnexpected(RuntimeException ex) {
        return ex instanceof NullPointerException
                || ex instanceof IndexOutOfBoundsException
                || ex instanceof ClassCastException
                || ex instanceof ArithmeticException;
    }

}
