package com.example.aiengineeragent.analyzer;

import reactor.core.publisher.Mono;

public interface Analyzer<T, R> {
    Mono<R> analyze(T input);
}
