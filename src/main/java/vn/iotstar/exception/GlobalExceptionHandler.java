package vn.iotstar.exception;

import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return (uri.startsWith("/api/") || uri.startsWith("/chat/api/")) || (accept != null && accept.contains("application/json"));
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleResourceNotFound(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.warn("404 Not Found at {}: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "Not Found");
            body.put("message", ex.getMessage());
            body.put("path", request.getRequestURI());
            return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
        }
        model.addAttribute("status", 404);
        model.addAttribute("error", "Không tìm thấy trang");
        model.addAttribute("message", "Đường dẫn không tồn tại hoặc đã bị di chuyển.");
        return "error/404";
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public Object handleOtpLimit(OtpRateLimitException ex,HttpServletRequest request,HttpServletResponse response,Model model) {
        response.setStatus(429);
        if(isApiRequest(request))return ResponseEntity.status(429).body(Map.of("status",429,"message",ex.getMessage(),"path",request.getRequestURI()));
        model.addAttribute("status",429);model.addAttribute("error","Tạm giới hạn OTP");model.addAttribute("message",ex.getMessage());return "error/400";
    }

    @ExceptionHandler(BadRequestException.class)
    public Object handleBadRequest(BadRequestException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.warn("BadRequestException: {}", ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "Bad Request");
            body.put("message", ex.getMessage());
            body.put("path", request.getRequestURI());
            return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
        }
        model.addAttribute("status", 400);
        model.addAttribute("error", "Yêu cầu không hợp lệ");
        model.addAttribute("message", ex.getMessage());
        return "error/400";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failed for {}: {}", request.getRequestURI(), errors);
        response.setStatus(HttpStatus.BAD_REQUEST.value());

        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "Validation Error");
            body.put("errors", errors);
            body.put("path", request.getRequestURI());
            return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
        }
        model.addAttribute("status", 400);
        model.addAttribute("error", "Dữ liệu không hợp lệ");
        model.addAttribute("message", errors.values().stream().findFirst().orElse("Vui lòng kiểm tra lại thông tin"));
        return "error/400";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex,
                                   HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", 403, "error", "Forbidden", "message", ex.getMessage(), "path", request.getRequestURI()));
        }
        model.addAttribute("message", ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.validation.BindException.class})
    public Object invalidInput(Exception ex,HttpServletRequest request,HttpServletResponse response,Model model) {
        response.setStatus(400);if(isApiRequest(request))return ResponseEntity.badRequest().body(Map.of("status",400,"message","Thiếu trường hoặc sai kiểu dữ liệu"));
        model.addAttribute("status",400);model.addAttribute("error","Dữ liệu không hợp lệ");model.addAttribute("message","Vui lòng nhập đủ trường và đúng kiểu dữ liệu.");return "error/400";
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Object invalidMethod(Exception ex,HttpServletRequest request,HttpServletResponse response,Model model) {
        response.setStatus(405);if(isApiRequest(request))return ResponseEntity.status(405).body(Map.of("status",405,"message","Phương thức HTTP không hỗ trợ"));
        model.addAttribute("status",405);model.addAttribute("error","Phương thức không hỗ trợ");model.addAttribute("message","Gửi lại OTP cần dùng nút POST trên form.");return "error/400";
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.error("Unhandled Exception at {}: ", request.getRequestURI(), ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            body.put("error", "Internal Server Error");
            body.put("message", "Đã có lỗi xảy ra trên hệ thống. Vui lòng thử lại sau!");
            body.put("path", request.getRequestURI());
            return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        model.addAttribute("status", 500);
        model.addAttribute("error", "Lỗi máy chủ");
        model.addAttribute("message", "Đã có sự cố xảy ra. Chúng tôi đang khắc phục!");
        return "error/500";
    }
}
