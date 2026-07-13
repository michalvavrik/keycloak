package org.keycloak.admin.client.jackson3;

import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.resteasy.providers.jackson.ResteasyJacksonProvider;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class JacksonProvider3 extends ResteasyJacksonProvider {

    private static final JsonMapper MAPPER;

    static {
        JsonMapper defaultMapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(v -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(new SimpleModule().addDeserializer(Stream.class, new StreamDeserializer3()))
                .build();
        MAPPER = defaultMapper.rebuild()
                .annotationIntrospector(AnnotationIntrospector.pair(
                        new KeycloakAnnotationIntrospector3(),
                        defaultMapper.serializationConfig().getAnnotationIntrospector()))
                .build();
    }

    public JacksonProvider3() {
        setMapper(MAPPER);
    }
}
