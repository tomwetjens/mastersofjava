package nl.moj.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;

public class MojApp {
    public static void main(final String[] args) {
        App app = new App();

        new MojStack(app, "MojStack", StackProps.builder()
                .env(Environment.builder()
                        .account("598172618529")
                        .region("eu-west-1")
                        .build())
                .build());

        app.synth();
    }
}

