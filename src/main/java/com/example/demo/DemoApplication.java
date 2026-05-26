package com.example.demo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		loadLocalEnv();
		SpringApplication.run(DemoApplication.class, args);
	}

	private static void loadLocalEnv() {
		Set<Path> candidates = new LinkedHashSet<>();
		addLocalEnvCandidates(candidates, Path.of("").toAbsolutePath());

		Path classPath = applicationClassPath();
		if (classPath != null) {
			addLocalEnvCandidates(candidates, classPath);
		}

		for (Path path : candidates) {
			if (Files.isRegularFile(path)) {
				loadLocalEnv(path);
				return;
			}
		}
	}

	private static void addLocalEnvCandidates(Set<Path> candidates, Path start) {
		Path base = start.toAbsolutePath().normalize();
		for (int i = 0; base != null && i < 8; i++) {
			candidates.add(base.resolve("local.env"));
			candidates.add(base.resolve("demo").resolve("local.env"));
			candidates.add(base.resolve("devtalk-backend").resolve("demo").resolve("local.env"));
			base = base.getParent();
		}
	}

	private static Path applicationClassPath() {
		try {
			return Path.of(DemoApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException | SecurityException ignored) {
			return null;
		}
	}

	private static void loadLocalEnv(Path path) {
		try {
			for (String line : Files.readAllLines(path)) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}

				String[] parts = trimmed.split("=", 2);
				if (parts.length != 2) {
					continue;
				}

				String key = parts[0].trim();
				String value = parts[1].trim();
				if (key.isEmpty() || value.isEmpty()) {
					continue;
				}
				if (isBlank(System.getenv(key)) && isBlank(System.getProperty(key))) {
					System.setProperty(key, value);
				}
			}
		} catch (IOException ignored) {
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
