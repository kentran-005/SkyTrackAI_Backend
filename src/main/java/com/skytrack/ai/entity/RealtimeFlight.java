package com.skytrack.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "realtime_flights")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeFlight {

    @Id
    // icao24 là mã thiết bị phát sóng của máy bay, dùng làm ID là hợp lý nhất
    private String icao24;

    private String callsign;
    private String originCountry;
    private Double longitude;
    private Double latitude;
    private Double altitude;
    private Double velocity;
    private Double heading;
    private Boolean onGround;

    // Thời gian cập nhật để biết dữ liệu này đã cũ chưa
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}