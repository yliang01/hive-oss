package cc.cc3c.hive.oss.business.config;

/** Read-only original YAML used when publishing the current configuration. */
@FunctionalInterface
public interface SecretConfigurationSnapshot {
    byte[] yaml();
}
