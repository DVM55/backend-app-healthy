package com.example.chat.controller;

import com.example.chat.dto.ApiResponse;
import com.example.chat.dto.req.*;
import com.example.chat.dto.res.LoginResponse;
import com.example.chat.dto.res.RegisterResponse;
import com.example.chat.entity.Account;
import com.example.chat.entity.UserDetail;
import com.example.chat.enums.AuthProvider;
import com.example.chat.enums.Role;

import com.example.chat.repository.AccountRepository;
import com.example.chat.security.JwtTokenProvider;
import com.example.chat.service.AuthService;
import com.example.chat.service.GoogleTokenVerifierService;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenVerifierService googleVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountRepository accountRepository;


    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody @Valid RegisterRequest request) {
        RegisterResponse createdAccount = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<RegisterResponse>builder()
                        .code(HttpServletResponse.SC_CREATED)
                        .message("Đăng ký thành công")
                        .data(createdAccount)
                        .build()
        );

    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody @Valid LoginRequest request) {
        authService.login(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Vui lòng xác thực tài khoản bước 2")
                        .data(null)
                        .build()
        );
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOTP(
            @RequestBody @Valid EmailRequest request
    ){
        authService.sendOTP(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Đã gửi mã OTP đến email của bạn")
                        .data(null)
                        .build()
        );
    }

    @PostMapping("/verify-account")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyAccount(
            @RequestBody @Valid VerifyAccountRequest request
    ){
        LoginResponse data = authService.verifyAccount(request);
        return ResponseEntity.ok(
                ApiResponse.<LoginResponse>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Xác thực tài khoản thành công")
                        .data(data)
                        .build()
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody @Valid EmailRequest request){
        authService.processForgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Đã gửi mã OTP đến email của bạn")
                        .data(null)
                        .build()
        );
    }

    /**
     * Bước 2: Xác thực OTP
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<?>> verifyOtp(@RequestBody @Valid VerifyAccountRequest request) {

        authService.verifyOtp(request);
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Xác thực OTP thành công")
                        .data(null)
                        .build()
        );
    }

    /**
     * Bước 3: Đặt lại mật khẩu mới
     */
    @PostMapping("/reset-password")
    public  ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody @Valid ResetPasswordRequest request){
        authService.resetPassword(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Đặt lại mật khẩu thành công")
                        .data(null)
                        .build()
        );
    }

    @PostMapping("/updated-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Đổi mật khẩu thành công")
                        .data(null)
                        .build()
        );
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody Map<String, String> body) throws Exception {
        String idToken = body.get("idToken");
        String deviceId = body.get("deviceId");

        GoogleIdToken.Payload payload = googleVerifier.verify(idToken);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        Account account = accountRepository.findByEmail(email)
                .orElseGet(() -> {
                    Account acc = new Account();
                    acc.setEmail(email);
                    acc.setUsername(name);
                    acc.setRole(Role.USER);
                    acc.setAuthProvider(AuthProvider.GOOGLE);
                    return acc;
                });

        UserDetail userDetail = account.getUserDetail();
        if (userDetail == null) {
            userDetail = new UserDetail();
            userDetail.setAccount(account);
            account.setUserDetail(userDetail);
            userDetail.setAvatar_url(picture);
        }

        account = accountRepository.save(account);

        String accessToken = jwtTokenProvider.generateAccessToken(
                account.getId(), account.getUsername(), account.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                account.getId(), account.getUsername(), account.getRole().name());

        authService.saveRefreshTokenForDevice(account, deviceId, refreshToken);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "id", account.getId(),
                "email", email,
                "name", name,
                "picture", userDetail.getAvatar_url(),
                "role", account.getRole().name()
        ));
    }

    @PostMapping("/refresh-accessToken")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        String deviceId = body.get("deviceId");
        return ResponseEntity.ok(authService.refreshToken(refreshToken, deviceId));
    }



    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String token,
            @RequestBody @Valid LogOutRequest request
    ) {
        authService.logout(token, request.getDeviceId());

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Đăng xuất thành công")
                        .data(null)
                        .build()
        );
    }


}

