package com.neche.ecommerce.auth.service.impl;

import com.neche.ecommerce.auth.payload.request.UserRegisterRequest;
import com.neche.ecommerce.auth.service.AuthService;
import com.neche.ecommerce.exception.AlreadyExistsException;
import com.neche.ecommerce.exception.EmailAlreadyExistsException;
import com.neche.ecommerce.roles.model.entity.Role;
import com.neche.ecommerce.roles.repository.RoleRepository;
import com.neche.ecommerce.user.model.entity.User;
import com.neche.ecommerce.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;


    @Value("${baseUrl}")
    private String baseUrl;


    @Override
    public String registerUser(UserRegisterRequest userRegisterRequest) throws MessagingException {
        // Validate email format
        String emailRegex = "^(.+)@(.+)$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(userRegisterRequest.getEmail());

        if(!matcher.matches()){
            return "Invalid Email domain";
        }

        String[] emailParts = userRegisterRequest.getEmail().split("\\.");
        if (emailParts.length < 2 || emailParts[emailParts.length - 1].length() < 2) {
            System.out.println("Invalid email domain. Email parts: " + Arrays.toString(emailParts));
            return "Invalid Email domain";
        }

        Optional<User> existingUser = userRepository.findByEmail(userRegisterRequest.getEmail());

        if(existingUser.isPresent()){
            throw new EmailAlreadyExistsException("Email already exists. Login to your account!");
        }

        Optional<User> existingUserByUsername = userRepository.findByUsername(userRegisterRequest.getUserName());
        if(existingUserByUsername.isPresent()){
            throw new AlreadyExistsException("Username already exists. Choose another username!");
        }

        Optional<Role> userRole = roleRepository.findByName("USER");
        if (userRole.isEmpty()) {
            throw new RuntimeException("Default role USER not found in the database.");
        }

        Set<Role> roles = new HashSet<>();
        roles.add(userRole.get());

        User newUser = User.builder()
                .fullName(userRegisterRequest.getFullName())
                .username(userRegisterRequest.getUserName())
                .email(userRegisterRequest.getEmail())
                .password(passwordEncoder.encode(userRegisterRequest.getPassword()))
                .roles(roles)
                .gender(userRegisterRequest.getGender())
                .build();

        User savedUser = userRepository.save(newUser);

        userRepository.save(savedUser);
        ConfirmationTokenModel confirmationToken = new ConfirmationTokenModel(savedUser);
        confirmationTokenRepository.save(confirmationToken);

        String confirmationUrl = EmailUtil.getVerificationUrl(confirmationToken.getToken());

        //Sending mail

        EmailDetails emailDetails = EmailDetails.builder()
                .fullName(savedUser.getFullName())
                .recipient(savedUser.getEmail())
                .subject("IFARMR REGISTRATION SUCCESSFUL")
                .link(confirmationUrl)
                .build();
        emailService.sendEmailAlerts(emailDetails, "email-verification");

        return "Confirmed Email";
    }


    @Override
    public LoginResponse loginUser(LoginRequest loginRequest) {
        authenticationManager.authenticate( new UsernamePasswordAuthenticationToken
                (loginRequest.getUsername(), loginRequest.getPassword()) );

        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if(!user.isEnabled()){
            throw new DisabledException("User account is not enabled, please check your email to enable it");
        }

        var jwtToken = jwtService.generateToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user,jwtToken);



        return org.ifarmr.payload.response.LoginResponse.builder()
                .id(user.getId())
                .token(jwtToken)
                .username(user.getUsername())
                .profilePicture(user.getDisplayPhoto())
                .role(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet()))
                .message("Login Success")

                .build();
    }





}
