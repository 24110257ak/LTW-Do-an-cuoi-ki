package vn.iotstar.exception;
public class OtpRateLimitException extends BadRequestException {
    public OtpRateLimitException(String message) { super(message); }
}
