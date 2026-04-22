package tn.esprit.training.chat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.training.chat.dto.ChatRequest;
import tn.esprit.training.chat.dto.ChatResponse;

@RestController
@RequestMapping("/api/chat")
public class ChatSimpleController {
    private final ChatSimpleService service;

    public ChatSimpleController(ChatSimpleService service) {
        this.service = service;
    }

    @PostMapping("/simple")
    public ResponseEntity<ChatResponse> simple(@RequestBody ChatRequest req) {
        String msg = req != null ? req.getMessage() : null;
        return ResponseEntity.ok(service.answer(msg));
    }
}

