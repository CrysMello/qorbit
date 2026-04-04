package com.qorbit.engine.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    // Registra apenas o módulo Hibernate — o Spring Boot cuida do resto
    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // Serializa null em vez de lançar erro para proxies lazy não carregados
        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        return module;
    }
}
