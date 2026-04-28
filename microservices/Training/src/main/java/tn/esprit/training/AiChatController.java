package tn.esprit.training;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.training.dto.ChatRequestDTO;
import tn.esprit.training.dto.ChatResponseDTO;
import tn.esprit.training.service.AiChatService;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDTO> chat(@RequestBody ChatRequestDTO body) throws Exception {
        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        return ResponseEntity.ok(chatService.chat(message, body.getUserDisplayName()));
    }
}

