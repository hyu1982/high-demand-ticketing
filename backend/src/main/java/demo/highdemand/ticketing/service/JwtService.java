package demo.highdemand.ticketing.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import demo.highdemand.ticketing.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final byte[] secret;

    public JwtService(
            @Value("${jwt.secret}") String secret) {

        this.secret = secret.getBytes(
                StandardCharsets.UTF_8
        );
    }

    public String generateToken(User user)
            throws Exception {

        Date issuedAt = new Date();

        Date expiration = new Date(
                issuedAt.getTime()
                        + 60 * 60 * 1000
        );

        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(user.getUsername())
                        .claim("role", user.getRole())
                        .claim("email", user.getEmail())
                        .issueTime(issuedAt)
                        .expirationTime(expiration)
                        .build();

        SignedJWT signedJWT =
                new SignedJWT(
                        new JWSHeader(
                                JWSAlgorithm.HS256
                        ),
                        claims
                );

        JWSSigner signer =
                new MACSigner(secret);

        signedJWT.sign(signer);

        return signedJWT.serialize();
    }
}