package com.finex.api.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal developer/administrative health endpoint (Master Plan §27). This is not part of
 * the HFT hot path; it exists to prove the service boots and can reach PostgreSQL.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        String dbStatus;
        boolean dbUp;
        try (Connection connection = dataSource.getConnection()) {
            dbUp = connection.isValid(2);
            dbStatus = dbUp ? "UP" : "DOWN";
        } catch (SQLException e) {
            dbUp = false;
            dbStatus = "DOWN";
        }

        Map<String, Object> body = Map.of(
                "status", dbUp ? "UP" : "DEGRADED",
                "db", dbStatus
        );
        return dbUp ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
    }
}
