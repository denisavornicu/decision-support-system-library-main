// SadApplication.java
package ro.uaic.sad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class SadApplication {
    public static void main(String[] args) { SpringApplication.run(SadApplication.class, args); }

    /** CORS pentru frontend-ul Vite (localhost:5173). */
    @Bean
    public WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry r) {
                r.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedMethods("GET");
            }
        };
    }
}
