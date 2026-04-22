package esprit.subscription.client;

import esprit.subscription.DTO.CompanyDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "entreprise", path = "/entreprise")
public interface EntrepriseClient {

    @GetMapping("/companies-by-user/{userId}")
    CompanyDto getCompanyByUserId(@PathVariable("userId") Long userId);
}
