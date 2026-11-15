package demo.highdemand.ticketing.controller;

import demo.highdemand.ticketing.dto.AuthResponse;
import demo.highdemand.ticketing.dto.LoginRequest;
import demo.highdemand.ticketing.entity.User;
import demo.highdemand.ticketing.repository.UserRepository;
import demo.highdemand.ticketing.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request)
            throws Exception {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        String token = jwtService.generateToken(user);

        return new AuthResponse(token);
    }

    @PostMapping("/decodetoken")
    public String decode(@RequestBody String token) throws Exception {

        String[] parts = token.split("\\.");

        String payload = parts[1];

        String decodedPayload =
                new String(
                        Base64.getUrlDecoder().decode(payload),
                        StandardCharsets.UTF_8
                );

        return decodedPayload;
    }

    //remember to comment this out for production releases, it is just for creating first user after setup
    @GetMapping("/bcrypt")
    public ResponseEntity<String> returnBCryptValue(@RequestParam String password)
    {
        try {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            return ResponseEntity.ok(
                    encoder.encode(password));
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body("Invalid password input.");
        }
        finally {

        }
    }
}
