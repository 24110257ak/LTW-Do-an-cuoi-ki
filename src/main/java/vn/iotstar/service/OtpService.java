package vn.iotstar.service;

import vn.iotstar.enums.OtpPurpose;

public interface OtpService {
    String generateAndSendOtp(String email, OtpPurpose purpose);
    boolean verifyOtp(String email, String otpCode, OtpPurpose purpose);
}

