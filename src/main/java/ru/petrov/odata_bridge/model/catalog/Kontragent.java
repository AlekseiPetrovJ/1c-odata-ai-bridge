package ru.petrov.odata_bridge.model.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.petrov.odata_bridge.model.Base1cEntity;

@Data
@EqualsAndHashCode(callSuper = true)
public class Kontragent extends Base1cEntity {
    @JsonProperty("Description")
    private String description;

    @JsonProperty("ИНН")
    private String inn;
}
