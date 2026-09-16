package de.bht.modulebook.pdfimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.bht.modulebook.config.PdfImportProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PythonPdfImportRunnerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsMultipartRequestToPdfImportServiceWithHttpOneOne(@TempDir Path tempDir) throws Exception {
        AtomicReference<String> requestProtocol = new AtomicReference<>();
        AtomicReference<String> requestContentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = startPdfImportServiceStub(requestProtocol, requestContentType, requestBody);

        PdfImportProperties properties = new PdfImportProperties();
        properties.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/import");
        PythonPdfImportRunner runner = new PythonPdfImportRunner(properties, objectMapper);

        Path inputPdf = tempDir.resolve("input.pdf");
        Path outputJson = tempDir.resolve("normalized_modules.json");
        Path reportJson = tempDir.resolve("report.json");
        Files.write(inputPdf, "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));

        ImportRunResult result = runner.run(
                inputPdf,
                tempDir,
                outputJson,
                reportJson,
                "test.pdf"
        );

        assertThat(result.succeeded()).isTrue();
        assertThat(requestProtocol.get()).isEqualTo("HTTP/1.1");
        assertThat(requestContentType.get()).startsWith("multipart/form-data; boundary=modulebook-");
        assertThat(requestBody.get()).contains("Content-Disposition: form-data; name=\"file\"; filename=\"test.pdf\"");
        assertThat(requestBody.get()).contains("Content-Disposition: form-data; name=\"sourceFile\"");
        assertThat(objectMapper.readTree(outputJson.toFile()).get(0).get("module_number").asString()).isEqualTo("B01");
        assertThat(objectMapper.readTree(reportJson.toFile()).get("status").asString()).isEqualTo("ok");
    }

    private HttpServer startPdfImportServiceStub(
            AtomicReference<String> requestProtocol,
            AtomicReference<String> requestContentType,
            AtomicReference<String> requestBody
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/import", exchange -> handleImport(exchange, requestProtocol, requestContentType, requestBody));
        httpServer.start();
        return httpServer;
    }

    private void handleImport(
            HttpExchange exchange,
            AtomicReference<String> requestProtocol,
            AtomicReference<String> requestContentType,
            AtomicReference<String> requestBody
    ) throws IOException {
        requestProtocol.set(exchange.getProtocol());
        requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));

        byte[] response = """
                {
                  "report": {
                    "status": "ok"
                  },
                  "normalizedModules": [
                    {
                      "module_number": "B01"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
