package at.aau.serg.websocketdemoserver.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.BlockingQueue;

public class WebSocketHandlerClientImpl implements WebSocketHandler {
    private BlockingQueue<String> messagesQueue;

    public WebSocketHandlerClientImpl(BlockingQueue<String> receivedMessagesQueue){
        messagesQueue = receivedMessagesQueue;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        messagesQueue.add((String) message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
