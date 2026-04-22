package esprit.subscription.DTO;

import lombok.Data;

@Data
public class GeoEventIngestBody {
    private Long userId;
    private String eventType = "PAYMENT";
    private String clientIp;
}
