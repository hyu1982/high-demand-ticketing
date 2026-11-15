ALTER TABLE dbo.Orders
    ADD IdempotencyKey NVARCHAR(100) NULL;
GO

CREATE UNIQUE INDEX UX_Orders_IdempotencyKey
    ON dbo.Orders (IdempotencyKey)
    WHERE IdempotencyKey IS NOT NULL;
GO