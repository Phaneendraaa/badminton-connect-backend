# Badminton Connect Backend

REST + WebSocket backend for **Badminton Connect**, a mobile app (React Native) that helps badminton players find partners, organise singles and doubles matches, track results and build a rating.

Players sign in with their phone number, post open games or challenge friends directly, chat inside match rooms, record set scores, and earn an Elo rating and a trust score from the matches they play.

## Features

- **Phone + OTP authentication.** No passwords. Short-lived JWT access tokens (1 day) and rotating, one-time-use refresh tokens (7 days) stored server-side so they can be revoked.
- **Open match posts.** Publish a game, browse the feed, request to join, and let the organiser accept or reject requests. Slot counts are protected against concurrent accepts.
- **Challenge a friend.** Create a private room, invite players, accept or decline invites, assign teams.
- **Live match play.** Start a match, record set scores, remove a player, finish the match. State changes are broadcast over WebSocket.
- **Match chat.** Per-match chat over STOMP/SockJS, restricted to confirmed players.
- **Elo ratings.** Singles and doubles supported (doubles uses the team average). Every player starts at 1000, K-factor 32.
- **Trust score.** Starts at 80, capped to [0, 100]. +1 for each completed match, -10 for a no-show.
- **Profiles and analytics.** Player profiles, stats, search and per-player analytics.
- **Notifications.** In-app notifications plus Expo push notifications.

## Tech stack

| Area | Technology |
|------|------------|
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation, Security, WebSocket, Actuator) |
| Database | MySQL |
| Auth | JWT (jjwt 0.12), phone OTP via Twilio |
| Realtime | STOMP over SockJS (`/ws`) |
| Mapping / boilerplate | ModelMapper, Lombok |
| Build | Maven (wrapper included) |
| Tests | JUnit 5, Mockito, Spring Security Test |

## Project layout

```
src/main/java/com/app/badminton_backend
├── auth/        login, OTP, JWT, refresh tokens, security + WebSocket config
├── match/       posts, join requests, challenges, match play, chat, notifications
├── elo/         Elo calculator and rating service
├── profile/     profiles, stats, analytics, trust score
├── exceptions/  domain exceptions and global exception handler
└── reference/   reference data (e.g. cities)
```

## Getting started

### Prerequisites

- JDK 17 or newer
- MySQL 8 running locally
- (Optional) a Twilio account for real SMS delivery

### Configuration

`application.properties` is intentionally git-ignored. Create `src/main/resources/application.properties` with your own values:

```properties
server.port=8081

spring.datasource.url=jdbc:mysql://localhost:3306/badminton
spring.datasource.username=<db-user>
spring.datasource.password=<db-password>
spring.jpa.hibernate.ddl-auto=update

# At least 32 characters. Generate your own; never commit it.
jwt.secretKey=<long-random-secret>

twilio.accountSid=<twilio-account-sid>
twilio.authToken=<twilio-auth-token>
twilio.trialNumber=<twilio-phone-number>

expo.push_url=https://exp.host/--/api/v2/push/send
```

Keep real credentials in environment variables or a secrets manager, not in the repository.

### Run

```bash
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`. The API is served at `http://localhost:8081` (or your configured port). Health check: `GET /actuator/health`.

### Test

```bash
./mvnw test
```

The unit tests cover the Elo calculator and service, trust-score rules, OTP login and token refresh flows, JWT utilities, and match-room aggregation. They use mocks, so they need no database.

`BadmintonBackendApplicationTests` boots the full Spring context and therefore needs a reachable database and the properties above. To run only the unit tests:

```bash
./mvnw test -Dtest='!BadmintonBackendApplicationTests'
```

## API overview

Everything except `/auth/**`, `/ws/**` and `/actuator/health` requires the header `Authorization: Bearer <accessToken>`. Detailed authentication docs are in [`document/auth-api.md`](document/auth-api.md).

| Prefix | Purpose |
|--------|---------|
| `POST /auth/login`, `/auth/login/verify-otp`, `/auth/refresh-token` | OTP login and token rotation |
| `/current/user`, `/user/*` | Current user, user search, push-token registration |
| `/profile/*` | Create/update profile, stats, search, analytics |
| `/match-post/*` | Create, browse, cancel and extend open match posts |
| `/match-join-request/*`, `/match-post/{id}/request` | Request to join, accept, reject, cancel, leave |
| `/challenge-friend/*` | Private rooms, invites, team assignment, match history |
| `/match/my-rooms` | All active rooms for the current user |
| `/match-play/*` | Start, assign teams, record sets, remove player, finish |
| `/match-chat/*`, `/post-inquiry/*` | Chat threads and pre-match inquiries |
| `/notifications/*` | List, mark read, unread count |
| `/reference/cities` | Reference data |

### WebSocket

Connect with SockJS to `/ws` and send the access token in the STOMP `CONNECT` frame header (`Authorization: Bearer <token>`). Send chat messages to `/app/chat.send`. Subscribe to `/topic/match/{matchId}` for chat (confirmed match players only) and `/topic/match-play/{matchId}` for live match-state updates.

## Related

The mobile client (React Native) lives in a separate repository.
