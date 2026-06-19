package com.skytrack.ai.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,https://manager-skytrack-ai.vercel.app}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. PHẢI ĐẶT CORS LÊN ĐẦU TIÊN để xử lý Preflight OPTIONS request
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Access Denied\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Cho phép TUYỆT ĐỐI tất cả các request OPTIONS (CORS Preflight) đi qua không cần check quyền
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Mở hoàn toàn các đường dẫn Authentication và tĩnh
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()
                        .requestMatchers("/logos/**").permitAll()

                        // MỞ TOÀN BỘ ENDPOINT GET (Bao gồm cả có v1 và không có v1) CHO NGƯỜI DÙNG VÃN CẢNH
                        .requestMatchers(HttpMethod.GET, "/api/flights/**", "/api/v1/flights/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/airports/**", "/api/v1/airports/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/airlines/**", "/api/v1/airlines/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/weather/**", "/api/v1/weather/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/realtime-flights/**", "/api/v1/realtime-flights/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**", "/api/v1/dashboard/**").permitAll()

                        // Phân quyền bảo mật cho ADMIN thao tác ghi/xóa dữ liệu
                        .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**", "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**", "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**", "/api/v1/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                // Đăng ký bộ lọc Token JWT
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toList());

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Mở rộng tối đa Header để vượt qua các bộ kiểm tra của trình duyệt
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        config.setExposedHeaders(Arrays.asList("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache kết quả Preflight trong 1 giờ để tăng tốc độ tải trang

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}