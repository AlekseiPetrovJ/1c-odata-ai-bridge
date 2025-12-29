package ru.petrov.odata_bridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.petrov.odata_bridge.config.IndexingConfig;
import ru.petrov.odata_bridge.config.ODataConfig;
import ru.petrov.odata_bridge.model.FieldInfo;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ODataService {
    private final WebClient webClient;
    private final IndexingConfig indexingConfig;


    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ODataService.class);

    public ODataService(IndexingConfig indexingConfig, ODataConfig oDataConfig) {
        this.indexingConfig = indexingConfig;
        String auth = oDataConfig.username() + ":" + oDataConfig.password();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        // Устанавливаем лимит, например, 50 МБ (50 * 1024 * 1024)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .baseUrl(oDataConfig.baseUrl())
                .defaultHeader("Authorization", "Basic " + encodedAuth)
                .exchangeStrategies(strategies) // Применяем стратегию
                .filter(logRequest())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info(">>>> ЗАПРОС К 1С: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    /**
     * Универсальный инструмент (Tool) для выполнения запросов к 1С.
     * Поддерживает фильтрацию, лимитирование и агрегацию (count).
     *
     * @param entity    Имя таблицы 1С.
     * @param filter    Строка фильтрации в формате OData (опционально).
     * @param top       Количество возвращаемых записей.
     * @param countOnly Если true, возвращает только количество записей через /$count.
     * @return Ответ от 1С в виде строки (JSON или число).
     */
    @Tool(
            name = "executeSmartQuery",
            returnDirect = true, // результат метода сразу возвращаем пользователю
            description = "Универсальный запрос к 1С. Параметры (entity, filter) нужно брать из базы знаний метаданных.")
    public Object executeSmartQuery(
            @ToolParam(description = "Имя сущности из метаданных (напр. Catalog_Контрагенты)") String entity,
            @ToolParam(description = "Фильтр OData (напр. ИНН eq '12345' или Number eq '001')") String filter,
            @ToolParam(description = "Лимит записей (по умолчанию 5)") Integer top,
            @ToolParam(description = "Только если нужен подсчет количества (Boolean)") Boolean countOnly
    ) {
        boolean isCount = Boolean.TRUE.equals(countOnly);
        log.info("[AI TOOL CALL] Метод: executeSmartQuery | Сущность: {} | Фильтр: {} | Лимит: {} | count {}",
                entity, filter, top, countOnly);
        // Определяем лимит
        int limit = (top != null) ? top : 5;

        return webClient.get()
                .uri(uriBuilder -> {
                    // Если счетчик — добавляем /$count к пути
                    uriBuilder.path(isCount ? entity + "/$count" : entity);
                    if (!isCount) {
                        uriBuilder
                                .queryParam("$top", limit)
                                .queryParam("$format", "json");
                    }
                    // Добавляем фильтр только если он передан и не пуст
                    if (filter != null && !filter.isBlank()) {
                        uriBuilder.queryParam("$filter", filter);
                    }

                    return uriBuilder.build();
                })
                .retrieve()
                // Используем ParameterizedTypeReference для десериализации JSON в список Map
                .bodyToMono(String.class) // Получаем СНАЧАЛА всё как строку (и JSON, и цифру)
                .flatMap(body -> {
                    if (isCount) return Mono.just(body);

                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        mapper.enable(SerializationFeature.INDENT_OUTPUT);

                        JsonNode root = mapper.readTree(body);

                        // Проверяем наличие ключа "value"
                        if (root.has("value")) {
                            JsonNode valueNode = root.get("value");
                            // Форматируем ТОЛЬКО содержимое массива value
                            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valueNode);
                            return Mono.just(prettyJson);
                        }

                        // Если ключа value нет (одиночный объект), форматируем всё, но без лишних полей
                        String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                        return Mono.just(prettyJson);

                    } catch (Exception e) {
                        log.error("JSON formatting error: {}", e.getMessage());
                        return Mono.just("```json\n" + body + "\n```");
                    }
                })
                .block();
    }

    public List<FieldInfo> parseXmlMetadata() {
        List<FieldInfo> fields = new ArrayList<>();

        // Получаем поток XML от 1С
        // В ODataService.parseXmlMetadata()
        InputStream is = webClient.get()
                .uri("$metadata")
                .accept(MediaType.APPLICATION_XML) // Явно просим XML
                .retrieve()
                .bodyToMono(org.springframework.core.io.buffer.DataBuffer.class)
                .map(dataBuffer -> dataBuffer.asInputStream(true)) // true — освободить буфер после чтения
                .block();


        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Отключаем внешние сущности для безопасности
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(is);

            String currentEntity = null;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();

                    if ("EntityType".equals(localName)) {
                        // Нашли начало описания таблицы (например, Catalog_Контрагенты)
                        String entityName = reader.getAttributeValue(null, "Name");

                        // 1. Фильтрация ТАБЛИЦ (Белый и Черный списки сущностей)
                        boolean isExcluded = indexingConfig.excludeEntities().stream().anyMatch(entityName::contains);
                        boolean isAllowed = indexingConfig.includeOnly() == null || indexingConfig.includeOnly().isEmpty() ||
                                indexingConfig.includeOnly().stream().anyMatch(entityName::equals);

                        currentEntity = (isAllowed && !isExcluded) ? entityName : null;
                        if (currentEntity != null) {
                            // Создаем Мастер-запись для всей таблицы
                            String humanName = currentEntity.replace("Catalog_", "Справочник ").replace("Document_", "Документ ");
                            fields.add(new FieldInfo(currentEntity, "TABLE_HEADER", "System", "[СУЩНОСТЬ] " + humanName, true));
                        }

                    } else if ("Property".equals(localName) && currentEntity != null) {
                        // Нашли поле внутри текущей таблицы
                        String fieldName = reader.getAttributeValue(null, "Name");
                        String fieldType = reader.getAttributeValue(null, "Type");

                        // 2. Фильтрация конкретных полей (например, Ref_Key)
                        if (indexingConfig.excludeFields().contains(fieldName)) {
                            continue;
                        }
                        // Если таблица разрешена и поле не в черном списке — индексируем
                        String cleanEntity = currentEntity.replace("Catalog_", "").replace("Document_", "");
                        String fieldDesc = String.format("[ПОЛЕ] %s в таблице %s", fieldName, cleanEntity);
                        fields.add(new FieldInfo(currentEntity, fieldName, fieldType, fieldDesc, false));

                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("EntityType".equals(reader.getLocalName())) {
                        currentEntity = null; // Закрыли текущую таблицу
                    }
                }
            }
            log.info("Парсинг завершен. Найдено полей: {}", fields.size());
        } catch (Exception e) {
            log.error("Ошибка парсинга метаданных: ", e);
        }
        return fields;
    }

}
