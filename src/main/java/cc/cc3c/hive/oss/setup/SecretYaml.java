package cc.cc3c.hive.oss.setup;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.env.PropertySource;
import java.io.IOException;
import java.util.List;

/** Standard Spring YAML parser; values, lists, placeholders and documents remain Spring properties. */
public final class SecretYaml {
    private SecretYaml() { }
    public static List<PropertySource<?>> load(byte[] yaml) throws IOException {
        return new YamlPropertySourceLoader().load("secretYaml", new ByteArrayResource(yaml) {
            @Override public String getFilename() { return "secret.yml"; }
        });
    }
}
