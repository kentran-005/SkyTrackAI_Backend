package com.skytrack.ai.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(httpBasic -> httpBasic.disable()) // Tắt popup mật khẩu
                .formLogin(formLogin -> formLogin.disable()) // Tắt form login mặc định
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Cho phép tất cả request OPTIONS (CORS Preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()

                        // API Search cho phép ai cũng gọi
                        .requestMatchers(HttpMethod.GET, "/api/flights/search").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/airports/**", "/api/airlines/**", "/api/flights/**", "/api/weather/**", "/api/realtime-flights/**", "/api/dashboard/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/airports/**", "/api/airlines/**", "/api/flights/**", "/api/passengers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/airports/**", "/api/airlines/**", "/api/flights/**", "/api/passengers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/airports/**", "/api/airlines/**", "/api/flights/**", "/api/passengers/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
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
        // Cho phép frontend NextJS gọi vào
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        // Cho phép tất cả method (GET, POST, PUT, DELETE, OPTIONS)
        config.setAllowedMethods(List.of("*"));
        // Cho phép tất cả headers (Đặc biệt là Authorization header của JWT)
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Áp dụng cấu hình CORS cho TẤT CẢ các đường dẫn
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}