package com.skytrack.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads/logos/}")
    private String uploadDir;

    private Path logoDirectory;

    @PostConstruct
    public void initializeUploadDirectory() {
        logoDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(logoDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize logo upload directory: " + logoDirectory, exception);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = logoDirectory.toUri().toString();
        if (!resourceLocation.endsWith("/")) resourceLocation += "/";

        registry.addResourceHandler("/logos/**")
                .addResourceLocations(resourceLocation)
                .setCachePeriod(3600);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/logos/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET");
    }
}
