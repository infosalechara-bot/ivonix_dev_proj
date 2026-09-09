package com.ivonix.pulse.ontology.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override public void addCorsMappings(CorsRegistry registry){
        String raw=System.getenv().getOrDefault("PULSE_CORS_ORIGINS", "http://localhost:5173");
        String[] origins=Arrays.stream(raw.split(",")).map(String::trim).filter(s->!s.isBlank()).toArray(String[]::new);
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedHeaders("Authorization","Content-Type","Accept","X-Request-Id").allowCredentials(false).maxAge(3600);
    }
}
