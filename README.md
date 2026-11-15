# High Demand Ticketing System

A full-stack ticketing application designed to demonstrate modern backend development, API design, authentication, caching, asynchronous messaging, and scalable system architecture.

## Overview

This project explores the engineering challenges associated with high-demand ticket purchasing, including concurrent requests, ticket availability, transaction processing, and reliable communication between application components.

The application consists of a frontend client and a Java Spring Boot backend.

## Technology Stack

| Component | Technology |
|---|---|
| Backend | Java 25, Spring Boot |
| Frontend | Angular 22 |
| Database | Microsoft SQL Server |
| Database migrations | Flyway |
| Messaging | Apache Kafka |
| Caching | Redis |
| Authentication | JWT |
| API Gateway | Spring Boot |
| Build | Maven |
| Containerization | Docker |

## Architecture

The application uses a frontend/backend architecture with independently organized components.

### High-Level Request Flow

1. The user interacts with the frontend.
2. Requests are sent through the API Gateway.
3. The backend validates authentication and processes the request.
4. Business logic handles ticket availability and purchasing operations.
5. Persistent data is stored in the relational database.
6. Redis supports caching and time-sensitive data.
7. Kafka supports asynchronous processing and communication between components.
8. Failed asynchronous messages can be routed through a Dead Letter Topic (DLT) for subsequent investigation or recovery.

The exact processing sequence depends on the operation being performed.

## Key Engineering Concepts

This project demonstrates practical implementation of:

- RESTful API development using Spring Boot.
- Authentication and authorization using JWT.
- Separation of concerns and layered application architecture.
- Relational database persistence and schema migrations.
- Redis caching and expiration policies.
- Asynchronous event processing with Apache Kafka.
- Kafka error handling and Dead Letter Topics.
- API Gateway integration.
- Handling concurrent requests and high-demand purchasing scenarios.

*Only retain features that are implemented in the published version.*

## Project Structure

```text
high-demand-ticketing/
│
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── frontend/
│   ├── src/
│   ├── package.json
│   └── ...
│
├── README.md
└── .gitignore
```

## Getting Started

### Prerequisites

- Java 25
- Maven
- Node.js and npm
- Docker
- Git
- Microsoft SQL server that you can configure. Flyaway migration script will populate the database schema

### Backend Setup

1. Clone the repository.
2. Navigate to the backend directory.
3. Configure the required environment variables.
4. Start the required infrastructure services.
5. Run the Spring Boot application.

```bash
cd backend
mvn spring-boot:run
```

### Frontend Setup

Navigate to the frontend directory:

```bash
cd frontend
npm install
npm start
```

Use the actual frontend start command configured in `package.json`.

### Infrastructure

Configure and start the required services:

- SQL database
- Redis
- Apache Kafka

See the backend configuration for the required connection settings.

**Important:** Never commit production credentials, JWT signing secrets, database passwords, or private certificates. Use environment variables or local configuration files.

## API

Base API path:

```text
/api/v1
```

Document the implemented endpoints here, including authentication requirements, request examples, and response examples.

## Design Considerations

The project focuses on several important distributed-system engineering concerns:

- Preventing overselling during periods of high demand.
- Maintaining consistency between ticket availability and purchase operations.
- Using caching without treating cached data as the authoritative source of truth.
- Separating synchronous API operations from asynchronous processing.
- Handling message failures and supporting recovery.
- Designing for reliability when individual infrastructure components become unavailable.

## Future Improvements

Potential areas for continued development:

- Load testing and performance benchmarking.
- Improved observability and distributed tracing.
- Additional resilience and retry mechanisms.
- Automated integration and concurrency testing.
- CI/CD pipeline using GitHub Actions.

## Author

Developed as a full-stack engineering portfolio project to demonstrate Java, Spring Boot, frontend development, and distributed-system design.
