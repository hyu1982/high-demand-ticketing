ALTER TABLE OutboxEvent
    ADD Publishing BIT NOT NULL
    CONSTRAINT DF_OutboxEvent_Publishing DEFAULT 0;