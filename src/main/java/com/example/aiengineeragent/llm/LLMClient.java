package com.example.aiengineeragent.llm;

import reactor.core.publisher.Mono;

public interface LLMClient {
    Mono<String> generate(String prompt);
}
