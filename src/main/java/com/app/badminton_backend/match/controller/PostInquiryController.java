package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.match.service.PostInquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/post-inquiry")
@RequiredArgsConstructor
public class PostInquiryController {

    private final PostInquiryService postInquiryService;

    /**
     * GET /post-inquiry/{postId}/thread
     * Returns all messages in the inquiry thread for this post, chronologically.
     * Accessible by the organizer and any user with a join request.
     */
    @GetMapping("/{postId}/thread")
    public ResponseEntity<List<PostInquiryService.InquiryMessageDto>> getThread(
            @PathVariable UUID postId) {
        return ResponseEntity.ok(postInquiryService.getThread(postId));
    }

    /**
     * POST /post-inquiry/{postId}/send
     * Sends a message to the inquiry thread.
     * Body: { "content": "..." }
     */
    @PostMapping("/{postId}/send")
    public ResponseEntity<PostInquiryService.InquiryMessageDto> sendMessage(
            @PathVariable UUID postId,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        return ResponseEntity.ok(postInquiryService.sendMessage(postId, content));
    }
}
