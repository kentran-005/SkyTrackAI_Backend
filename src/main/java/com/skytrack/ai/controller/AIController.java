package com.skytrack.ai.controller;

import com.skytrack.ai.external.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final GeminiService geminiService;

    // API Chat: Frontend gửi lên câu hỏi, Backend trả lời
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chatWithAI(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        String answer = geminiService.askGemini(question);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    // API Summary: Tóm tắt hoạt động sân bay
    @GetMapping("/summary")
    public ResponseEntity<Map<String, String>> getDailySummary() {
        // câu lệnh (prompt) để AI tóm tắt
        String prompt = "Hãy đóng vai trò là một hệ thống quản lý sân bay thông minh. " +
                "Hãy đưa ra một bản tóm tắt ngắn gọn (khoảng 3 câu) về tình hình chuyến bay hôm nay " +
                "với các số liệu giả định như: 1500 chuyến bay, 87 chuyến delay, 12 chuyến hủy, tỷ lệ đúng giờ 94%. " +
                "Trả lời bằng tiếng Việt.";

        String summary = geminiService.askGemini(prompt);
        return ResponseEntity.ok(Map.of("summary", summary));
    }
}