package cc.cc3c.hive.oss.runtime;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;

@Configuration
@EnableScheduling
@EnableWebMvc
@EnableAutoConfiguration(exclude = {DispatcherServletAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class, ErrorMvcAutoConfiguration.class})
// Auto-configurations in dependency JARs are loaded by Boot, not component scanning.
@ComponentScan(basePackages = "cc.cc3c.hive.oss.business")
public class BusinessContextConfiguration { }
