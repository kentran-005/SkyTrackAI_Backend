package com.skytrack.ai.dto;

import java.util.List;

public record RealtimeFlightSnapshotDto(
        List<FlightStateDto> flights,
        RealtimeFlightStatusDto status
) {
}
