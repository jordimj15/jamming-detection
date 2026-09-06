package org.jammingdetection.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application configuration from {@code config.properties} on the
 * classpath and exposes typed accessors for its values. Loaded once when
 * the class is first referenced.
 */
public class Config {

    private static final Properties props = new Properties();

    static {
        try (InputStream input = Config.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(props.getProperty(key));
    }

    public static long getLong(String key) {
        return Long.parseLong(props.getProperty(key));
    }

    private Config() {}
}
