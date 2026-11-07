package cc.cc3c.hive.oss.business.config;

import cc.cc3c.hive.oss.business.web.CategoryContextArgumentResolver;
import cc.cc3c.hive.oss.business.web.CategoryFileContextArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class HiveOssWebConfig implements WebMvcConfigurer {
    private final CategoryContextArgumentResolver categoryContextArgumentResolver;
    private final CategoryFileContextArgumentResolver categoryFileContextArgumentResolver;

    public HiveOssWebConfig(CategoryContextArgumentResolver categoryContextArgumentResolver,
                            CategoryFileContextArgumentResolver categoryFileContextArgumentResolver) {
        this.categoryContextArgumentResolver = categoryContextArgumentResolver;
        this.categoryFileContextArgumentResolver = categoryFileContextArgumentResolver;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(categoryContextArgumentResolver);
        resolvers.add(categoryFileContextArgumentResolver);
    }
}
