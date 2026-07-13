package org.keycloak.admin.client.jackson3;

import java.util.List;
import java.util.stream.Stream;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

class StreamDeserializer3 extends ValueDeserializer<Stream<?>> {

    private final JavaType contentType;

    StreamDeserializer3() {
        this(null);
    }

    private StreamDeserializer3(JavaType contentType) {
        this.contentType = contentType;
    }

    @Override
    public Stream<?> deserialize(JsonParser p, DeserializationContext ctxt) {
        JavaType streamType = ctxt.getContextualType();
        JavaType actualContentType = contentType;

        if (actualContentType == null && streamType != null && streamType.containedTypeCount() == 1) {
            actualContentType = streamType.containedType(0);
        }

        if (actualContentType == null) {
            actualContentType = ctxt.getTypeFactory().constructType(Object.class);
        }

        JavaType listType = ctxt.getTypeFactory().constructCollectionType(List.class, actualContentType);
        List<?> list = ctxt.readValue(p, listType);
        return list.stream();
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType contextualType = ctxt.getContextualType();
        JavaType actualContentType = null;

        if (contextualType != null && contextualType.containedTypeCount() == 1) {
            actualContentType = contextualType.containedType(0);
        }

        return new StreamDeserializer3(actualContentType);
    }
}
