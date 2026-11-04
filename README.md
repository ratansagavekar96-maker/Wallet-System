# Wallet System – Spring Boot REST API

A secure digital wallet backend built with Spring Boot. It supports user registration, wallet balance management, money transfers, transaction history, and admin operations.

## Features

- Register and log in users using email and password.
- Automatically create a wallet with a zero balance for each new user.
- Add money and transfer money between wallets.
- View wallet details and paginated transaction history.
- Admin APIs to view all wallets and transactions.
- JWT authentication and role-based authorization.
- Idempotency and database locking to help prevent duplicate processing and balance inconsistencies.

## Technologies Used

- Java 17
- Spring Boot
- Spring Data JPA / Hibernate
- Spring Security and JWT
- H2 Database
- Maven
- JUnit and Mockito
- Postman

## Architecture

`Controller → Service → Repository → H2 Database`

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/register` | Register a user and create a wallet |
| POST | `/auth/login` | Log in and receive a JWT |
| GET | `/wallet` | View the logged-in user's wallet |
| POST | `/wallet/add` | Add money |
| POST | `/wallet/transfer` | Transfer money to another user |
| GET | `/wallet/transactions` | View transaction history |
| GET | `/admin/wallets` | View all wallets (ADMIN) |
| GET | `/admin/transactions` | View all transactions (ADMIN) |

Protected APIs require `Authorization: Bearer <JWT>`. Add-money and transfer requests also require an `Idempotency-Key` header.

## Security

- Passwords are stored using BCrypt hashing.
- Users log in to receive a signed JWT.
- A JWT filter validates tokens on protected requests.
- `USER` can access their own wallet and transactions.
- `ADMIN` can access the admin APIs.
- Wallet ownership is determined from the authenticated user.

## Idempotency

The add-money and transfer APIs require an `Idempotency-Key`. The key is stored with the transaction and protected by a database unique constraint. Repeating the same request with the same key returns the previous transaction result instead of processing it again. Reusing a key with different operation details is rejected.

## Concurrency Strategy

Wallet updates use database-level pessimistic write locks. For transfers, both wallets are locked in ascending wallet-ID order to reduce deadlock risk. The wallet entity also uses JPA `@Version` for optimistic version tracking. No in-memory or `synchronized` locks are used.

## Transaction Boundaries

Money operations run inside Spring `@Transactional` service methods. A transfer debits the sender, credits the receiver, and records the transaction in one database transaction. Registration creates the user and wallet in one transaction. Database changes are rolled back if an operation fails before commit.

## Run Locally

### Prerequisites

- JDK 17 or later
- Git

### Steps

1. Clone the repository:

   ```bash
   git clone https://github.com/ratansagavekar96-maker/wallet-system.git
   ```

2. Open the `Wallet-System` folder in STS or another Java IDE.

3. Set the `JWT_SECRET` environment variable to a Base64-encoded secret compatible with the configured JWT algorithm. Do not commit secrets to Git.

4. From the `Wallet-System` folder, start the application:
5. The application runs at `http://localhost:8080`.
6. Test APIs using Postman

The H2 database settings are in `application.yml`. If the H2 console is enabled, open `http://localhost:8080/h2-console` and use the JDBC URL and credentials from that file.

