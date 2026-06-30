package ca.uhn.fhir.jpa.starter.generation.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "hapi.fhir.generation")
public class GenerationProperties {

	private Path workDir;
	private Path inputBaseDir;
	private Path jarPath;
	private String mavenPath = "mvn";

	public Path getWorkDir() {
		return workDir;
	}

	public void setWorkDir(Path workDir) {
		this.workDir = workDir;
	}

	public Path getInputBaseDir() {
		return inputBaseDir;
	}

	public void setInputBaseDir(Path inputBaseDir) {
		this.inputBaseDir = inputBaseDir;
	}

	public Path getJarPath() {
		return jarPath;
	}

	public void setJarPath(Path jarPath) {
		this.jarPath = jarPath;
	}

	public String getMavenPath() {
		return mavenPath;
	}

	public void setMavenPath(String mavenPath) {
		this.mavenPath = mavenPath;
	}
}