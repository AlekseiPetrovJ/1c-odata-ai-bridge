package ru.petrov.odata_bridge.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.petrov.odata_bridge.config.IndexingConfig;
import ru.petrov.odata_bridge.model.FieldInfo;
import ru.petrov.odata_bridge.model.ODataResponse;
import ru.petrov.odata_bridge.model.catalog.Kontragent;
import ru.petrov.odata_bridge.model.task.UserTask;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ODataService {
    private final WebClient webClient;
    private final IndexingConfig config;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ODataService.class);

    public ODataService(IndexingConfig config) {
        this.config = config;
        String auth = config.baseUrl() + ":" + config.password();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        // Устанавливаем лимит, например, 50 МБ (50 * 1024 * 1024)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .baseUrl(config.baseUrl())
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

    @Tool(description = "Получить топ контрагентов из базы 1С")
    public List<Kontragent> fetchTopKontragents(
            @ToolParam(description = "Количество записей для получения (по умолчанию 5)") Integer limit) {
        int topValue = (limit != null) ? limit : 5;
        log.info("Инструмент fetchTopKontragents вызван с лимитом: {}", topValue);

        return webClient.get()
                .uri("Catalog_Контрагенты?$top=5&$format=json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ODataResponse<Kontragent>>() {
                })
                .map(ODataResponse::getValue)
                .block(); // .block() допустим для простого MVP
    }

    @Tool(description = "Получить топ задач из базы 1С")
    public List<UserTask> fetchTasks() {
        log.info("Выполнение запроса к 1с для получения топа задач");
        return webClient.get()
                .uri("Task_ЗадачаИсполнителя?$top=10&$format=json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ODataResponse<UserTask>>() {
                })
                .map(ODataResponse::getValue)
                .block();
    }

    @Tool(description = "Универсальный запрос к 1С. Параметры (entity, filter) нужно брать из базы знаний метаданных.")
    public List<Map<String, Object>> executeSmartQuery(
            @ToolParam(description = "Имя сущности из метаданных (напр. Catalog_Контрагенты)") String entity,
            @ToolParam(description = "Фильтр OData (напр. ИНН eq '12345' или Number eq '001')") String filter,
            @ToolParam(description = "Лимит записей (по умолчанию 5)") Integer top
    ) {
        log.info("Универсальный вызов: Сущность={}, Фильтр={}, Лимит={}", entity, filter, top);

        int limit = (top != null) ? top : 5;

        return webClient.get()
                .uri(uriBuilder -> {
                    URI finalUri = uriBuilder.path(entity)
                            .queryParam("$top", limit)
                            .queryParam("$format", "json")
                            .build();

                    if (filter != null && !filter.isBlank()) {
                        // Пересобираем с фильтром, если он есть
                        finalUri = UriComponentsBuilder.fromUri(finalUri)
                                .queryParam("$filter", filter)
                                .build().toUri();
                    }

                    log.info(">>>> СФОРМИРОВАННЫЙ ЗАПРОС К 1С: {}", finalUri);
                    return finalUri;
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ODataResponse<Map<String, Object>>>() {
                })
                .map(ODataResponse::getValue)
                .block();
    }

    public List<FieldInfo> parseXmlMetadata() {
        List<FieldInfo> fields = new ArrayList<>();

        // Получаем поток XML от 1С
//        InputStream is = webClient.get()
//                .uri("$metadata")
//                .retrieve()
//                .bodyToMono(InputStream.class)
//                .block();
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

                        // 1. Фильтрация сущностей (например, Удалить...)
                        boolean isExcluded = config.excludeEntities().stream()
                                .anyMatch(entityName::contains);

                        currentEntity = isExcluded ? null : entityName;

                    } else if ("Property".equals(localName) && currentEntity != null) {
                        // Нашли поле внутри текущей таблицы
                        String fieldName = reader.getAttributeValue(null, "Name");

                        // 2. Фильтрация конкретных полей (например, Ref_Key)
                        if (config.excludeFields().contains(fieldName)) {
                            continue;
                        }
                        String fieldType = reader.getAttributeValue(null, "Type");
                        String humanDescription = String.format("Реквизит '%s' объекта '%s'. Тип данных: %s.",
                                fieldName,
                                currentEntity.replace("Catalog_", "Справочник ").replace("Document_", "Документ "),
                                fieldType.replace("Edm.", ""));

                        fields.add(new FieldInfo(currentEntity, fieldName, fieldType, humanDescription));

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
