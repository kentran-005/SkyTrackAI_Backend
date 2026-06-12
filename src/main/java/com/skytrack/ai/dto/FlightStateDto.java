package com.skytrack.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightStateDto {
    private String icao24;        // Mã hex duy nhất của máy bay
    private String callsign;      // Số hiệu chuyến bay (VD: VNA123)
    private String originCountry; // Quốc gia xuất phát
    private Double longitude;     // Kinh độ
    private Double latitude;      // Vĩ độ
    private Double altitude;      // Độ cao (mét)
    private Double velocity;      // Vận tốc (m/s)
    private Double heading;       // Hướng bay (độ)
    private Boolean onGround;     // Đang trên mặt đất?
}