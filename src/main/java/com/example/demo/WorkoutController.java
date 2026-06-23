package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
class WorkoutController {

    @Autowired
    private WorkoutRepository workoutRepository;

    @GetMapping("/workouts")
    public List<Workout> getAllWorkouts() {
        return workoutRepository.findAll();
    }

    @GetMapping("/workouts/{id}")
    public ResponseEntity<Workout> getWorkoutById(@PathVariable Long id) {
        Workout workout = workoutRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Workout not found with id " + id));
        return ResponseEntity.ok(workout);
    }

    @PostMapping("/workouts")
    public Workout createWorkout(@Valid @RequestBody Workout workout) {
        return workoutRepository.save(workout);
    }

    @PutMapping("/workouts/{id}")
    public ResponseEntity<Workout> updateWorkout(@PathVariable Long id, @Valid @RequestBody Workout workoutDetails) {
        Workout workout = workoutRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Workout not found with id " + id));

        workout.setTitle(workoutDetails.getTitle());
        workout.setDescription(workoutDetails.getDescription());
        workout.setDate(workoutDetails.getDate());

        Workout updatedWorkout = workoutRepository.save(workout);
        return ResponseEntity.ok(updatedWorkout);
    }

    @DeleteMapping("/workouts/{id}")
    public ResponseEntity<?> deleteWorkout(@PathVariable Long id) {
        Workout workout = workoutRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Workout not found with id " + id));

        workoutRepository.delete(workout);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/info")
    public Map<String, String> getAppInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("app", "WorkoutTracker");
        info.put("version", "1.0.0");
        return info;
    }
}