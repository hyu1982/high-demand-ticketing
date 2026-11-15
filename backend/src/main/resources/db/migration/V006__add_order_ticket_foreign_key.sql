ALTER TABLE Orders
    ADD CONSTRAINT FK_Orders_Tickets
        FOREIGN KEY (TicketId)
            REFERENCES Tickets(Id);