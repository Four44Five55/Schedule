package ru.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Разрешить для всех URL
                .allowedOrigins(
                        "http://localhost:5173", // Vite dev server (scripts/start-dev.sh)
                        "http://localhost:5174", // Vite dev server, запасной порт (если 5173 занят)
                        "http://localhost:4173"  // Vite preview — прод-сборка (scripts/start-prod.sh)
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
