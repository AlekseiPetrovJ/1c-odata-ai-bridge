package ru.petrov.odata_bridge.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.petrov.odata_bridge.config.IndexingConfig;
import ru.petrov.odata_bridge.model.FieldInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Сервис управления векторным индексом и контекстным поиском.
 */
@Service
public class IndexingService {
    @Value("${spring.ai.vectorstore.pgvector.table-name}")
    private String vectorTableName;
    private final VectorStore vectorStore;
    private final ODataService odataService;
    private final IndexingConfig config;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IndexingService.class);


    public IndexingService(VectorStore vectorStore, ODataService odataService, IndexingConfig config, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.odataService = odataService;
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Запустите этот метод, чтобы наполнить базу.
    public void updateMetadataIndex() {
        // Получаем вектора строго в одном потоке
        if (!isIndexing.compareAndSet(false, true)) {
            log.warn("Индексация уже запущена другим потоком!");
            return;
        }

        try {
            log.info("Очистка таблицы метаданных: {}", vectorTableName);
            jdbcTemplate.execute("TRUNCATE TABLE " + vectorTableName);

            log.info("Запуск парсинга XML метаданных из 1С ...");
            List<FieldInfo> fields = odataService.parseXmlMetadata();

            log.info("Парсинг завершен. Подготовка {} документов к векторизации...", fields.size());
            List<Document> allDocs = fields.stream()
                    .map(this::toDocument)
                    .toList();

            int size = allDocs.size();
            int batchSize = config.batchSize();

            for (int i = 0; i < size; i += batchSize) {
                int end = Math.min(i + batchSize, size);
                List<Document> batch = allDocs.subList(i, end);

                try {
                    vectorStore.add(batch);
                    log.info("Успешно проиндексировано: {}/{}", end, size);
                } catch (Exception e) { // Ловим ошибки БД или Ollama
                    log.error("Ошибка при индексации порции {} - {}: {}", i, end, e.getMessage());
                    // Добавляем небольшую паузу, 2 сек,
                    // если ошибка вызвана перегревом CPU или таймаутом Ollama
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        } catch (DataAccessException e) {
            throw new RuntimeException(e);
        } finally {
            isIndexing.set(false); // Всегда освобождаем флаг
        }
    }

    private Document toDocument(FieldInfo field) {
        Map<String, Object> metadata = Map.of(
                "entity", field.entity(),
                "field", field.name(),
                "type", field.type(),
                "is_header", field.isHeader()
        );
        return new Document(field.description(), metadata);
    }

    public String findRelevantMetadata(String userQuery) {
        // Выполняем поиск
        List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuery)
                        .topK(config.topK())
                        .similarityThreshold(config.similarityThreshold())
                        .build()
        );

        String collect = similarDocs.stream()
                .map(doc -> String.format("Таблица: %s, Поле: %s (Описание: %s)",
                        doc.getMetadata().get("entity"),
                        doc.getMetadata().get("field"),
                        doc.getText()))
                .collect(Collectors.joining("\n"));
        log.info("Контекст из DB: {} \n Конец контекста",collect);
        return collect;
    }

    /**
     * Этап 1: Поиск наиболее подходящей таблицы по запросу пользователя.
     * Использует фильтрацию по метаданным (is_header = true) для исключения шума.
     *
     * @param query Строка запроса пользователя.
     * @return Текст мастер-записи найденной сущности.
     */
    public String findEntityHeader(String query) {
        return vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(3)
                                .filterExpression("is_header == true").build()
                ).stream()
                .map(doc -> doc.getText() + " [ID: " + doc.getMetadata().get("entity") + "]")
                .collect(Collectors.joining("\n"));
    }

    /**
     * Этап 2: Получение подробной структуры полей для конкретной сущности.
     * Ограничивает контекст LLM только релевантными полями одной таблицы.
     *
     * @param entityName Техническое имя сущности, полученное на первом этапе.
     * @return Список описаний полей в формате "Имя: Описание".
     */
    public String findFieldsForEntity(String entityName) {
        SearchRequest request = SearchRequest.builder()
                .query(entityName)
                .topK(15) // Берем больше полей, но только одной таблицы
                .filterExpression("entity == '" + entityName + "'")
                .build();
        return vectorStore.similaritySearch(request).stream()
                .map(doc -> doc.getMetadata().get("field") + ": " + doc.getText())
                .collect(Collectors.joining("\n"));
    }

    public String getAllEntitiesHelp() {
        SearchRequest request = SearchRequest.builder()
                .query("справочники и документы") // Общий запрос
                .filterExpression("is_header == true") // Только заголовки
                .topK(20) // Лимит списка
                .build();

        return vectorStore.similaritySearch(request).stream()
                .map(doc -> "- " + doc.getText())
                .distinct()
                .collect(Collectors.joining("\n"));
    }
}
