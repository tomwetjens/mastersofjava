package nl.moj.cdk;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationActionProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCondition;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

class MojController extends Construct {

    MojController(MojStack stack) {
        super(stack, "Controller");

        var taskExecutionRole = Role.fromRoleArn(this, "TaskExecutionRole", "arn:aws:iam::" + stack.getAccount() + ":role/ecsTaskExecutionRole");

        var taskDef = FargateTaskDefinition.Builder.create(this, "TaskDef")
                .cpu(1024)
                .memoryLimitMiB(4096)
                .executionRole(taskExecutionRole)
//                .volumes(List.of(
//                        Volume.builder()
//                                .efsVolumeConfiguration(
//                                        EfsVolumeConfiguration.builder()
//                                                .fileSystemId("fs-12345678")
//                                                .rootDirectory("/path/to/mount")
//                                                .build()
//                                )
//                                .build()
//                ))
                .build();

        var postgresContainer = taskDef.addContainer("PostgresContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("postgres:15"))
                .cpu(512)
                .memoryReservationMiB(1024)
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
                                .logGroupName("MojControllerPostgres")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .build())
                        .streamPrefix("Postgres")
                        .build()))
                .build());

//        var controllerContainer = taskDef.addContainer("ControllerContainer", ContainerDefinitionOptions.builder()
//                .image(ContainerImage.fromRegistry(stack.registry + "/moj/moj-controller:17"))
//                .memoryReservationMiB(4096)
//                .environment(Map.of(
//                        "OIDC_ISSUER_URI", "http://host.docker.internal:8888/realms/moj",
//                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/moj",
//                        "SPRING_DATASOURCE_USERNAME", "moj",
//                        "SPRING_DATASOURCE_PASSWORD", "moj",
//                        "SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver"
//                ))
//                .portMappings(List.of(
//                        PortMapping.builder()
//                                .containerPort(8080)
//                                .hostPort(8080)
//                                .build(),
//                        PortMapping.builder()
//                                .containerPort(61616)
//                                .hostPort(61616)
//                                .build()
//                ))
//                .build());
//
//        controllerContainer
//                .addContainerDependencies(ContainerDependency.builder()
//                        .container(postgresContainer)
//                        .condition(ContainerDependencyCondition.START)
//                        .build());

        var service = FargateService.Builder.create(this, "Service")
                .cluster(stack.cluster)
                .desiredCount(1)
                .serviceName("MojController")
                .taskDefinition(taskDef)
                .build();
//
//        var targetGroup = stack.httpListener.addTargets("ControllerService", AddApplicationTargetsProps.builder()
//                .targetGroupName("MojControllerTargetGroup")
//                .port(8080)
//                .targets(List.of(service))
//                .healthCheck(HealthCheck.builder()
//                        .enabled(false)
//                        .build())
//                .build());
//
//        stack.httpListener.addAction("ControllerServiceAction", AddApplicationActionProps.builder()
//                .priority(20)
//                .action(ListenerAction.forward(List.of(targetGroup)))
//                .build());
    }
}
