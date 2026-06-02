package com.webchat.backend.dto;

import lombok.Data;

@Data
public class UserProfileRequest {
    private String name;
    private String email;
    private String password;
}
