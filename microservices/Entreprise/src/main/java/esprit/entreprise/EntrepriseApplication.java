package esprit.entreprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@EnableCaching
@EnableJpaRepositories(basePackages = "esprit.entreprise.repository")
@EntityScan(basePackages = "esprit.entreprise.entity")
public class EntrepriseApplication {

	public static void main(String[] args) {
		SpringApplication.run(EntrepriseApplication.class, args);
	}

}
