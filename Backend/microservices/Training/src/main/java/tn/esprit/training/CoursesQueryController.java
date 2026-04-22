package tn.esprit.training;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.training.service.CoursesQueryService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CoursesQueryController {
    private final CoursesQueryService service;

    public CoursesQueryController(CoursesQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCourses(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "levels", required = false) String levels,
            @RequestParam(value = "priceLevel", defaultValue = "ALL") String priceLevel,
            @RequestParam(value = "sort", defaultValue = "NEWEST") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "9") int size
    ) {
        List<String> cats = categories != null && !categories.isBlank()
                ? Arrays.stream(categories.split(",")).map(String::trim).toList()
                : List.of();
        List<String> lvls = levels != null && !levels.isBlank()
                ? Arrays.stream(levels.split(",")).map(String::trim).toList()
                : List.of();
        if (size > 50) size = 50;
        Map<String, Object> resp = service.query(q, cats, lvls, priceLevel, sort, page, size);
        return ResponseEntity.ok(resp);
    }
}

