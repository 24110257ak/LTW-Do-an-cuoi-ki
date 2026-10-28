package vn.iotstar;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import vn.iotstar.entity.*;
import vn.iotstar.repository.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:chat_ws_only;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@ActiveProfiles("test")
class ChatSocketIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BranchRepository branches;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry registry;
    private Branch a, b;
    private final List<Socket> sockets = new ArrayList<>();
    private final List<Client> clients = new ArrayList<>();
    private record Client(HttpClient http, CookieManager cookies, JsonNode bootstrap) {}

    @BeforeEach void setup() {
        var cr = roles.save(Role.builder().roleName("CUSTOMER").build());
        var sr = roles.save(Role.builder().roleName("STAFF").build());
        a = branches.save(Branch.builder().branchName("Socket A").address("A").district("A").phone("100").build());
        b = branches.save(Branch.builder().branchName("Socket B").address("B").district("B").phone("101").build());
        create("ws_customer",cr,null,"0919000001"); create("ws_stranger",cr,null,"0919000002");
        create("ws_staff",sr,a,"0919000003"); create("ws_other",sr,b,"0919000004");
    }
    void create(String name, Role role, Branch branch, String phone) {
        users.save(User.builder().username(name).email(name+"@test.com").password(encoder.encode("chat-test-password"))
                .fullName(name).phone(phone).role(role).branch(branch).isVerified(true).build());
    }
    @AfterEach void cleanup() {
        sockets.forEach(s -> { if (s.socket != null) s.socket.abort(); });
        messages.deleteAll(); sessions.deleteAll(); users.deleteAll(); branches.deleteAll(); roles.deleteAll();
    }
    URI uri(String path) { return URI.create("http://localhost:"+port+path); }
    Client login(String username) throws Exception {
        CookieManager cookies = new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        var page = http.send(HttpRequest.newBuilder(uri("/auth/login")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertTrue(matcher.find(),"Login form must contain a CSRF token");
        String body = "username="+username+"&password=chat-test-password&_csrf="+URLEncoder.encode(matcher.group(1),java.nio.charset.StandardCharsets.UTF_8);
        var logged = http.send(HttpRequest.newBuilder(uri("/auth/login")).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(302,logged.statusCode()); assertFalse(logged.headers().firstValue("location").orElse("").contains("error"));
        var bootstrap = http.send(HttpRequest.newBuilder(uri("/chat/api/bootstrap")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,bootstrap.statusCode(),bootstrap.body());
        Client client = new Client(http,cookies,json.readTree(bootstrap.body())); clients.add(client); return client;
    }
    JsonNode post(Client client, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(uri("/chat/api"+path)).header("Content-Type","application/json")
                .header(client.bootstrap.path("csrfHeader").asText(),client.bootstrap.path("csrfToken").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = client.http.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),response.body()); return response.body().isBlank()?null:json.readTree(response.body());
    }
    class Socket implements WebSocket.Listener {
        WebSocket socket;
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket webSocket) { socket=webSocket; webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            int end;
            while ((end=buffer.indexOf("\0"))>=0) { frames.add(buffer.substring(0,end).stripLeading()); buffer.delete(0,end+1); }
            webSocket.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket,int status,String reason) { frames.add("CLOSED"); return CompletableFuture.completedFuture(null); }
        @Override public void onError(WebSocket webSocket,Throwable error) { frames.add("ERROR "+error.getMessage()); }
        void send(String text) { socket.sendText(text+"\0",true).join(); }
        String await(String prefix) throws Exception {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime()<deadline) {
                String frame=frames.poll(1,TimeUnit.SECONDS);
                if (frame!=null && frame.startsWith(prefix)) return frame;
                if (frame!=null && (frame.startsWith("ERROR")||frame.equals("CLOSED"))) fail(frame);
            }
            throw new AssertionError("No STOMP "+prefix+" frame received");
        }
    }
    Socket connect(Client client, boolean csrf) throws Exception {
        Socket listener=new Socket(); sockets.add(listener);
        String cookie=client.cookies.getCookieStore().getCookies().stream().map(c -> c.getName()+"="+c.getValue()).collect(java.util.stream.Collectors.joining("; "));
        client.http.newWebSocketBuilder().header("Cookie",cookie).header("Origin","http://localhost:"+port)
                .buildAsync(URI.create("ws://localhost:"+port+"/ws/websocket"),listener).get(8,TimeUnit.SECONDS);
        listener.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\n"+(csrf ? client.bootstrap.path("csrfHeader").asText()+":"+client.bootstrap.path("csrfToken").asText()+"\n":"")+"\n");
        if(csrf) listener.await("CONNECTED"); return listener;
    }
    void awaitSubscription(String username) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<deadline) {
            var user=registry.getUser(username);
            if (user!=null && user.getSessions().stream().anyMatch(session -> session.getSubscriptions().stream()
                    .anyMatch(subscription -> "/user/queue/chat".equals(subscription.getDestination())))) return;
            Thread.sleep(10);
        }
        fail("Subscription was not registered for "+username);
    }
    @Test void actualWebSocketDeliversPrivatelyAfterCommitAndHistorySurvivesReconnect() throws Exception {
        Client customer=login("ws_customer"), staff=login("ws_staff"), stranger=login("ws_stranger"), other=login("ws_other");
        Socket c=connect(customer,true), s=connect(staff,true), x=connect(stranger,true), o=connect(other,true);
        for(Socket socket:List.of(c,s,x,o)) socket.send("SUBSCRIBE\nid:chat\ndestination:/user/queue/chat\nreceipt:ready\n\n");
        for(String name:List.of("ws_customer","ws_staff","ws_stranger","ws_other")) awaitSubscription(name);
        long id=post(customer,"/sessions",Map.of("branchId",a.getBranchId())).path("sessionId").asLong();
        c.await("MESSAGE"); s.await("MESSAGE");
        var sent=post(customer,"/messages",Map.of("sessionId",id,"clientMessageId","socket-first","content","Xin chào nhân viên"));
        assertTrue(c.await("MESSAGE").contains("Xin chào nhân viên")); assertTrue(s.await("MESSAGE").contains("Xin chào nhân viên"));
        assertNull(x.frames.poll(200,TimeUnit.MILLISECONDS),"Another customer must receive no chat event");
        assertNull(o.frames.poll(200,TimeUnit.MILLISECONDS),"Another branch must receive no chat event");
        post(customer,"/messages",Map.of("sessionId",id,"clientMessageId","socket-first","content","Xin chào nhân viên"));
        assertEquals(1,messages.count()); assertNull(s.frames.poll(200,TimeUnit.MILLISECONDS),"Retry must not broadcast twice");
        c.socket.abort();
        post(staff,"/messages",Map.of("sessionId",id,"clientMessageId","socket-reply","content","Chào bạn, cửa hàng hỗ trợ bạn."));
        s.await("MESSAGE");
        Socket reconnect=connect(customer,true); reconnect.send("SUBSCRIBE\nid:again\ndestination:/user/queue/chat\nreceipt:ready-again\n\n"); awaitSubscription("ws_customer");
        var history=customer.http.send(HttpRequest.newBuilder(uri("/chat/api/sessions/"+id+"/messages")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,history.statusCode()); assertEquals(2,json.readTree(history.body()).path("messages").size());
        assertFalse(history.body().contains("password")); assertEquals(customer.bootstrap.path("userId").asLong(),sent.path("senderId").asLong());
        s.send("SUBSCRIBE\nid:forbidden\ndestination:/topic/chat/"+id+"\n\n");
        String rejected=s.frames.poll(5,TimeUnit.SECONDS); assertNotNull(rejected); assertTrue(rejected.startsWith("ERROR")||rejected.equals("CLOSED"),rejected);
    }
    @Test void concurrentOpenAndSendAreIdempotent() throws Exception {
        Client customer=login("ws_customer");
        java.util.function.Supplier<JsonNode> open=() -> {
            try { return post(customer,"/sessions",Map.of("branchId",a.getBranchId())); }
            catch (Exception e) { throw new CompletionException(e); }
        };
        var first=CompletableFuture.supplyAsync(open); var second=CompletableFuture.supplyAsync(open);
        long id=first.get(8,TimeUnit.SECONDS).path("sessionId").asLong();
        assertEquals(id,second.get(8,TimeUnit.SECONDS).path("sessionId").asLong()); assertEquals(1,sessions.count());
        java.util.function.Supplier<JsonNode> send=() -> {
            try { return post(customer,"/messages",Map.of("sessionId",id,"clientMessageId","concurrent","content","one message")); }
            catch (Exception e) { throw new CompletionException(e); }
        };
        var a=CompletableFuture.supplyAsync(send); var b=CompletableFuture.supplyAsync(send);
        assertEquals(a.get(8,TimeUnit.SECONDS).path("messageId").asLong(),b.get(8,TimeUnit.SECONDS).path("messageId").asLong());
        assertEquals(1,messages.count());
    }
    @Test void connectWithoutCsrfIsRejected() throws Exception {
        Socket socket=connect(login("ws_customer"),false);
        String frame=socket.frames.poll(5,TimeUnit.SECONDS); assertNotNull(frame); assertTrue(frame.startsWith("ERROR")||frame.equals("CLOSED"),frame);
    }
}
