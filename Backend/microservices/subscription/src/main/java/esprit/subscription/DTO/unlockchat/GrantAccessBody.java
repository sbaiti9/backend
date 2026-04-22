package esprit.subscription.DTO.unlockchat;

import lombok.Data;

/** Corps pour {@code POST /api/unlock-chat/admin/grant-access} (évite les soucis de proxy sur les chemins longs). */
@Data
public class GrantAccessBody {
    private Long sessionId;
    private String adminUsername;
}
