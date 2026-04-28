package esprit.subscription.client;

import esprit.subscription.DTO.UserProfileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user", contextId = "userProfileClient", path = "/user")
public interface UserClient {

    @GetMapping("/users/{id}")
    UserProfileDto getUserById(@PathVariable("id") Long id);
}
