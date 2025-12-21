package ru.petrov.odata_bridge.model;
/**
 * Описание элемента метаданных 1С.
 * @param entity    Техническое имя таблицы (напр. Catalog_Контрагенты)
 * @param name      Техническое имя поля (напр. ИНН) или TABLE_HEADER для самой таблицы
 * @param type      Тип данных OData
 * @param description Человекочитаемое описание для векторного поиска
 * @param isHeader  Признак: true — это описание таблицы, false — это описание поля
 */
public record FieldInfo(
        String entity,
        String name,
        String type,
        String description,
        boolean isHeader
) {}
