package com.skytrack.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "flight_subscriptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "flight_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}