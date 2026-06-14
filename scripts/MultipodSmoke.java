import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smoke test for the multi-pod setup, run via run-multipod-local.sh against three local server
 * instances sharing one database: create a game on :8080, join on :8081, reconnect on :8082
 * after the first socket died, then start the game and check that the player on :8081 receives
 * the broadcast.
 *
 * Runs with plain `java scripts/MultipodSmoke.java`, no dependencies. JSON is matched with
 * regexes, crude but enough for a smoke check.
 */
public class MultipodSmoke {
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static class WsSession implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder partial = new StringBuilder();
        WebSocket socket;

        static WsSession connect(int port) throws Exception {
            WsSession session = new WsSession();
            session.socket =
                    HTTP.newWebSocketBuilder()
                            .buildAsync(URI.create("ws://localhost:" + port + "/websocket/game"), session)
                            .get(10, TimeUnit.SECONDS);
            return session;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        void send(String json) {
            socket.sendText(json, true).join();
        }

        String await(String type, int seconds) throws InterruptedException {
            return await(type, null, seconds);
        }

        /** Waits for a message of the given type that also contains marker, skipping older
         *  buffered messages of the same type. */
        String await(String type, String marker, int seconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + seconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                String msg = messages.poll(250, TimeUnit.MILLISECONDS);
                if (msg == null) continue;
                if (msg.contains("\"type\":\"" + type + "\"")
                        && (marker == null || msg.contains(marker))) {
                    return msg;
                }
                if (msg.contains("\"type\":\"ERROR\"")) {
                    throw new AssertionError("Server replied ERROR while waiting for " + type
                            + ": " + cut(msg));
                }
            }
            throw new AssertionError("Timed out waiting for " + type
                    + (marker == null ? "" : " containing " + marker));
        }
    }

    static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new AssertionError("Field " + field + " missing in: " + cut(json));
        return m.group(1);
    }

    static String cut(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("[1] Alice creates a game on :8080");
        WsSession alice8080 = WsSession.connect(8080);
        alice8080.send(
                "{\"type\":\"CREATE_GAME\",\"requestId\":\"r1\",\"payload\":{\"playerName\":\"Alice\"}}");
        String created = alice8080.await("GAME_CREATED", 10);
        String gameId = extract(created, "gameId");
        String aliceId = extract(created, "playerId");
        String aliceToken = extract(created, "reconnectToken");
        System.out.println("    game " + gameId + ", alice " + aliceId.substring(0, 8) + "...");

        System.out.println("[2] Bob and Carol join on :8081 (different pod)");
        WsSession bob8081 = WsSession.connect(8081);
        bob8081.send(
                "{\"type\":\"JOIN_GAME\",\"requestId\":\"r2\",\"payload\":{\"gameId\":\"" + gameId
                        + "\",\"playerName\":\"Bob\"}}");
        bob8081.await("GAME_JOINED", 10);

        WsSession carol8081 = WsSession.connect(8081);
        carol8081.send(
                "{\"type\":\"JOIN_GAME\",\"requestId\":\"r3\",\"payload\":{\"gameId\":\"" + gameId
                        + "\",\"playerName\":\"Carol\"}}");
        carol8081.await("GAME_JOINED", 10);
        System.out.println("    joined across pods, game state lives in the shared DB");

        System.out.println("[3] Alice's pod dies; she reconnects on :8082 with her token");
        alice8080.socket.abort();
        Thread.sleep(500);
        WsSession alice8082 = WsSession.connect(8082);
        alice8082.send(
                "{\"type\":\"RECONNECT\",\"requestId\":\"r4\",\"payload\":{\"gameId\":\"" + gameId
                        + "\",\"playerId\":\"" + aliceId + "\",\"token\":\"" + aliceToken + "\"}}");
        String snapshot = alice8082.await("SNAPSHOT", 10);
        System.out.println("    SNAPSHOT received, token validated from the DB by a foreign pod");

        System.out.println("[4] Alice starts the game on :8082; Bob on :8081 must hear about it");
        alice8082.send("{\"type\":\"START_GAME\",\"requestId\":\"r5\",\"payload\":null}");
        alice8082.await("GAME_STATE_UPDATED", "PLAYER_CHOICE", 10);
        bob8081.await("GAME_STATE_UPDATED", "PLAYER_CHOICE", 10);
        System.out.println("    cross-pod broadcast delivered (8082 -> 8081)");

        System.out.println();
        System.out.println("MULTIPOD SMOKE PASSED: create@8080, join@8081, reconnect@8082, "
                + "cross-pod broadcast 8082->8081 all working.");
        System.exit(0);
    }
}
