package com.example.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final RedisBaseService redisBaseService;
    private final MailService mailService;

    public String generateOtp(String email, int ttlMinutes) {
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        redisBaseService.setWithTTL("OTP:" + email, otp, ttlMinutes); // TTL theo phút
        return otp;
    }

    // Kiểm tra OTP
    public boolean verifyOtp(String email, String otp) {
        String key = "OTP:" + email;
        Object cached = redisBaseService.get(key);

        return cached != null && cached.toString().equals(otp);
    }

    // Xóa OTP sau khi reset thành công
    public void deleteOtp(String email) {
        redisBaseService.delete("OTP:" + email);
    }

    @Async
    public void sendOTPAsync(String email, int type) {
        int ttl;
        String subject;
        String body;

        switch (type) {
            case 1 -> {
                ttl = 1;
                subject = "Mã OTP xác thực tài khoản";
                body = "<p>Xin chào,</p>" +
                        "<p>Mã OTP của bạn là: <b>%s</b></p>" +
                        "<p>Mã này có hiệu lực trong 1 phút.</p>";
            }
            case 2 -> {
                ttl = 15;
                subject = "Mã OTP khôi phục mật khẩu";
                body = "<p>Xin chào,</p>" +
                        "<p>Mã OTP của bạn là: <b>%s</b></p>" +
                        "<p>Mã này có hiệu lực trong 15 phút.</p>";
            }
            default -> throw new IllegalArgumentException("Loại OTP không hợp lệ: " + type);
        }

        String otp = generateOtp(email, ttl);
        mailService.sendHtml(email, subject, String.format(body, otp));
    }



}
