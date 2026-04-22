package tn.esprit.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/event")
public class EventRESTApi {
    @GetMapping("/hello")
    public String sayhello() {
        return "Hello World from Event";
    }
}
