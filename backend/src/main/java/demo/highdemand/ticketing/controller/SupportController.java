package demo.highdemand.ticketing.controller;

import demo.highdemand.ticketing.service.TicketService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final TicketService ticketService;

    public SupportController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

//    @GetMapping("/test")
//    public String test() {
//        return "Support access granted";
//    }
//
//    @GetMapping("/service-test/{ticketId}")
//    public String serviceTest(
//            @PathVariable Long ticketId) {
//
//        return ticketService.getSupportInformation(ticketId);
//    }
}
