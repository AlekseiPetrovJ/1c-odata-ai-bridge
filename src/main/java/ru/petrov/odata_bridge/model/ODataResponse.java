package ru.petrov.odata_bridge.model;

import lombok.Data;

import java.util.List;

@Data
public class ODataResponse<T> {
    private List<T> value;

    public List<T> getValue() {
        return value;
    }
}
