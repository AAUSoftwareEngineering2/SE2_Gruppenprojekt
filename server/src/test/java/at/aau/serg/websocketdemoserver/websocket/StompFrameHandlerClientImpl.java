package at.aau.serg.websocketdemoserver.websocket;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;

public class StompFrameHandlerClientImpl<T> implements StompFrameHandler {
    private final BlockingQueue<T> messagesQueue;
    private final Class<T> payloadType;

    public StompFrameHandlerClientImpl(BlockingQueue<T> receivedMessagesQueue, Class<T> payloadType) {
        messagesQueue = receivedMessagesQueue;
        this.payloadType = payloadType;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return this.payloadType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleFrame(StompHeaders headers, Object payload) {
        // add the new message to the queue of received messages
        messagesQueue.add((T) payload);
    }

}
