package com.example.demo;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FitnessTrackerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @Order(1)
    void getWorkouts_shouldReturn200AndList() {
        ResponseEntity<List> response = restTemplate.getForEntity(baseUrl() + "/api/workouts", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(2)
    void createWorkout_shouldReturn200AndPersistedEntity() {
        Map<String, Object> body = Map.of(
            "type", "Running",
            "duration", 30,
            "caloriesBurned", 300,
            "notes", "Integration test run"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/api/workouts",
            new HttpEntity<>(body, headers),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("type")).isEqualTo("Running");
        assertThat(response.getBody().get("duration")).isEqualTo(30);
    }

    @Test
    @Order(3)
    void getWorkoutById_shouldReturn200ForExistingWorkout() {
        // Create one first
        Map<String, Object> body = Map.of("type", "Cycling", "duration", 45, "caloriesBurned", 400, "notes", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> created = restTemplate.postForEntity(
            baseUrl() + "/api/workouts", new HttpEntity<>(body, headers), Map.class);

        Integer id = (Integer) created.getBody().get("id");

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/api/workouts/" + id, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("type")).isEqualTo("Cycling");
    }

    @Test
    @Order(4)
    void getWorkoutById_shouldReturn404ForMissingWorkout() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/api/workouts/99999", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void updateWorkout_shouldReturn200WithUpdatedValues() {
        Map<String, Object> body = Map.of("type", "Swimming", "duration", 60, "caloriesBurned", 500, "notes", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> created = restTemplate.postForEntity(
            baseUrl() + "/api/workouts", new HttpEntity<>(body, headers), Map.class);
        Integer id = (Integer) created.getBody().get("id");

        Map<String, Object> update = Map.of("type", "Swimming", "duration", 75, "caloriesBurned", 600, "notes", "Updated");
        restTemplate.put(baseUrl() + "/api/workouts/" + id, new HttpEntity<>(update, headers));

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/api/workouts/" + id, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("duration")).isEqualTo(75);
    }

    @Test
    @Order(6)
    void deleteWorkout_shouldReturn200ThenNotFound() {
        Map<String, Object> body = Map.of("type", "Yoga", "duration", 20, "caloriesBurned", 100, "notes", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> created = restTemplate.postForEntity(
            baseUrl() + "/api/workouts", new HttpEntity<>(body, headers), Map.class);
        Integer id = (Integer) created.getBody().get("id");

        restTemplate.delete(baseUrl() + "/api/workouts/" + id);

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/api/workouts/" + id, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(7)
    void healthEndpoint_shouldReturn200() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/api/workouts/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("running");
    }

    @Test
    @Order(8)
    void actuatorHealth_shouldReturnUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/actuator/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }
}
