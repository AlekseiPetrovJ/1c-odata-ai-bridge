package ru.petrov.odata_bridge.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AIService {
    private final ChatClient chatClient;

    public AIService(ChatClient.Builder chatClientBuilder, ODataService oDataService) {
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
//                .system(systemPromt)
                .user(message)
                .call()
                .content();
    }
}
