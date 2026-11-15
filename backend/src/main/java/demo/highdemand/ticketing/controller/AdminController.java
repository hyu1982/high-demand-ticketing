package demo.highdemand.ticketing.controller;

import demo.highdemand.ticketing.service.TicketService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TicketService ticketService;

    public AdminController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

//    @GetMapping("/test")
//    public String test() {
//        return "Admin access granted";
//    }
//
//    @GetMapping("/service-test")
//    public String serviceTest() {
//        return ticketService.getAdminInformation();
//    }
}
