package de.bht.modulebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import de.bht.modulebook.config.PdfImportProperties;

@SpringBootApplication
// It tells Spring: “please create a PdfImportProperties object from application.properties.”
@EnableConfigurationProperties(PdfImportProperties.class)
public class ModulebookApplication {

	public static void main(String[] args) {
		SpringApplication.run(ModulebookApplication.class, args);
	}

}
