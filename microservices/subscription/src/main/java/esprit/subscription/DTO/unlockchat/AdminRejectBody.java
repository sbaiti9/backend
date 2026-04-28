package esprit.subscription.DTO.unlockchat;

import lombok.Data;

@Data
public class AdminRejectBody {
    private String adminUsername;
    private String reason;
}
