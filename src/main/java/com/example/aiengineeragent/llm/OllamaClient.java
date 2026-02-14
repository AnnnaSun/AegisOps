package com.example.aiengineeragent.llm;

import com.example.aiengineeragent.config.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class OllamaClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final String GENERATE_API_PATH = "/api/generate";
    private static final int PROMPT_LOG_LIMIT = 200;
    private static final int ERROR_BODY_LIMIT = 500;

    private final WebClient webClient;
    private final OllamaProperties ollamaProperties;

    public OllamaClient(WebClient.Builder webClientBuilder, OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
        this.webClient = webClientBuilder
                .baseUrl(ollamaProperties.getUrl())
                .build();
    }

    /**
     * Calls Ollama in a fully non-blocking way and returns generated text.
     * This method maps HTTP and timeout failures into RuntimeException with url/model context.
     */
    @Override
    public Mono<String> generate(String prompt) {
        String promptPreview = truncate(prompt, PROMPT_LOG_LIMIT);
        log.info("Calling Ollama generate. url={}, model={}, promptPreview={}",
                ollamaProperties.getUrl(), ollamaProperties.getModel(), promptPreview);

        OllamaGenerateRequest request = new OllamaGenerateRequest();
        request.setModel(ollamaProperties.getModel());
        request.setPrompt(prompt);
        request.setStream(false);

        return webClient.post()
                .uri(GENERATE_API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                // Capture non-2xx responses with a truncated body for troubleshooting.
                .onStatus(
                        status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> {
                                    String bodySnippet = truncate(body, ERROR_BODY_LIMIT);
                                    return new RuntimeException(String.format(
                                            "Ollama request failed. url=%s, model=%s, status=%s, body=%s",
                                            ollamaProperties.getUrl(),
                                            ollamaProperties.getModel(),
                                            clientResponse.statusCode(),
                                            bodySnippet));
                                })
                )
                .bodyToMono(OllamaGenerateResponse.class)
                .timeout(Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()))
                .map(response -> {
                    if (response == null) {
                        throw new RuntimeException(String.format(
                                "Ollama response body is empty. url=%s, model=%s",
                                ollamaProperties.getUrl(), ollamaProperties.getModel()));
                    }

                    String generatedText = response.getResponse();
                    if (generatedText == null) {
                        // Keep upstream analyzer in control of fallback decisions.
                        log.warn("Ollama response field missing. url={}, model={}, parsedField=response",
                                ollamaProperties.getUrl(), ollamaProperties.getModel());
                        return "";
                    }

                    log.info("Ollama generate succeeded. url={}, model={}",
                            ollamaProperties.getUrl(), ollamaProperties.getModel());
                    return generatedText;
                })
                .onErrorMap(ex -> {
                    String message = String.format("Ollama call error. url=%s, model=%s, reason=%s",
                            ollamaProperties.getUrl(), ollamaProperties.getModel(), ex.getMessage());
                    log.error(message, ex);
                    return new RuntimeException(message, ex);
                });
    }

    /**
     * Truncates long text for safe logging and error reporting.
     * <p>
     * - Keep original text when length <= maxLen.
     * - Return first maxLen chars + "..." when length > maxLen.
     * - Return null when input is null.
     * </p>
     */
    private static String truncate(String input, int maxLen) {
        if (input == null) {
            return null;
        }
        if (input.length() <= maxLen) {
            return input;
        }
        return input.substring(0, maxLen) + "...";
    }

    private static class OllamaGenerateRequest {
        private String model;
        private String prompt;
        private boolean stream;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }

    private static class OllamaGenerateResponse {
        private String response;

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }
    }
}
