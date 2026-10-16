package com.hope.trading.market_intelligence.domain.feature;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NumericFeatureValue.class, name = "numeric"),
        @JsonSubTypes.Type(value = CategoricalFeatureValue.class, name = "categorical"),
        @JsonSubTypes.Type(value = BooleanFeatureValue.class, name = "boolean")
})
public sealed interface FeatureValue permits NumericFeatureValue, CategoricalFeatureValue, BooleanFeatureValue {
    String name();
    FeatureMissingness status();
}
