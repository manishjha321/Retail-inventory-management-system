package com.manish.retailinventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class SarthiDtos {
    public record ChatMessage(@NotBlank String role, @NotBlank @Size(max = 4000) String content) {}

    public record ChatRequest(
            @NotBlank @Size(max = 1000) String message,
            @Size(max = 30) @Valid List<ChatMessage> history) {}

    public record ChatResponse(String reply, List<String> toolsUsed) {}

    public record StatusResponse(boolean enabled, String name, String model) {}
}
