package com.techpulse.wallet.service;

import com.techpulse.wallet.dto.AuthResponse;
import com.techpulse.wallet.dto.LoginRequest;
import com.techpulse.wallet.dto.RegisterRequest;

public interface AuthService {

    String register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}