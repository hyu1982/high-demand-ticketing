package demo.highdemand.ticketing.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.highdemand.ticketing.notification.Notification;
import demo.highdemand.ticketing.notification.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Timer;

@Component
public class OrderCreatedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final MeterRegistry meterRegistry;
    private final Timer processingTimer;

    public OrderCreatedConsumer(ProcessedEventRepository processedEventRepository, ObjectMapper objectMapper, NotificationRepository notificationRepository
                                , MeterRegistry meterRegistry) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.notificationRepository = notificationRepository;
        this.meterRegistry = meterRegistry;

        this.processingTimer = Timer.builder(
                        "kafka.order.created.processing")
                .description("Time spent processing OrderCreatedEvent")
//                .publishPercentiles(
//                        0.95,
//                        0.99
//                )
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /*
    @KafkaListener(
            topics = "order-created",
            groupId = "order-notification-service"
    )
    public void consume(OrderCreatedEvent event) {

        if (processedEventRepository.existsByEventId(event.eventId())) {
            System.out.println(
                    "Skipping already processed event: "
                            + event.eventId()
            );
            return;
        }

        System.out.println(
                "Processing OrderCreatedEvent: " + event
        );

        processedEventRepository.save(new ProcessedEvent(event.eventId())
        );
    }*/

    @Transactional
    @KafkaListener(
            topics = "order-created",
            groupId = "order-notification-service"
    )
    public void consume(OrderCreatedEvent event) {

//        if (event.version() == 1) {
//            System.out.println("Processing OrderCreatedEvent V1");
//        } else if (event.version() == 2) {
//            System.out.println("Processing OrderCreatedEvent V2");
//        } else {
//            System.out.println("Unsupported OrderCreatedEvent version: " + event.version());
//            return;
//        }
//
//        System.out.println(
//                "Consumer instance: "
//                        + System.getenv().getOrDefault("HOSTNAME", "local")
//                        + " | Event: "
//                        + event.eventId()
//        );

//        long start = System.nanoTime();

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            if (processedEventRepository.existsByEventId(
                    event.eventId())) {

                System.out.println(
                        "Skipping already processed event: "
                                + event.eventId()
                );

                return;
            }

            System.out.println(
                    "Processing OrderCreatedEvent: " + event
            );

            Notification notification = new Notification(
                    event.eventId(),
                    event.orderId(),
                    event.customerEmail()
            );

            notificationRepository.save(notification);

//        if (true) {
//            throw new RuntimeException("TEST: Simulated consumer failure");
//        }

            processedEventRepository.save(
                    new ProcessedEvent(event.eventId())
            );
        }
        finally {

//            long elapsedNanos =
//                    System.nanoTime() - start;
//
//            long elapsedMillis =
//                    elapsedNanos / 1_000_000;
//
//            System.out.println(
//                    "OrderCreatedEvent processing latency: "
//                            + elapsedMillis
//                            + " ms"
//            );
            sample.stop(processingTimer);
        }
    }
}