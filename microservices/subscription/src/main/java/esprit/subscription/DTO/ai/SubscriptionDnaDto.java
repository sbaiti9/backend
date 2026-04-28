package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDnaDto {
    private Long userId;
    private String archetype;
    private String archetypeLabel;
    private String color;
    private String strategy;
    @Builder.Default
    private List<String> signals = new ArrayList<>();
}
