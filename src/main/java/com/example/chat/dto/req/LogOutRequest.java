package com.example.chat.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogOutRequest {
    @NotBlank(message = "Device ID không được để trống")
    private String deviceId;
}
