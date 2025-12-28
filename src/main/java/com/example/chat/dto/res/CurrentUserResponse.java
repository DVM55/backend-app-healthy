package com.example.chat.dto.res;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserResponse {
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String avatar;
}