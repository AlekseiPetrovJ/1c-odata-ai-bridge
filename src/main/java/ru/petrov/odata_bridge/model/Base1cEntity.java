package ru.petrov.odata_bridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public  abstract class Base1cEntity {
    @JsonProperty("Ref_Key")
    private String refKey;
}
