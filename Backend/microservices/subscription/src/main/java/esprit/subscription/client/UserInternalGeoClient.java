package esprit.subscription.client;

import esprit.subscription.DTO.GeoEventIngestBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user", contextId = "userInternalGeo", path = "/user", configuration = GeoInternalFeignConfig.class)
public interface UserInternalGeoClient {

    @PostMapping("/internal/geo-event")
    void postGeoEvent(@RequestBody GeoEventIngestBody body);
}
