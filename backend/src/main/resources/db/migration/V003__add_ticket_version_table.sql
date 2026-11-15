ALTER TABLE [dbo].[Tickets]
    ADD Version BIGINT NOT NULL
    CONSTRAINT DF_Tickets_Version DEFAULT 0;