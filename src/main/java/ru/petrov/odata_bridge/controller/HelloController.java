package ru.petrov.odata_bridge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.petrov.odata_bridge.service.AIService;
import ru.petrov.odata_bridge.service.IndexingService;
import ru.petrov.odata_bridge.service.ODataService;

@RestController
public class HelloController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HelloController.class);
    private ODataService oDataService;
    private AIService aiService;
    private IndexingService indexingService;
    //Внедряем на время тестов для проверки поиска с применением RAG
//    private final ChatClient chatClient;


    public HelloController(
//            ChatClient.Builder builder,
            IndexingService indexingService, AIService aiService, ODataService oDataService) {
//        this.chatClient = builder.build();
        this.indexingService = indexingService;
        this.aiService = aiService;
        this.oDataService = oDataService;
    }

//    @GetMapping("api/status")
//    public List<Kontragent> checkStatus() {
//        log.info("Получен запрос на проверку статуса приложения");
//        return oDataService.fetchTopKontragents(7);
//    }

    @GetMapping("/api/ai")
    public String askAi(@RequestParam(value = "prompt", defaultValue = "Расскажи анекдот про программиста") String prompt) {
        log.info("Запрос к ИИ: {}", prompt);
        return aiService.getOllamaResponse(prompt);
    }
    @GetMapping("/api/ai/ask")
    public String smartAsk(@RequestParam String prompt) {
        return aiService.getSmartResponse(prompt);
    }

    @GetMapping("/api/admin/reindex")
    public String reindexMetadata() {
        log.info("Запущен ручной процесс переиндексации метаданных 1С");
        try {
            indexingService.updateMetadataIndex();
            return "Индексация успешно завершена. Проверьте логи для деталей.";
        } catch (Exception e) {
            log.error("Ошибка при индексации: ", e);
            return "Ошибка при индексации: " + e.getMessage();
        }
    }

//    @GetMapping("/api/ai/test-rag")
//    public String testRag(@RequestParam String prompt) {
//        // 1. Извлекаем знания из вашей векторной базы
//        String context = indexingService.findRelevantMetadata(prompt);
//        // 2. Отправляем вопрос + контекст в Ollama
//        return chatClient.prompt()
//                .system("Ты помощник по 1С. Используй эти данные о структуре базы, чтобы ответить на вопрос:\n" + context)
//                .user(prompt)
//                .call()
//                .content();
//    }

}
