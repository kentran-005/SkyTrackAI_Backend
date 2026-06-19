package com.skytrack.ai.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    // Tự động load danh sách origin từ application.properties, nếu không có sẽ lấy danh sách mặc định (đã add thêm link Vercel công khai của bạn)
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,https://manager-skytrack-ai.vercel.app}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(httpBasic -> httpBasic.disable()) // Tắt popup mật khẩu mặc định
                .formLogin(formLogin -> formLogin.disable()) // Tắt form login mặc định
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
                            response.getWriter().write("{\"error\":\"Mày đòi hack à? 😂\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Cho phép tất cả request OPTIONS (CORS Preflight) đi qua
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Các route tĩnh và authentication tự do truy cập
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()
                        .requestMatchers("/logos/**").permitAll()

                        // ĐỒNG BỘ TIỀN TỐ /api/v1/ ĐỂ TRÁNH LỖI 401 UNAUTHORIZED
                        .requestMatchers(HttpMethod.GET, "/api/v1/flights/search").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/flights",
                                "/api/v1/flights/**",
                                "/api/v1/airports/**",
                                "/api/v1/airlines/**",
                                "/api/v1/weather/**",
                                "/api/v1/realtime-flights/**",
                                "/api/v1/dashboard/**"
                        ).permitAll()

                        // Giữ lại fallback cũ phòng trường hợp code của bạn có cả route không có v1
                        .requestMatchers(HttpMethod.GET, "/api/flights/search").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/flights",
                                "/api/flights/**",
                                "/api/airports/**",
                                "/api/airlines/**",
                                "/api/weather/**",
                                "/api/realtime-flights/**",
                                "/api/dashboard/**"
                        ).permitAll()

                        // Phân quyền cho ADMIN (Cập nhật đồng bộ cả 2 loại url có v1 và không v1 cho chắc chắn)
                        .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**", "/api/v1/users/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/airports/**", "/api/v1/airports/**", "/api/airlines/**", "/api/v1/airlines/**", "/api/flights/**", "/api/v1/flights/**", "/api/passengers/**", "/api/v1/passengers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/airports/**", "/api/v1/airports/**", "/api/airlines/**", "/api/v1/airlines/**", "/api/flights/**", "/api/v1/flights/**", "/api/passengers/**", "/api/v1/passengers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/airports/**", "/api/v1/airports/**", "/api/airlines/**", "/api/v1/airlines/**", "/api/flights/**", "/api/v1/flights/**", "/api/passengers/**", "/api/v1/passengers/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
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

        // Tách chuỗi origins và xử lý khoảng trắng
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toList());

        config.setAllowedOrigins(origins);

        // Cho phép các HTTP Methods truyền thống và hiện đại
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Explicitly khai báo các Headers thay vì dùng "*" để tránh một số trình duyệt kén chọn khi allowCredentials = true
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        config.setExposedHeaders(Arrays.asList("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}