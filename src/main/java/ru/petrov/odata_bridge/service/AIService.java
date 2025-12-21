package ru.petrov.odata_bridge.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import ru.petrov.odata_bridge.config.AiConfig;

@Service
public class AIService {
    private final ChatClient chatClient;
    private final AiConfig aiConfig;
    private final IndexingService indexingService;

    public AIService(ChatClient.Builder chatClientBuilder, ODataService oDataService, AiConfig aiConfig, IndexingService indexingService) {
        this.aiConfig = aiConfig;
        this.indexingService = indexingService;
        this.chatClient = chatClientBuilder
                .defaultTools(oDataService)  //Подключение инструментов с аннотацией Tool
                .build();
    }

    /**
     * Отправляет сообщение в Ollama и возвращает ответ.
     *
     * @param message Запрос пользователя.
     * @return Сгенерированный ответ от модели.
     */
    public String getOllamaResponse(String message) {
        return chatClient.prompt()
                .system(aiConfig.systemPrompt())
                .user(message)
                .call()
                .content();
    }

    public String getSmartResponse(String userPrompt) {
        // 1. RAG: Получаем технический контекст из векторов
        String context = indexingService.findRelevantMetadata(userPrompt);

        // 2. Orchestration: Формируем итоговый запрос к LLM
        return chatClient.prompt()
                .system(s -> s.text(aiConfig.systemPrompt())
                        .param("context", context)) // Динамически внедряем контекст
                .user(userPrompt)
                .call()
                .content();
    }
}
