package demo.highdemand.ticketing.event;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dlt")
public class DltReplayController {

    private final DltReplayService dltReplayService;

    public DltReplayController(
            DltReplayService dltReplayService) {

        this.dltReplayService = dltReplayService;
    }

    @PostMapping("/replay")
    public ResponseEntity<Void> replay(
            @RequestBody OrderCreatedEvent event) {

        dltReplayService.replay(event);

        return ResponseEntity.accepted().build();
    }
}