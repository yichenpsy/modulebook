package de.bht.modulebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import de.bht.modulebook.config.PdfImportProperties;

@SpringBootApplication
@EnableConfigurationProperties(PdfImportProperties.class)
public class ModulebookApplication {

	public static void main(String[] args) {
		SpringApplication.run(ModulebookApplication.class, args);
	}

}
