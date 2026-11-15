package demo.highdemand.ticketing.event; // Change to your application's package

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Override
    public Health health() {


        try (AdminClient admin = AdminClient.create(
                Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers
                ))) {

            var nodes = admin.describeCluster()
                    .nodes()
                    .get(3, TimeUnit.SECONDS);

            return Health.up()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("brokerCount", nodes.size())
                    .build();

        } catch (Exception ex) {
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}