package nl.moj.cdk;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.globalaccelerator.ConnectionProtocol;
import software.amazon.awscdk.services.globalaccelerator.ListenerOptions;
import software.amazon.awscdk.services.globalaccelerator.PortRange;
import software.constructs.Construct;

import java.util.List;

class MojStack extends Stack {

    MojStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
    }

    Vpc vpc = Vpc.Builder.create(this, "Vpc")
            .vpcName("MojVpc")
            .build();

    Cluster cluster = Cluster.Builder.create(this, "Cluster")
            .clusterName("MojCluster")
            .vpc(vpc)
            .build();

//    String registry = this.getAccount() + ".dkr.ecr." + this.getRegion() + ".amazonaws.com";

    ApplicationLoadBalancer loadBalancer = ApplicationLoadBalancer.Builder.create(this, "LoadBalancer")
            .vpc(vpc)
            .internetFacing(true)
            .loadBalancerName("MojLoadBalancer")
            .build();

    ApplicationListener httpListener = loadBalancer.addListener("HttpListener", ApplicationListenerProps.builder()
            .loadBalancer(loadBalancer)
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .open(true)
            .defaultAction(ListenerAction.fixedResponse(404, FixedResponseOptions.builder().build()))
            .build());

    MojKeycloak keycloak = new MojKeycloak(this);
//    MojController controller = new MojController(this);
}
