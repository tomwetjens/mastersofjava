package nl.moj.cdk;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

class MojKeycloak extends Construct {

    MojKeycloak(MojStack stack) {
        super(stack, "Keycloak");

        var taskExecutionRole = Role.fromRoleArn(this, "TaskExecutionRole", "arn:aws:iam::" + stack.getAccount() + ":role/ecsTaskExecutionRole");

        var taskDef = FargateTaskDefinition.Builder.create(this, "TaskDef")
                .cpu(512)
                .memoryLimitMiB(1024)
                .executionRole(taskExecutionRole)
                .build();

        var postgresContainer = taskDef.addContainer("PostgresContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("postgres:15"))
                .cpu(256)
                .memoryReservationMiB(512)
                .environment(Map.of(
                        "POSTGRES_USER", "postgres",
                        "POSTGRES_PASSWORD", "postgres"
                ))
                .portMappings(List.of(
                        PortMapping.builder()
                                .containerPort(5432)
                                .hostPort(5432)
                                .build()
                ))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "PostgresLogGroup")
                                .logGroupName("MojKeycloakPostgres")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .build())
                        .streamPrefix("Postgres")
                        .build()))
                .build());

        var keycloakContainer = taskDef.addContainer("KeycloakContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("quay.io/keycloak/keycloak:21.1"))
                .cpu(256)
                .memoryReservationMiB(512)
                .environment(Map.of(
                        "KEYCLOAK_ADMIN", "admin",
                        "KEYCLOAK_ADMIN_PASSWORD", "admin",
                        "KC_DB", "postgres",
                        "KC_DB_USERNAME", "postgres",
                        "KC_DB_PASSWORD", "postgres",
                        "KC_DB_URL", "jdbc:postgresql://localhost:5432/postgres"
                ))
                .command(List.of(
                        "start",
                        "--hostname=host.docker.internal",
                        "--hostname-strict-https=false",
                        "--http-enabled=true"
                ))
                .portMappings(List.of(
                        PortMapping.builder()
                                .containerPort(8080)
                                .hostPort(8080)
                                .build()
                ))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "KeycloakLogGroup")
                                .logGroupName("MojKeycloak")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .build())
                        .streamPrefix("Keycloak")
                        .build()))
                .build());

        keycloakContainer
                .addContainerDependencies(ContainerDependency.builder()
                        .container(postgresContainer)
                        .condition(ContainerDependencyCondition.START)
                        .build());

        var service = FargateService.Builder.create(this, "Service")
                .cluster(stack.cluster)
                .desiredCount(1)
                .serviceName("MojKeycloak")
                .taskDefinition(taskDef)
                .build();

        service.getConnections().allowFrom(stack.loadBalancer, Port.tcp(8080));

        var targetGroup = stack.httpListener.addTargets("KeycloakService", AddApplicationTargetsProps.builder()
                .targetGroupName("MojKeycloakTargetGroup")
                .port(8080)
                .targets(List.of(service))
                .healthCheck(HealthCheck.builder()
                        .path("/")
                        .port("8080")
                        .build())
                .build());

        stack.httpListener.addAction("KeycloakServiceAction", AddApplicationActionProps.builder()
                .priority(10)
                .conditions(List.of(ListenerCondition.pathPatterns(List.of(
                        "/auth/*",
                        "/admin", "/admin/*",
                        "/realms"
                ))))
                .action(ListenerAction.forward(List.of(targetGroup)))
                .build());
    }
}
