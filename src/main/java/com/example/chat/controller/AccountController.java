package com.example.chat.controller;

import com.example.chat.dto.ApiResponse;
import com.example.chat.dto.PagingResponse;
import com.example.chat.dto.req.UpdateAccountRequest;
import com.example.chat.dto.res.AccountResponse;
import com.example.chat.dto.res.CurrentUserResponse;
import com.example.chat.dto.res.DoctorResponse;
import com.example.chat.dto.res.ProfileUserResponse;
import com.example.chat.entity.Account;
import com.example.chat.entity.DoctorDetail;
import com.example.chat.entity.UserDetail;
import com.example.chat.repository.AccountRepository;
import com.example.chat.service.AccountService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;

    @PutMapping("/update-profile-user")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @RequestBody @Valid UpdateAccountRequest request
    ) {
        AccountResponse updated = accountService.updateAccount(request);

        return ResponseEntity.ok(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Cập nhật tài khoản thành công")
                        .data(updated)
                        .build()
        );
    }

    @GetMapping("/users")
    public ResponseEntity<PagingResponse<AccountResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        // Gọi service để lấy PagingResponse
        PagingResponse<AccountResponse> pagingResponse = accountService.getPagedAccounts(page, size, sortBy, direction);

        return ResponseEntity.ok(pagingResponse);
    }

    @GetMapping("/doctors")
    public PagingResponse<DoctorResponse> getDoctors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return accountService.getPagedDoctors(page, size, sortBy, direction);
    }


    @GetMapping("/profile-user")
    public ResponseEntity<ApiResponse<ProfileUserResponse>> getProfile() {
        ProfileUserResponse profile = accountService.getProfile();

        return ResponseEntity.ok(
                ApiResponse.<ProfileUserResponse>builder()
                        .code(HttpServletResponse.SC_OK)
                        .message("Lấy thông tin người dùng thành công")
                        .data(profile)
                        .build()
        );
    }

    @PutMapping("/updated-avatar")
    public ResponseEntity<ApiResponse<String>> updateAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = accountService.updateAvatar(file);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpServletResponse.SC_OK)
                .message("Cập nhật avatar thành công")
                .data(avatarUrl)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser() {

        CurrentUserResponse dto = accountService.getCurrentUserInfo();

        ApiResponse<CurrentUserResponse> response = ApiResponse.<CurrentUserResponse>builder()
                .code(HttpServletResponse.SC_OK)
                .message("Lấy thông tin người dùng thành công")
                .data(dto)
                .build();

        return ResponseEntity.ok(response);
    }





}