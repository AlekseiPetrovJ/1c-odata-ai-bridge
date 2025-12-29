package ru.petrov.odata_bridge.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import ru.petrov.odata_bridge.config.AiConfig;

/**
 * Сервис интеллектуальной оркестрации диалога.
 * Реализует паттерн "Двухэтапный поиск" (Reasoning + RAG).
 */
@Service
public class AIService {
    private final ChatClient chatClient;
    private final AiConfig aiConfig;
    private final IndexingService indexingService;
    private final ChatMemory chatMemory;
    private final ChatClient classifierClient;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ODataService.class);


    public AIService(ChatClient.Builder chatClientBuilder,
                     ChatClient.Builder classifierClientBuilder,
                     ODataService oDataService,
                     AiConfig aiConfig,
                     IndexingService indexingService,
                     ChatMemory chatMemory) {
        this.aiConfig = aiConfig;
        this.indexingService = indexingService;
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultTools(oDataService)
                .build();
        this.classifierClient = classifierClientBuilder.build();
        System.out.println(aiConfig.systemPrompt());
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

    /**
     * Обрабатывает запрос пользователя в три шага:
     * 1. Классификация запроса и выбор целевой сущности 1С.
     * 2. Формирование изолированного контекста полей для выбранной сущности.
     * 3. Генерация и выполнение запроса к 1С через инструмент Tool Call.
     *
     * @param userPrompt Текст вопроса на естественном языке.
     * @return Ответ с данными из 1С.
     */
    public String getSmartResponse(String userPrompt) {

        // ЭТАП 1: Определение только имени сущности (Entity)
        // Ищем в RAG 1-2 самых подходящих заголовка таблиц
        String entityContext = indexingService.findEntityHeader(userPrompt);

        log.info("=== ЗАПУСК ЭТАПА 1 (КЛАССИФИКАЦИЯ) ===");
        String classificationPrompt = String.format(
                "Твоя задача — сопоставить вопрос пользователя с одной из доступных категорий. " +
                        "СПИСОК КАТЕГОРИЙ:\n%s\n\n" +
                        "ПРАВИЛО: Выведи только техническое название категории (например, Catalog_Организации). " +
                        "Отвечай СТРОГО техническим именем из скобок [ID: ...]. Не используй точки и русский язык" +
                        "Если в истории чата уже была выбрана категория, используй её. " +
                        "Ответь одним словом.", entityContext);
        String targetEntity = classifierClient.prompt()
                .user(classificationPrompt)
                .advisors(new org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor()) //Отладка запросов
                .call()
                .content()
                .trim();

        // ЭТАП 2: Получение полей ТОЛЬКО для этой таблицы и выполнение запроса
        // String fieldsContext = indexingService.findFieldsForEntity(targetEntity); // Раскомментировать для активации фильтрации и поиска по конкретным полям
        log.info("==========================================");
        log.info("=== ЗАПУСК ЭТАПА 2 (исполнение) ===");
        // Этап 2: Исполнение (вывод JSON)
        return chatClient.prompt()
                .system(s -> s.text(aiConfig.systemPrompt())
                        .param("targetEntity", targetEntity)
                        // .param("context", fieldsContext) // Раскомментировать для активации фильтрации и поиска по конкретным полям
                )
                .user(userPrompt)
                .call()
                .content();

    }
}
