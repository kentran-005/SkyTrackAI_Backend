package com.skytrack.ai.service.impl;

import com.skytrack.ai.entity.Airline;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.AirlineRepository;
import com.skytrack.ai.service.AirlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AirlineServiceImpl implements AirlineService {

    private final AirlineRepository airlineRepository;

    @Value("${app.upload.dir:uploads/logos/}")
    private String uploadDir;


    @Override
    public List<Airline> getAllAirlines() {
        return airlineRepository.findAll();
    }

    @Override
    public Airline createAirline(Airline airline) {
        return airlineRepository.save(airline);
    }

    @Override
    public Airline updateAirline(Long id, Airline airline) {
        Airline existing = airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airline not found"));
        existing.setCode(airline.getCode());
        existing.setName(airline.getName());
        existing.setLogo(airline.getLogo());
        return airlineRepository.save(existing);
    }

    @Override
    public void deleteAirline(Long id) {
        if (!airlineRepository.existsById(id)) {
            throw new ResourceNotFoundException("Airline not found");
        }
        airlineRepository.deleteById(id);
    }

    @Override
    public String updateLogo(Long id, MultipartFile file) {
        // 1. Tìm airline
        Airline existing = airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airline not found"));

        // 2. Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !List.of("image/png", "image/jpeg", "image/webp", "image/svg+xml")
                .contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type");
        }

        try {

            String oldLogo = existing.getLogo();
            if (oldLogo != null && oldLogo.startsWith("/logos/")) {
                Path oldFile = Paths.get(uploadDir)
                        .toAbsolutePath()
                        .normalize()
                        .resolve(oldLogo.replace("/logos/", ""))
                        .normalize();
                Files.deleteIfExists(oldFile);
            }

            // 3. Tạo tên file unique: vd "1_1718612345678.png"
            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            if (ext == null || ext.isBlank()) {
                ext = switch (contentType) {
                    case "image/png" -> "png";
                    case "image/jpeg" -> "jpg";
                    case "image/webp" -> "webp";
                    case "image/svg+xml" -> "svg";
                    default -> throw new IllegalArgumentException("Invalid file extension");
                };
            }
            String fileName = id + "_" + System.currentTimeMillis() + "." + ext;

            // 4. Tạo thư mục nếu chưa có & lưu file
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            Path targetFile = uploadPath.resolve(fileName).normalize();
            if (!targetFile.startsWith(uploadPath)) {
                throw new IllegalArgumentException("Invalid logo file name");
            }
            Files.copy(file.getInputStream(),
                    targetFile,
                    StandardCopyOption.REPLACE_EXISTING);

            // 5. Cập nhật DB
            String logoUrl = "/logos/" + fileName;
            existing.setLogo(logoUrl);
            airlineRepository.save(existing);

            return logoUrl;

        } catch (IOException e) {
            throw new RuntimeException("Failed to save logo: " + e.getMessage());
        }
    }
}
