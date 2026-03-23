# WebSocket Server demo project 

This repository contains two implementations for WebSocket communication in Java Spring Boot. The first implementation utilizes a broker with STOMP protocol in the package `at.aau.serg.websocketdemoserver.websocket.broker`, and the second implementation uses a basic WebSocket handler in the package `at.aau.serg.websocketdemoserver.websocket.handler`. Additionally, integration tests have been provided for each implementation.

## Broker Implementation with STOMP Protocol

The `at.aau.serg.websocketdemoserver.websocket.broker` package contains a WebSocket implementation that utilizes a broker with the STOMP protocol. STOMP (Simple Text Oriented Messaging Protocol) is a lightweight messaging protocol that defines the format and rules for data exchange. 

To explore the code for the broker implementation, navigate to the `at.aau.serg.websocketdemoserver.websocket.broker` package [here](./src/main/java/at/aau/serg/websocketdemoserver/websocket/broker).

## Basic WebSocket Handler Implementation

The `at.aau.serg.websocketdemoserver.websocket.handler` package contains a basic WebSocket implementation that utilizes a simple WebSocket handler. This implementation is straightforward and suitable for scenarios where a lightweight solution is preferred without the overhead of a full-fledged broker like STOMP.

To explore the code for the basic WebSocket handler implementation, navigate to the `at.aau.serg.websocketdemoserver.websocket.handler` package [here](./src/main/java/at/aau/serg/websocketdemoserver/websocket/handler).

## Integration Tests

Integration tests have been provided for both implementations. These tests focus on understanding the functionality of connecting, sending, and receiving messages via WebSocket communication. They serve as valuable resources for understanding how to effectively use WebSocket communication in Spring Boot applications.

To explore the integration tests, navigate to the respective test classes for the broker and handler implementations.
