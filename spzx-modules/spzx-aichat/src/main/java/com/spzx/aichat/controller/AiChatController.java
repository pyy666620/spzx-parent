package com.spzx.aichat.controller;

import com.spzx.aichat.service.IAiChatService;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ai聊天接口")
@RestController
public class AiChatController extends BaseController {

    @Autowired
    private IAiChatService aiChatService;

    @GetMapping("/ask")
    public AjaxResult ask(@RequestParam String question) {
        String answer = aiChatService.ask(question);
        return success(answer);
    }
}