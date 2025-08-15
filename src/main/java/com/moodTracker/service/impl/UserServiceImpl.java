package com.moodTracker.service.impl;

import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.dto.ResetPasswordRequest;
import com.moodTracker.entity.Role;
import com.moodTracker.entity.User;
import com.moodTracker.mapper.UserMapper;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.security.JwtService;
import com.moodTracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public void registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already in use");
        }

        User user = userMapper.toEntity(request);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setEnabled(true);
        user.setRole(Role.USER);

        userRepository.save(user);
    }

    @Override
    public String login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        org.springframework.security.core.userdetails.User userDetails =
                (org.springframework.security.core.userdetails.User) auth.getPrincipal();

        return jwtService.generateToken(userDetails);
    }

    @Override
    public String changePassword(ResetPasswordRequest request) {

        User user = userRepository.findUserByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        if (user != null) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Password has been changed for user ")
                .append(request.email())
                .append(". Please continue to login page.");

        return sb.toString();
    }


}
