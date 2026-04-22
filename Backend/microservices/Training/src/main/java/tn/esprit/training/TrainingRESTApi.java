package tn.esprit.training;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/training")
public class TrainingRESTApi {
    @GetMapping("/hello")
    public String sayhello() {
        return "Hello World from Training";
    }

}
