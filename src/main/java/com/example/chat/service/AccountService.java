package com.example.chat.service;

import com.example.chat.dto.PagingResponse;
import com.example.chat.dto.req.UpdateAccountRequest;
import com.example.chat.dto.res.*;
import com.example.chat.entity.Account;
import com.example.chat.entity.DoctorDetail;
import com.example.chat.entity.UserDetail;
import com.example.chat.enums.Role;
import com.example.chat.mapper.AccountMapper;
import com.example.chat.repository.AccountRepository;
import com.example.chat.repository.UserDetailRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserDetailRepository userDetailRepository;

    private final AccountMapper accountMapper;
    private final CloudinaryService cloudinaryService;

    @Transactional(rollbackFor = Exception.class)
    public AccountResponse updateAccount(UpdateAccountRequest request) {

        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // 1. Tìm tài khoản
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại"));

        // 2. Cập nhật dữ liệu account
        accountMapper.updateAccountFromDTO(request, account);

        // 3. Lấy hoặc tạo mới UserDetail
        UserDetail userDetail = account.getUserDetail();
        if (userDetail == null) {
            userDetail = new UserDetail();
            userDetail.setAccount(account); // liên kết 2 chiều
            account.setUserDetail(userDetail);
        }

        // 4. Cập nhật dữ liệu userDetail
        accountMapper.updateUserDetailFromDTO(request, userDetail);

        // 5. Lưu lại
        userDetailRepository.save(userDetail);
        accountRepository.save(account);

        // 6. Trả về DTO
        return accountMapper.toDto(account);
    }

    public PagingResponse<AccountResponse> getPagedAccounts(int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Account> accountPage = accountRepository.findAll(pageable);

        List<AccountResponse> data = accountPage
                .stream()
                .map(accountMapper::toDto)
                .toList();

        return PagingResponse.<AccountResponse>builder()
                .code(200)
                .message("Lấy danh sách người dùng thành công")
                .page(accountPage.getNumber())
                .size(accountPage.getSize())
                .totalElements(accountPage.getTotalElements())
                .totalPages(accountPage.getTotalPages())
                .data(data)
                .build();
    }

    public PagingResponse<DoctorResponse> getPagedDoctors(int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        // 🔹 Giả sử Account có field role (Enum Role.DOCTOR)
        Page<Account> doctorPage = accountRepository.findAllByRole(Role.DOCTOR, pageable);

        List<DoctorResponse> data = doctorPage
                .stream()
                .map(accountMapper::toDoctorDto)
                .toList();

        return PagingResponse.<DoctorResponse>builder()
                .code(200)
                .message("Lấy danh sách bác sĩ thành công")
                .page(doctorPage.getNumber())
                .size(doctorPage.getSize())
                .totalElements(doctorPage.getTotalElements())
                .totalPages(doctorPage.getTotalPages())
                .data(data)
                .build();
    }


    @Transactional(readOnly = true)
    public ProfileUserResponse getProfile() {

        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại"));

        UserDetail detail = account.getUserDetail();

        return ProfileUserResponse.builder()
                .id(account.getId())
                .username(account.getUsername())
                .phone(detail != null ? detail.getPhone() : null)
                .email(account.getEmail())
                .dateOfBirth(detail != null ? detail.getDate_of_birth() : null)
                .gender(detail != null && detail.getGender() != null
                        ? detail.getGender().name()
                        : null)
                .address(detail != null ? detail.getAddress() : null)
                .build();
    }


    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUserInfo() {

        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại"));

        return CurrentUserResponse.builder()
                .userId(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .role(account.getRole().name())
                .avatar(extractAvatar(account))
                .build();
    }


    private String extractAvatar(Account account) {
        return switch (account.getRole()) {
            case USER -> Optional.ofNullable(account.getUserDetail())
                    .map(UserDetail::getAvatar_url)
                    .orElse("data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBwgHBgkIBwgKCgkLDRYPDQwMDRsUFRAWIB0iIiAdHx8kKDQsJCYxJx8fLT0tMTU3Ojo6Iys/RD84QzQ5OjcBCgoKDQwNGg8PGjclHyU3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3N//AABEIAMAAzAMBIgACEQEDEQH/xAAbAAEAAgMBAQAAAAAAAAAAAAAAAQIEBQYDB//EADIQAQABAwICCAUDBQEAAAAAAAABAgMRBCEFMRIiMkFRYXGBExVSU5EzNEIjcqHB0RT/xAAWAQEBAQAAAAAAAAAAAAAAAAAAAQL/xAAWEQEBAQAAAAAAAAAAAAAAAAAAEQH/2gAMAwEAAhEDEQA/APogDTIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAoAmImZxEZBA9PgXpjMWq59kVW7lPaoqj1hBQEqIAQAAAAAAAAAAAAAAAADfujKMtnwjSdOr41e9MconxBbRcL6UU3NRnHdRDZ2rFq3GKKKY9nrEbJStIwiaYnnET7LCDC1HD7F6NqehV9VLS6rTXNNX0a4zE8qvF07x1NinUWqrdWN428lzRzAtctzarqoq5xO6rSACIAAAAAAAAAAAAAARHSqxHOXUaa1Fq1TREYiIc7o6OnqrdPjVDpk1cSAigAAANJxqzFN6m5THajf1a2G841TnSxV301NH6NYgAqACAAAAAAAAAAAAKyeG/vrXq6Ry2nq+Hft1d8S6imcxmO9NVICAAAADC4x+yq9Yc+3XHK8WaKPqqaVrEABAAAAAAAAAAAAAAD/AE6Dhmoi9pqfqp2mHPsjRamrTXYq/jO1UA6Uedq7RcoiqmqJjxejLQAAiTLXcT1kWqJtW5/qTz8ga/id/wCPqZiOxTtDFQNIACAAAAAAAAAAAAAAACqyNHq7mlq6u9M86W40/ErF2MTV0KvCpz5hNK6qLtFUZiuJ91LmqsW469ymPdzEZjkbzzSFbXV8U506eJ/ulq5mqqZmqc1TzmUClABAAABQAQAAAAAAATG847xUPazprt+Yi3RM+fc2Gh4ZExFzUc53ij/ra0UxTTEUxER4Qm6RqbXB8/q3falk08J00dqKqvWWfgSrGH8s0v25/MnyzS/bn8yzAGF8r0v0T+T5Xpfon8s0Bh/LNL9ufzKJ4XpZ/hMe7NAay5wezP6ddVM+bDv8Mv24macVx5N+jBSOUqpqpzExifCUOk1WjtaiOtTir6oaLV6a5pq5iuM091TVSPABQARAAAAAkJ5AjPe3XCtD0aYvXY608onuYHDdN/6L8TPYp3nzdDHkmriYSrnCcoqRGTIJEZMgkRkyCRGTIJEZMgl5XrNF2iaK4zE/4emUZBzer09WmuzRVy/jPi8HQ8S08X7E4jr07w57fPg1iAAgAAAATyExvMR4g3nCrXwtLFUx1qpzLNiXlajo2qI8KYhfKKsZVyZRVsmVcmQWyZVyZEWyZVyZBYVyZBYyrkFWyZVyZBOcue19v4WqrpjlnZ0GWo4zTiuivywuI1wtNuumnpVRtPKVVQAAAATT26fVC1Hbp9RXR9yVc5CCxlUyQWFdzcgtkV3MkFhXJkgsKhBbIqbkFjKu5uQWa7jMdKm1id8yz8y13F56tuPOSDAq+JMdaYmPDKmMeCPYEAAf/9k=");
            case DOCTOR -> Optional.ofNullable(account.getDoctorDetail())
                    .map(DoctorDetail::getAvatar_url)
                    .orElse("data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBwgHBgkIBwgKCgkLDRYPDQwMDRsUFRAWIB0iIiAdHx8kKDQsJCYxJx8fLT0tMTU3Ojo6Iys/RD84QzQ5OjcBCgoKDQwNGg8PGjclHyU3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3N//AABEIAMAAzAMBIgACEQEDEQH/xAAbAAEAAgMBAQAAAAAAAAAAAAAAAQIEBQYDB//EADIQAQABAwICCAUDBQEAAAAAAAABAgMRBCEFMRIiMkFRYXGBExVSU5EzNEIjcqHB0RT/xAAWAQEBAQAAAAAAAAAAAAAAAAAAAQL/xAAWEQEBAQAAAAAAAAAAAAAAAAAAEQH/2gAMAwEAAhEDEQA/APogDTIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAoAmImZxEZBA9PgXpjMWq59kVW7lPaoqj1hBQEqIAQAAAAAAAAAAAAAAAADfujKMtnwjSdOr41e9MconxBbRcL6UU3NRnHdRDZ2rFq3GKKKY9nrEbJStIwiaYnnET7LCDC1HD7F6NqehV9VLS6rTXNNX0a4zE8qvF07x1NinUWqrdWN428lzRzAtctzarqoq5xO6rSACIAAAAAAAAAAAAAARHSqxHOXUaa1Fq1TREYiIc7o6OnqrdPjVDpk1cSAigAAANJxqzFN6m5THajf1a2G841TnSxV301NH6NYgAqACAAAAAAAAAAAAKyeG/vrXq6Ry2nq+Hft1d8S6imcxmO9NVICAAAADC4x+yq9Yc+3XHK8WaKPqqaVrEABAAAAAAAAAAAAAAD/AE6Dhmoi9pqfqp2mHPsjRamrTXYq/jO1UA6Uedq7RcoiqmqJjxejLQAAiTLXcT1kWqJtW5/qTz8ga/id/wCPqZiOxTtDFQNIACAAAAAAAAAAAAAAACqyNHq7mlq6u9M86W40/ErF2MTV0KvCpz5hNK6qLtFUZiuJ91LmqsW469ymPdzEZjkbzzSFbXV8U506eJ/ulq5mqqZmqc1TzmUClABAAABQAQAAAAAAATG847xUPazprt+Yi3RM+fc2Gh4ZExFzUc53ij/ra0UxTTEUxER4Qm6RqbXB8/q3falk08J00dqKqvWWfgSrGH8s0v25/MnyzS/bn8yzAGF8r0v0T+T5Xpfon8s0Bh/LNL9ufzKJ4XpZ/hMe7NAay5wezP6ddVM+bDv8Mv24macVx5N+jBSOUqpqpzExifCUOk1WjtaiOtTir6oaLV6a5pq5iuM091TVSPABQARAAAAAkJ5AjPe3XCtD0aYvXY608onuYHDdN/6L8TPYp3nzdDHkmriYSrnCcoqRGTIJEZMgkRkyCRGTIJEZMgl5XrNF2iaK4zE/4emUZBzer09WmuzRVy/jPi8HQ8S08X7E4jr07w57fPg1iAAgAAAATyExvMR4g3nCrXwtLFUx1qpzLNiXlajo2qI8KYhfKKsZVyZRVsmVcmQWyZVyZEWyZVyZBYVyZBYyrkFWyZVyZBOcue19v4WqrpjlnZ0GWo4zTiuivywuI1wtNuumnpVRtPKVVQAAAATT26fVC1Hbp9RXR9yVc5CCxlUyQWFdzcgtkV3MkFhXJkgsKhBbIqbkFjKu5uQWa7jMdKm1id8yz8y13F56tuPOSDAq+JMdaYmPDKmMeCPYEAAf/9k=");
            default -> "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBwgHBgkIBwgKCgkLDRYPDQwMDRsUFRAWIB0iIiAdHx8kKDQsJCYxJx8fLT0tMTU3Ojo6Iys/RD84QzQ5OjcBCgoKDQwNGg8PGjclHyU3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3N//AABEIAMAAzAMBIgACEQEDEQH/xAAbAAEAAgMBAQAAAAAAAAAAAAAAAQIEBQYDB//EADIQAQABAwICCAUDBQEAAAAAAAABAgMRBCEFMRIiMkFRYXGBExVSU5EzNEIjcqHB0RT/xAAWAQEBAQAAAAAAAAAAAAAAAAAAAQL/xAAWEQEBAQAAAAAAAAAAAAAAAAAAEQH/2gAMAwEAAhEDEQA/APogDTIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAoAmImZxEZBA9PgXpjMWq59kVW7lPaoqj1hBQEqIAQAAAAAAAAAAAAAAAADfujKMtnwjSdOr41e9MconxBbRcL6UU3NRnHdRDZ2rFq3GKKKY9nrEbJStIwiaYnnET7LCDC1HD7F6NqehV9VLS6rTXNNX0a4zE8qvF07x1NinUWqrdWN428lzRzAtctzarqoq5xO6rSACIAAAAAAAAAAAAAARHSqxHOXUaa1Fq1TREYiIc7o6OnqrdPjVDpk1cSAigAAANJxqzFN6m5THajf1a2G841TnSxV301NH6NYgAqACAAAAAAAAAAAAKyeG/vrXq6Ry2nq+Hft1d8S6imcxmO9NVICAAAADC4x+yq9Yc+3XHK8WaKPqqaVrEABAAAAAAAAAAAAAAD/AE6Dhmoi9pqfqp2mHPsjRamrTXYq/jO1UA6Uedq7RcoiqmqJjxejLQAAiTLXcT1kWqJtW5/qTz8ga/id/wCPqZiOxTtDFQNIACAAAAAAAAAAAAAAACqyNHq7mlq6u9M86W40/ErF2MTV0KvCpz5hNK6qLtFUZiuJ91LmqsW469ymPdzEZjkbzzSFbXV8U506eJ/ulq5mqqZmqc1TzmUClABAAABQAQAAAAAAATG847xUPazprt+Yi3RM+fc2Gh4ZExFzUc53ij/ra0UxTTEUxER4Qm6RqbXB8/q3falk08J00dqKqvWWfgSrGH8s0v25/MnyzS/bn8yzAGF8r0v0T+T5Xpfon8s0Bh/LNL9ufzKJ4XpZ/hMe7NAay5wezP6ddVM+bDv8Mv24macVx5N+jBSOUqpqpzExifCUOk1WjtaiOtTir6oaLV6a5pq5iuM091TVSPABQARAAAAAkJ5AjPe3XCtD0aYvXY608onuYHDdN/6L8TPYp3nzdDHkmriYSrnCcoqRGTIJEZMgkRkyCRGTIJEZMgl5XrNF2iaK4zE/4emUZBzer09WmuzRVy/jPi8HQ8S08X7E4jr07w57fPg1iAAgAAAATyExvMR4g3nCrXwtLFUx1qpzLNiXlajo2qI8KYhfKKsZVyZRVsmVcmQWyZVyZEWyZVyZBYVyZBYyrkFWyZVyZBOcue19v4WqrpjlnZ0GWo4zTiuivywuI1wtNuumnpVRtPKVVQAAAATT26fVC1Hbp9RXR9yVc5CCxlUyQWFdzcgtkV3MkFhXJkgsKhBbIqbkFjKu5uQWa7jMdKm1id8yz8y13F56tuPOSDAq+JMdaYmPDKmMeCPYEAAf/9k=";
        };
    }


    @Transactional
    public String updateAvatar(MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new IllegalArgumentException("Ảnh không được rỗng");
        }

        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại"));

        // Upload ảnh lên Cloudinary
        UploadImageResponse uploadResponse = cloudinaryService.uploadFile(avatarFile);

        // Nếu chưa có UserDetail thì tạo mới
        UserDetail userDetail = account.getUserDetail();
        if (userDetail == null) {
            userDetail = new UserDetail();
            userDetail.setAccount(account);   // gắn quan hệ 1-1
            account.setUserDetail(userDetail);
        }

        // Cập nhật avatar
        userDetail.setAvatar_url(uploadResponse.getFileUrl());

        accountRepository.save(account);

        return uploadResponse.getFileUrl();
    }

}
