package cc.cc3c.hive.oss.setup;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SetupConfiguration implements WebMvcConfigurer {
    @Bean
    public BusinessServlet businessServlet() {
        return new BusinessServlet();
    }

    @Bean(destroyMethod = "close")
    public BusinessContextManager businessContextManager(ConfigurableApplicationContext applicationContext,
                                                         BusinessServlet servlet) {
        return new BusinessContextManager(applicationContext, servlet);
    }

    @Bean
    public ServletRegistrationBean<BusinessServlet> businessServletRegistration(BusinessServlet servlet) {
        var registration = new ServletRegistrationBean<>(servlet, "/api/*");
        registration.setName("business");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/setup/**").allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
