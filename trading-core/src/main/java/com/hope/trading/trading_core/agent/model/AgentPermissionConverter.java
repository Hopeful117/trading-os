package com.hope.trading.trading_core.agent.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AgentPermissionConverter implements AttributeConverter<AgentPermission, String> {
    @Override
    public String convertToDatabaseColumn(AgentPermission attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public AgentPermission convertToEntityAttribute(String value) {
        return value == null ? null : AgentPermission.fromValue(value);
    }
}
