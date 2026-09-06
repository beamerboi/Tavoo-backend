package org.tavoo.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.tavoo.dto.LoginRequest;
import org.tavoo.service.JwtService;
import org.tavoo.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthController authController;

    @Test
    void forwardsRememberMeChoiceToTokenIssuer() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        authController.login(new LoginRequest("waiter", "password", true));

        verify(jwtService).issueAccessToken(authentication, true);
    }
}
