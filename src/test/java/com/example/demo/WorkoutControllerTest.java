package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkoutController.class)
class WorkoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkoutRepository workoutRepository;

    private Workout workout1;
    private Workout workout2;

    @BeforeEach
    void setUp() {
        workout1 = new Workout(1L, "Running", 30, 300, LocalDateTime.now(), "Morning run");
        workout2 = new Workout(2L, "Cycling", 45, 400, LocalDateTime.now(), "Evening ride");
    }

    @Test
    void getAllWorkouts_ShouldReturnListOfWorkouts() throws Exception {
        when(workoutRepository.findAll()).thenReturn(Arrays.asList(workout1, workout2));

        mockMvc.perform(get("/api/workouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("Running"))
                .andExpect(jsonPath("$[1].type").value("Cycling"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(workoutRepository, times(1)).findAll();
    }

    @Test
    void getWorkoutById_WhenExists_ShouldReturnWorkout() throws Exception {
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout1));

        mockMvc.perform(get("/api/workouts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Running"))
                .andExpect(jsonPath("$.duration").value(30))
                .andExpect(jsonPath("$.caloriesBurned").value(300));

        verify(workoutRepository, times(1)).findById(1L);
    }

    @Test
    void getWorkoutById_WhenNotExists_ShouldReturn404() throws Exception {
        when(workoutRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/workouts/999"))
                .andExpect(status().isNotFound());

        verify(workoutRepository, times(1)).findById(999L);
    }

    @Test
    void createWorkout_ShouldReturnCreatedWorkout() throws Exception {
        Workout newWorkout = new Workout(null, "Swimming", 60, 500, LocalDateTime.now(), "Pool session");
        Workout savedWorkout = new Workout(3L, "Swimming", 60, 500, LocalDateTime.now(), "Pool session");

        when(workoutRepository.save(any(Workout.class))).thenReturn(savedWorkout);

        mockMvc.perform(post("/api/workouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Swimming\",\"duration\":60,\"caloriesBurned\":500,\"notes\":\"Pool session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.type").value("Swimming"));

        verify(workoutRepository, times(1)).save(any(Workout.class));
    }

    @Test
    void updateWorkout_WhenExists_ShouldReturnUpdatedWorkout() throws Exception {
        Workout updatedWorkout = new Workout(1L, "Running", 45, 450, LocalDateTime.now(), "Updated run");

        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout1));
        when(workoutRepository.save(any(Workout.class))).thenReturn(updatedWorkout);

        mockMvc.perform(put("/api/workouts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Running\",\"duration\":45,\"caloriesBurned\":450,\"notes\":\"Updated run\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration").value(45))
                .andExpect(jsonPath("$.caloriesBurned").value(450));

        verify(workoutRepository, times(1)).findById(1L);
        verify(workoutRepository, times(1)).save(any(Workout.class));
    }

    @Test
    void updateWorkout_WhenNotExists_ShouldReturn404() throws Exception {
        when(workoutRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/workouts/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Running\",\"duration\":45,\"caloriesBurned\":450}"))
                .andExpect(status().isNotFound());

        verify(workoutRepository, times(1)).findById(999L);
        verify(workoutRepository, never()).save(any(Workout.class));
    }

    @Test
    void deleteWorkout_WhenExists_ShouldReturn200() throws Exception {
        when(workoutRepository.findById(1L)).thenReturn(Optional.of(workout1));
        doNothing().when(workoutRepository).delete(any(Workout.class));

        mockMvc.perform(delete("/api/workouts/1"))
                .andExpect(status().isOk());

        verify(workoutRepository, times(1)).findById(1L);
        verify(workoutRepository, times(1)).delete(workout1);
    }

    @Test
    void deleteWorkout_WhenNotExists_ShouldReturn404() throws Exception {
        when(workoutRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/workouts/999"))
                .andExpect(status().isNotFound());

        verify(workoutRepository, times(1)).findById(999L);
        verify(workoutRepository, never()).delete(any(Workout.class));
    }

    @Test
    void health_ShouldReturnHealthMessage() throws Exception {
        mockMvc.perform(get("/api/workouts/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Fitness Tracker API is running!"));
    }
}
