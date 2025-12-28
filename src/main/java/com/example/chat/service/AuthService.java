package com.example.chat.service;

import com.example.chat.dto.req.*;
import com.example.chat.dto.res.LoginResponse;
import com.example.chat.dto.res.RegisterResponse;
import com.example.chat.entity.Account;
import com.example.chat.entity.Key;
import com.example.chat.enums.Role;
import com.example.chat.exception.ConflictException;
import com.example.chat.repository.AccountRepository;
import com.example.chat.repository.DeviceTokenRepository;
import com.example.chat.repository.KeyRepository;
import com.example.chat.security.JwtTokenProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeyRepository keyRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;
    private final RedisBaseService redisBaseService;


    public RegisterResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email đã được sử dụng");
        }

        if (accountRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username đã được sử dụng");
        }

        Account account = Account.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        Account savedAccount = accountRepository.save(account);

        return RegisterResponse.builder()
                .id(savedAccount.getId())
                .email(savedAccount.getEmail())
                .username(savedAccount.getUsername())
                .role(savedAccount.getRole())
                .createdAt(savedAccount.getCreatedAt())
                .build();
    }

    public void login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        otpService.sendOTPAsync(request.getEmail(), 1);
    }


    public LoginResponse verifyAccount(VerifyAccountRequest request){
        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());
        if (!valid) {
            throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
        }

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(()-> new EntityNotFoundException("Tài khoản không tồn tại với email:" + request.getEmail()));

        // Sinh JWT token
        String accessToken = jwtTokenProvider.generateAccessToken(
                account.getId(),
                account.getUsername(),
                account.getRole().name()
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
                account.getId(),
                account.getUsername(),
                account.getRole().name()
        );

        // Lưu refresh token vào DB
        Optional<Key> keyOptional = keyRepository.findByAccount_IdAndDeviceId(account.getId(), request.getDeviceId());
        Key key = keyOptional.orElseGet(Key::new);

        key.setAccount(account);
        key.setDeviceId(request.getDeviceId());
        key.setRefreshToken(refreshToken);

        keyRepository.save(key);

        String avatarUrl = switch (account.getRole()) {
            case USER, ADMIN   -> account.getUserDetail() != null
                    ? account.getUserDetail().getAvatar_url()
                    : null;
            case DOCTOR -> account.getDoctorDetail() != null
                    ? account.getDoctorDetail().getAvatar_url()
                    : null;
        };

        return LoginResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .username(account.getUsername())
                .role(account.getRole())
                .avatar_url(avatarUrl)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public void saveRefreshTokenForDevice(Account account, String deviceId, String refreshToken) {
        Optional<Key> keyOptional = keyRepository.findByAccount_IdAndDeviceId(account.getId(), deviceId);
        Key key = keyOptional.orElseGet(Key::new);

        key.setAccount(account);
        key.setDeviceId(deviceId);
        key.setRefreshToken(refreshToken);

        keyRepository.save(key);
    }


    public void processForgotPassword(EmailRequest request)  {
        if (!accountRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email này chưa được đăng ký. Không thể thực hiện chức năng này");
        }
        otpService.sendOTPAsync(request.getEmail(), request.getType());
    }

    public void verifyOtp(VerifyAccountRequest request) {
        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());
        if (!valid) {
            throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
        }
    }

    public void sendOTP(EmailRequest request){
        switch (request.getType()) {
            case 1:
                otpService.sendOTPAsync(request.getEmail(), 1);
                break;

            case 2:
                otpService.sendOTPAsync(request.getEmail(), 2);
                break;

            default:
                throw new IllegalArgumentException("Loại OTP không hợp lệ: " + request.getType());
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());
        if (!valid) {
            throw new RuntimeException("❌ Phiên đã hết hạn, vui lòng thực hiện quên mật khẩu lại!");
        }

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("❌ Không tìm thấy tài khoản với email: " + request.getEmail()));

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);

        // ✅ Xoá OTP sau khi reset thành công
        otpService.deleteOtp(request.getEmail());
    }

    public void changePassword(String oldPassword, String newPassword) {
        Long accountId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            throw new RuntimeException("Mật khẩu cũ không chính xác");
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    public Map<String, String> refreshToken(String refreshToken, String deviceId) {
        // Tìm refresh token trong DB
        Key key = keyRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token không hợp lệ"));

        Account account = key.getAccount();

        accountRepository.findById(account.getId())
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại hoặc đã bị xoá"));

        // Kiểm tra deviceId khớp
        if (!key.getDeviceId().equals(deviceId)) {
            throw new RuntimeException("DeviceId không khớp với refresh token");
        }

        // Sinh accessToken mới
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                account.getId(),
                account.getUsername(),
                account.getRole().name()
        );

        // ✅ Trả về map chỉ chứa accessToken
        return Map.of("accessToken", newAccessToken);
    }

    @Transactional
    public void logout(String authHeader, String deviceId) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException(
                    "Token Invalid!"
            );
        }

        String token = authHeader.substring(7);
        Long accountId = jwtTokenProvider.extractUserIdIgnoreExpiration(token);

        long expireAt = jwtTokenProvider.getExpirationTime(token);
        redisBaseService.blacklistToken(token, expireAt);

        keyRepository.deleteByAccount_IdAndDeviceId(accountId, deviceId);

        deviceTokenRepository.deleteByUserIdAndDeviceId(accountId, deviceId);
    }
}

