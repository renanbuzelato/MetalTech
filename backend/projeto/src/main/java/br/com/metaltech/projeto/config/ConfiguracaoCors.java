package br.com.metaltech.projeto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Permite que o dashboard React e o app de RA chamem a API pelo navegador.
// Em produção, troque "*" pelos endereços reais do front-end.
@Configuration
public class ConfiguracaoCors implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/**") // Liberado para todas as rotas da aplicação
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}