package esprit.subscription.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Document;

@Entity
@Table(name = "pricing_plans")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Document(indexName = "pricing_plans")
public class PricingPlan {

    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    @org.springframework.data.annotation.Id
    private String esId;

    @Column(nullable = false)
    private String name; // BASIC/PRO/PREMIUM

    private String description;

    @Column(nullable = false)
    private Double monthlyPrice;

    @Column(nullable = false)
    private Double yearlyPrice;

    @Column(length = 2000)
    private String features; // Comma-separated or JSON string

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Boolean highlight; // Marks recommended plan


}
