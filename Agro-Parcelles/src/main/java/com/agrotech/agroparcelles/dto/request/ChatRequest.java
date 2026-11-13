package com.agrotech.agroparcelles.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.Map;

/** Question posée à l'assistant IA, avec l'historique immédiat de la conversation (optionnel). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatRequest {

    @NotBlank(message = "Le message est obligatoire")
    @Schema(example = "Est-ce que je peux semer du maïs maintenant ?")
    private String message;

    @Schema(description = "Messages précédents de la conversation, role = user|assistant")
    @Builder.Default
    private List<Map<String, String>> historique = List.of();
}
