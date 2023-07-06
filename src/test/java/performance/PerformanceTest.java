package performance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.gatling.core.json.Json;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import performance.util.OidcToken;
import performance.util.RestClient;
import performance.util.User;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class PerformanceTest extends Simulation {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTest.class);

    private final HttpProtocolBuilder httpProtocol = http.baseUrl(Conf.mojServerUrl)
            .header("Authorization", session -> ((OidcToken) Objects.requireNonNull(session.get("token"))).bearerHeader())
            .userAgentHeader("Gatling2")
            .wsBaseUrl(Conf.mojServerUrl.replace("http", "ws"))
            .wsReconnect()
            .wsAutoReplySocketIo4()
            .wsMaxReconnects(1000);

    private String superAwesomeNamingServiceUUID;
    private final List<String> testUUIDs = new ArrayList<>();

    private final List<User> users = new ArrayList<>();
    private String sessionUUID;

    private User mojAdmin = null;

    private final ChainBuilder createUser = exec(session -> {
        User user = initializeUser(session);
        users.add(user);
        return session.set("user", user).set("token", RestClient.getOidcToken(user));
    });

    // do we need this?
    private final ChainBuilder joinCompetition = exec(s -> {
        http("Join Competition").get("/play").check(status().is(200));
        return s;
    });

    private final ChainBuilder createTeam = exec(
            http("Create team").post("/team")
                    .formParam("name", session -> {
                        log.info("Using team name {}", ((User) session.get("user")).team());
                        return ((User) session.get("user")).team();
                    })
                    .formParam("company", "Monkey Inc")
                    .formParam("country", "NL")
                    .formParam("submit", "")
                    .check(status().is(200)));

    private final ChainBuilder joinAssignment = exec(http("Get assignment info").get("/play")
            .check(status().is(200))
            .check(bodyString().saveAs("assignmentHtml"))).exec(session -> {
                parseHtml(session.getString("assignmentHtml"));
                return session;
            })
            .exec(ws("Connect WebSocket")
                    .connect("/ws/session/websocket")
                    .onConnected(exec(ws("Connect with Stomp").sendText("""
                                    CONNECT
                                    accept-version:1.0,1.1,1.2
                                    heart-beat:4000,4000

                                    \u0000
                                    """)
                            .await(10)
                            .on(ws.checkTextMessage("Check connection")
                                    .check(regex("CONNECTED\n.*")))).exec(ws("Subscribe to user destination").sendText("""
                            SUBSCRIBE
                            ack:client
                            id:sub-0
                            destination:/user/queue/session

                            \u0000
                            """))));

    private final ChainBuilder successCompile = exec(ws("Compile (Success)")
            .sendText(session -> getCompileMessage(Conf.assignment.getEmpty()))
            .await(10)
            .on(ws.checkTextMessage("Compile Started").check(regex(".*COMPILING_STARTED.*")).silent())
            .await(10)
            .on(ws.checkTextMessage("Compile (Success) Response").check(regex(".*COMPILE.*success\":true.*"))));

    private final ChainBuilder failCompile = exec(ws("Compile wrong code")
            .sendText(session -> getCompileMessage(Conf.assignment.getDoesNotCompile()))
            .await(10)
            .on(ws.checkTextMessage("Compile finished").check(regex(".*COMPILE.*success\":false.*"))));

    private final ChainBuilder attemptCompileAndTest = exec(
            ws("Attempt #{i}")
                    // randomly choose how many UT's will fail.
                    .sendText(session -> getTestMessage(
                            either(createOptions(Conf.assignment.getAttempts()))))
                    .await(10)
                    .on(ws.checkTextMessage("Testing started").check(regex(".*TESTING_STARTED.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Compile success").check(regex(".*COMPILE.*success\":true.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test0 done").check(regex(".*Test0.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test1 done").check(regex(".*Test1.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test2 done").check(regex(".*Test2.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test3 done").check(regex(".*Test3.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test4 done").check(regex(".*Test4.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test5 done").check(regex(".*Test5.*")))
                    .await(10)
                    .on(ws.checkTextMessage("Test6 done").check(regex(".*Test6.*")))
    );

    private final ChainBuilder submit = exec(ws("Submit").sendText(session -> getSubmitMessage(Conf.assignment.getSolution()))
            .await(10)
            .on(ws.checkTextMessage("Submit started").check(regex(".*SUBMIT_STARTED.*")))
            .await(10)
            .on(ws.checkTextMessage("Compile success").check(regex(".*COMPILE.*success.*true.*")))
            .await(10)
            .on(ws.checkTextMessage("Test0 done").check(regex(".*Test0.*")))
            .await(10)
            .on(ws.checkTextMessage("Hidden test done").check(regex(".*HiddenTest.*")))
            .await(10)
            .on(ws.checkTextMessage("Test1 done").check(regex(".*Test1.*")))
            .await(10)
            .on(ws.checkTextMessage("Test2 done").check(regex(".*Test2.*")))
            .await(10)
            .on(ws.checkTextMessage("Test3 done").check(regex(".*Test3.*")))
            .await(10)
            .on(ws.checkTextMessage("Test4 done").check(regex(".*Test4.*")))
            .await(10)
            .on(ws.checkTextMessage("Test5 done").check(regex(".*Test5.*")))
            .await(10)
            .on(ws.checkTextMessage("Test6 done").check(regex(".*Test6.*")))
            .await(10)
            .on(ws.checkTextMessage("Submit done").check(regex(".*SUBMIT.*"))));

    public void before() {
        mojAdmin = RestClient.createAdminUser(Conf.mojAmin);
    }

    public void after() {
        // clean up
        for (User user : users) {
            try {
                RestClient.deleteTeam(mojAdmin, user.team());
                RestClient.deleteKeycloakUser(user);
            } catch (Exception e) {
                log.error("Cleanup of user {} failed", user.username(), e);
            }
        }
        RestClient.deleteKeycloakUser(mojAdmin);
    }

    public PerformanceTest() {
        // This is where the test starts:
        ScenarioBuilder scn = scenario("Test " + Conf.assignment.getAssignmentName())
                .exec(createUser, createTeam, joinAssignment)
                .pause(1)
                .exec(successCompile) //, failCompile)
//                .repeat(Conf.attemptCount, "i")
//                    .on(attemptCompileAndTest.pause(session -> Duration.ofSeconds(Conf.waitTimeBetweenSubmits.get())))
//                .pause(1)
//                .exec(submit)
                .pause(1)
                .exec(ws("Close Websocket").close());
        setUp(scn.injectOpen(rampUsers(Conf.teams).during(Conf.ramp))).protocols(httpProtocol);
    }

    private static User initializeUser(Session session) {
        User user = RestClient.createUser(String.format("pt-user-%d", session.userId()));
        return new User(user.id(), user.username(), user.password(), String.format("pt-team-%d", session.userId()));
    }

    private void parseHtml(String html) {
//        System.out.println("-------------------------------------");
//        System.out.println(html);
//        System.out.println("--------------------------------------");
        // We are looking at the tab components. Each has an 'id' that starts with 'cm-' and then the UUID of the tab.
        String[] split = html.split("id=\"cm-");

        // The amount of tabs should be 9, so the length of the array should be 10 (index 0 is the html before the tabs).
        // Otherwise the wrong assignment might be active (or none)
        if (split.length != Conf.assignment.getTabCount() + 1) {
            //System.out.println(html);
            System.err.println(">>>>>> Is the " + Conf.assignment.getAssignmentName() + " assignment currently running?");
            after();
            System.exit(1);
        }

        // ignore 0, it is the html before the tabs so no UUIDs there
        // ignore 1, it's the uuid of the assignment tab
        // 2 is the SuperAwesomeNameService
        superAwesomeNamingServiceUUID = split[2].substring(0, 36);
        // the rest are all tests
        for (int i = 3; i < split.length; i++) {
            testUUIDs.add(split[i].substring(0, 36));
        }

        sessionUUID = html.split("session = '")[1].substring(0, 36);
    }

    private String getCompileMessage(String code) {
        String content = Json.stringify(Message.builder()
                .sources(Collections.singletonList(new Message.Source(superAwesomeNamingServiceUUID, code)))
                .tests(null)
                .assignmentName(Conf.assignment.getAssignmentName())
                .uuid(sessionUUID)
                .timeLeft("60")
                .arrivalTime(null)
                .build(), false);

        return "SEND\n" + "destination:/app/submit/compile\n" + "content-length:" + content.length() + "\n" + "\n" + content + "\u0000";
    }

    private String getTestMessage(String code) {
        return getTestMessage(code, "test");
    }

    private String getSubmitMessage(String code) {
        return getTestMessage(code, "submit");
    }

    private String getTestMessage(String code, String endpoint) {
        String content = Json.stringify(Message.builder()
                .sources(Collections.singletonList(new Message.Source(superAwesomeNamingServiceUUID, code)))
                .tests(testUUIDs)
                .assignmentName(Conf.assignment.getAssignmentName())
                .uuid(sessionUUID)
                .timeLeft("60")
                .arrivalTime(null)
                .build(), false);

        return "SEND\n" + "destination:/app/submit/" + endpoint + "\n" + "content-length:" + content.length() + "\n" + "\n" + content + "\u0000";
    }

    public static <T> T either(T... options) {
        return options[random(options.length)];
    }

    public static String[] createOptions(int attempts) {
        String[] options = new String[attempts + 1];
        options[0] = Conf.assignment.getEmpty();

        for( int i=1; i < options.length; i++ ) {
            options[i] = Conf.assignment.getAttempt(i-1);
        }
        return options;
    }

    public static int random(int maxExclusive) {
        return (int) (Math.random() * maxExclusive);
    }

    public static int random(int min, int maxExclusive) {
        return (int) (Math.random() * (maxExclusive - min)) + min;
    }

}
