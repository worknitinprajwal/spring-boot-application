package com.example.demo;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutTest {

    @Test
    void testWorkoutCreation() {
        LocalDateTime now = LocalDateTime.now();
        Workout workout = new Workout(1L, "Running", 30, 300, now, "Morning run");

        assertEquals(1L, workout.getId());
        assertEquals("Running", workout.getType());
        assertEquals(30, workout.getDuration());
        assertEquals(300, workout.getCaloriesBurned());
        assertEquals(now, workout.getDate());
        assertEquals("Morning run", workout.getNotes());
    }

    @Test
    void testWorkoutSetters() {
        Workout workout = new Workout();
        LocalDateTime now = LocalDateTime.now();

        workout.setId(2L);
        workout.setType("Cycling");
        workout.setDuration(45);
        workout.setCaloriesBurned(400);
        workout.setDate(now);
        workout.setNotes("Evening ride");

        assertEquals(2L, workout.getId());
        assertEquals("Cycling", workout.getType());
        assertEquals(45, workout.getDuration());
        assertEquals(400, workout.getCaloriesBurned());
        assertEquals(now, workout.getDate());
        assertEquals("Evening ride", workout.getNotes());
    }

    @Test
    void testWorkoutEquality() {
        LocalDateTime now = LocalDateTime.now();
        Workout workout1 = new Workout(1L, "Running", 30, 300, now, "Morning run");
        Workout workout2 = new Workout(1L, "Running", 30, 300, now, "Morning run");

        assertEquals(workout1, workout2);
        assertEquals(workout1.hashCode(), workout2.hashCode());
    }

    @Test
    void testWorkoutToString() {
        LocalDateTime now = LocalDateTime.now();
        Workout workout = new Workout(1L, "Running", 30, 300, now, "Morning run");

        String toString = workout.toString();
        assertTrue(toString.contains("Running"));
        assertTrue(toString.contains("30"));
        assertTrue(toString.contains("300"));
    }

    @Test
    void testNoArgsConstructor() {
        Workout workout = new Workout();
        assertNotNull(workout);
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Workout workout = new Workout(1L, "Swimming", 60, 500, now, "Pool session");

        assertNotNull(workout);
        assertEquals("Swimming", workout.getType());
    }
}
