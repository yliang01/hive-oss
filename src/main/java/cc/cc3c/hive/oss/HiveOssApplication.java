package cc.cc3c.hive.oss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

@SpringBootApplication
public class HiveOssApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder().sources(HiveOssApplication.class).run(args);
    }
}
