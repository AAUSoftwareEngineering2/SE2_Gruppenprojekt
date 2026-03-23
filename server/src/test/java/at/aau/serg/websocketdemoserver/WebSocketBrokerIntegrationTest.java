package at.aau.serg.websocketdemoserver;

import at.aau.serg.websocketdemoserver.messaging.dtos.StompMessage;
import at.aau.serg.websocketdemoserver.websocket.StompFrameHandlerClientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketBrokerIntegrationTest {

    @LocalServerPort
    private int port;

    private final String WEBSOCKET_URI = "ws://localhost:%d/websocket-example-broker";
    private final String WEBSOCKET_TOPIC = "/topic/hello-response";
    private final String WEBSOCKET_TOPIC_OBJECT = "/topic/rcv-object";

    @Test
    void testWebSocketMessageBroker() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingDeque<>(); // Queue of messages from the server.
        StompSession session = initStompSession(WEBSOCKET_TOPIC, new StringMessageConverter(), messages, String.class);

        // send a message to the server
        String message = "Test message";
        session.send("/app/hello", message);

        var expectedResponse = "echo from broker: " + message;
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isEqualTo(expectedResponse);
    }

    @Test
    void testWebSocketMessageBrokerHandleObject() throws Exception {
        BlockingQueue<StompMessage> messages = new LinkedBlockingDeque<>(); // Queue of messages from the server.
        StompSession session = initStompSession(WEBSOCKET_TOPIC_OBJECT, new JacksonJsonMessageConverter(), messages, StompMessage.class);

        // send a message object to the server
        StompMessage message = new StompMessage("client", "Test Object Message");
        session.send("/app/object", message);

        assertThat(messages.poll(1, TimeUnit.SECONDS)).isEqualTo(message);
    }

    /**
     * @return The Stomp session for the WebSocket connection (Stomp - WebSocket is comparable to HTTP - TCP).
     */
    public <T> StompSession initStompSession(String destination,
                                             MessageConverter messageConverter,
                                             BlockingQueue<T> queue,
                                             Class<T> expectedType) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(messageConverter);

        // connect client to the websocket server
        StompSession session = stompClient.connectAsync(String.format(WEBSOCKET_URI, port),
                        new StompSessionHandlerAdapter() {
                        })
                // wait 1 sec for the client to be connected
                .get(1, TimeUnit.SECONDS);

        // subscribes to the topic defined in WebSocketBrokerController
        // and adds received messages to WebSocketBrokerIntegrationTest#messages
        session.subscribe(destination, new StompFrameHandlerClientImpl<>(queue, expectedType));

        return session;
    }

}
