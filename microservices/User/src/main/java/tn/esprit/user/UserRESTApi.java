package tn.esprit.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserRESTApi {
    @GetMapping("/hello")
    public String sayhello() {
        return "Hello World from User";
    }
}
