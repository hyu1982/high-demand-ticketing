CREATE TABLE [dbo].[Tickets](
    [Id] [bigint] IDENTITY(1,1) NOT NULL,
    [TicketNumber] [nvarchar](500) NULL,
    [Status] [nvarchar](500) NULL,
    [Price] DECIMAL(10,2) NOT NULL DEFAULT 0,
    CONSTRAINT [PK_Tickets] PRIMARY KEY CLUSTERED
(
[Id] ASC
)
    ) ON [PRIMARY]
    GO