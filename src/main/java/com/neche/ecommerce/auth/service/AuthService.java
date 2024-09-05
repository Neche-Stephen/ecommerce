package com.neche.ecommerce.auth.service;

import com.neche.ecommerce.auth.payload.request.LoginRequest;
import com.neche.ecommerce.auth.payload.request.UserRegisterRequest;
import com.neche.ecommerce.auth.payload.response.LoginResponse;
import jakarta.mail.MessagingException;

public interface AuthService {

    String registerUser(UserRegisterRequest userRegisterRequest) throws MessagingException;

    LoginResponse loginUser(LoginRequest loginRequest);
}
