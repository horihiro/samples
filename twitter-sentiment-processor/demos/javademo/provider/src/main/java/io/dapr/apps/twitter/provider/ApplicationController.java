/*
 * Copyright (c) Microsoft Corporation.
 * Licensed under the MIT License.
 */

package io.dapr.apps.twitter.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.apps.twitter.provider.model.AnalyzedTweet;
import io.dapr.apps.twitter.provider.model.Sentiment;
import io.dapr.apps.twitter.provider.model.Tweet;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.HttpExtension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ApplicationController {

    private static final String SENTIMENT_PROCESSOR_APP = "sentiment_processor";

    private static final String STATE_STORE = "statestore";

    private static final String PUBSUB = "messagebus";

    private static final String PUBSUB_TOPIC = "tweets";

    @Autowired
    private final DaprClient daprClient;

    @PostMapping(value = "/tweet")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Mono<Void> tweet(@RequestBody Tweet tweet) {
        log.info(String.format("Tweet received %s in %s: %s", tweet.getId(), tweet.getLanguage(), tweet.getText()));
        return daprClient
                .invokeService(SENTIMENT_PROCESSOR_APP, "sentiment", tweet, HttpExtension.POST, Sentiment.class)
                .map(sentiment -> AnalyzedTweet.builder()
                  .id(tweet.getId())
                  .tweet(tweet)
                  .sentiment(sentiment)
                  .build())
                .flatMap(analizedTweet -> daprClient.saveState(STATE_STORE, analizedTweet.getId(), analizedTweet)
                        .then(daprClient.publishEvent(PUBSUB, PUBSUB_TOPIC, analizedTweet)
                        .thenReturn(analizedTweet)))
                .doOnSuccess(analizedTweet -> log.info(String.format("Tweet saved %s: %s", analizedTweet.getId(), analizedTweet.getSentiment().getSentiment())))
                .doOnError(onError -> log
                        .error(String.format("Tweet not saved %s : %s", tweet.getId(), tweet.getText()), onError))
                .then();
    }

    @GetMapping(path = "/health")
    public Mono<Void> health() {
        return Mono.empty();
    }
}
