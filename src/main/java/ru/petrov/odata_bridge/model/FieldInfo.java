package ru.petrov.odata_bridge.model;

public class FieldInfo {
    private final String entity;
    private final String name;
    private final String type;
    private final String description;

    public FieldInfo(String entity, String name, String type, String description) {
        this.entity = entity;
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public String getEntity() {
        return entity;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
