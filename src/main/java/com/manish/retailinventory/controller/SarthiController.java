package com.manish.retailinventory.controller;

import com.manish.retailinventory.dto.SarthiDtos.*;
import com.manish.retailinventory.service.SarthiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Any logged-in user may talk to Sarthi. What it can reveal depends on the user's role. */
@RestController
@RequestMapping("/api/v1/sarthi")
@RequiredArgsConstructor
public class SarthiController {
    private final SarthiService service;

    @GetMapping("/status")
    public StatusResponse status() {
        return service.status();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return service.chat(request);
    }
}
