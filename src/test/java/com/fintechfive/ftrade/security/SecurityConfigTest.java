package com.fintechfive.ftrade.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Date;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real SecurityConfig against tokens signed the way auth-service signs them:
 * HS256 with the shared secret, roles in a "roles" claim.
 */
@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
@TestPropertySource(properties = "jwt.shared-secret=" + SecurityConfigTest.SECRET)
class SecurityConfigTest {

    static final String SECRET = "test-secret-at-least-32-characters-long";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        ClientsProbe clientsProbe() {
            return new ClientsProbe();
        }
    }

    // stands in for ClientController, so only the security rules decide the response
    @RestController
    static class ClientsProbe {
        @GetMapping("/v1/clients/me")
        String me() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void clientRoute_clientToken_returns200() throws Exception {
        mockMvc.perform(get("/v1/clients/me").header("Authorization", "Bearer " + token(List.of("CLIENT"))))
                .andExpect(status().isOk());
    }

    // admins and analysts use the reporting dashboard, not the client API
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ANALYST"})
    void clientRoute_staffToken_returns403(String role) throws Exception {
        mockMvc.perform(get("/v1/clients/me").header("Authorization", "Bearer " + token(List.of(role))))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientRoute_noRoles_returns403() throws Exception {
        mockMvc.perform(get("/v1/clients/me").header("Authorization", "Bearer " + token(List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientRoute_noToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/clients/me"))
                .andExpect(status().isUnauthorized());
    }

    private static String token(List<String> roles) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("7")
                .claim("roles", roles)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(UTF_8)));
        return jwt.serialize();
    }
}
