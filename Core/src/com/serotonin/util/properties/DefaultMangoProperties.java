/*
 * Copyright (C) 2020 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.util.properties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>We can potentially replace the regex code with {@linkplain org.springframework.util.PropertyPlaceholderHelper} or
 * {@linkplain org.apache.commons.text.StringSubstitutor}.</p>
 *
 * <p>Limitations of this class include:</p>
 * <ul>
 *   <li>No protection against infinite recursion when interpolating</li>
 *   <li>No way to specify default in interpolation expression</li>
 *   <li>No way escape interpolation expressions</li>
 * </ul>
 *
 * <p>{@linkplain com.infiniteautomation.mango.spring.MangoPropertySource} is used to supply these properties to Spring.
 * Note: The Spring property resolver attempts interpolation again when getting properties from Environment.</p>
 *
 * @author Jared Wiltshire
 */
public class DefaultMangoProperties implements MangoProperties {

    private final Path envPropertiesPath;
    protected volatile Properties properties;

    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    public DefaultMangoProperties(Properties properties) {
        this.envPropertiesPath = null;
        this.properties = properties;
    }

    public DefaultMangoProperties(Path envPropertiesPath) {
        this.envPropertiesPath = envPropertiesPath;
        reload();
    }

    void reload() {
        if (envPropertiesPath == null) {
            throw new UnsupportedOperationException();
        }

        // Load the environment properties
        Properties properties;
        try {
            properties = DefaultMangoProperties.loadFromResources("env.properties");
            if (Files.isReadable(envPropertiesPath)) {
                try (Reader reader = Files.newBufferedReader(envPropertiesPath)) {
                    properties.load(reader);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.properties = properties;
    }

    Path getEnvPropertiesPath() {
        return envPropertiesPath;
    }

    @Override
    public String getString(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = properties.getProperty(key);
        }
        return interpolateProperty(value);
    }

    private String interpolateProperty(String value) {
        if (value == null) return value;

        Matcher matcher = INTERPOLATION_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String interpolatedKey = matcher.group(1);
            String interpolatedValue = getString(interpolatedKey);
            if (interpolatedValue == null) {
                throw new IllegalStateException("Property has no value: " + interpolatedKey);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(interpolatedValue));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static Properties loadFromResources(String resourceName) throws IOException {
        return loadFromResources(resourceName, DefaultMangoProperties.class.getClassLoader());
    }

    public static Properties loadFromResources(String resourceName, ClassLoader cl) throws IOException {
        Properties properties = new Properties();
        ArrayList<URL> resources = Collections.list(cl.getResources(resourceName));
        for (URL resource : resources) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
