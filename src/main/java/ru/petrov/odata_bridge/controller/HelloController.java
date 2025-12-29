package ru.petrov.odata_bridge.controller;

import org.springframework.http.MediaType;
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


    public HelloController(
            IndexingService indexingService, AIService aiService, ODataService oDataService) {
        this.indexingService = indexingService;
        this.aiService = aiService;
        this.oDataService = oDataService;
    }


    @GetMapping("/api/ai")
    public String simpleAsk(@RequestParam(value = "prompt", defaultValue = "Привет.") String prompt) {
        log.info("Простой запрос к ИИ: {}", prompt);
        return aiService.getOllamaResponse(prompt);
    }
    @GetMapping(value = "/api/ai/ask", produces = MediaType.APPLICATION_JSON_VALUE)
    public String smartAsk(@RequestParam String prompt) {
        // 1. Быстрая проверка на запрос справки
        String cleanPrompt = prompt.toLowerCase().trim();
        if (cleanPrompt.matches(".*(помощь|умеешь|справка|таблицы|что делать).*")) {
            return "### 📚 Доступные данные в 1С:\n" +
                    indexingService.getAllEntitiesHelp() +
                    "\n\n*Пример запроса: 'Покажи 5 складов' или 'Сколько в базе контрагентов'*";
        }
        // 2. Если не справка — то ответ ИИ
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
}
