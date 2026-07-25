# DesignForge Phase 1 — Foundation & Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the DesignForge monorepo — full Docker Compose infra, a Spring Boot API with working auth (register/login/logout/me via JWT + Redis-backed refresh tokens), and a Next.js app shell (theme, nav, auth pages) — proven end-to-end by one Playwright smoke test.

**Architecture:** Monorepo with `apps/api` (Spring Boot 3 modular monolith, Java 21), `apps/web` (Next.js 14 App Router, TypeScript, Tailwind, shadcn/ui), `packages/shared-types` (hand-maintained TS DTOs mirroring the API), and `infra/docker-compose.yml` provisioning Postgres, Redis, Kafka+Zookeeper, Elasticsearch, and MinIO. This plan only wires up Postgres (data) and Redis (refresh tokens) — Kafka, Elasticsearch, and MinIO are provisioned here but get their first real consumers in later plans (simulator, patterns, deferred modules respectively), per the design spec.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Security 6, Spring Data JPA, Flyway, jjwt 0.12.x, spring-boot-starter-data-redis, Postgres 16, Redis 7, Next.js 14 (App Router), TypeScript, Tailwind CSS, shadcn/ui, Vitest + React Testing Library, Playwright.

## Global Constraints

- Monorepo layout: `apps/web`, `apps/api`, `packages/shared-types`, `infra/` — exactly as specified in the design spec.
- Full stack provisioned via Docker Compose from day one: Postgres, Redis, Kafka+Zookeeper, Elasticsearch, MinIO.
- `.env` is gitignored; `.env.example` is committed with placeholder values only (`OLLAMA_API_KEY` included even though unused until the AI pipeline plan).
- Raw LLM output must never reach the frontend (not exercised in this plan, but no code here may violate it later — no shortcuts that would make that harder).
- Java 21 / Spring Boot 3 modular monolith — package-per-module (`auth`, `common`, etc.), not microservices.
- Passwords hashed with BCrypt; JWT access tokens short-lived, refresh tokens stored server-side in Redis (not purely stateless), so logout can actually revoke a session.

---

### Task 1: Monorepo scaffold + Docker Compose infra

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `.env.example`
- Create: `.gitignore`
- Modify: `README.md`

**Interfaces:**
- Produces: running containers reachable at `localhost:5432` (Postgres), `localhost:6379` (Redis), `localhost:9092` (Kafka), `localhost:9200` (Elasticsearch), `localhost:9000`/`9001` (MinIO API/console). Every later task in every future plan connects to these.

- [ ] **Step 1: Create the directory skeleton**

```bash
mkdir -p apps/web apps/api packages/shared-types infra
```

- [ ] **Step 2: Write `infra/docker-compose.yml`**

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16
    container_name: designforge-postgres
    environment:
      POSTGRES_DB: designforge
      POSTGRES_USER: designforge
      POSTGRES_PASSWORD: designforge_dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U designforge"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7
    container_name: designforge-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: designforge-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: designforge-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.0
    container_name: designforge-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  minio:
    image: minio/minio:latest
    container_name: designforge-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: designforge
      MINIO_ROOT_PASSWORD: designforge_dev_password
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  postgres_data:
  minio_data:
```

- [ ] **Step 3: Write `.env.example`**

```bash
# Postgres
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=designforge
POSTGRES_USER=designforge
POSTGRES_PASSWORD=designforge_dev_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=replace-with-a-long-random-secret-min-32-bytes
JWT_ACCESS_TOKEN_TTL_MINUTES=15
JWT_REFRESH_TOKEN_TTL_DAYS=7

# Ollama Cloud (wired later in the AI pipeline plan; placeholder for now)
OLLAMA_API_KEY=replace-with-real-key
OLLAMA_BASE_URL=https://ollama.com/api

# MinIO (unused until a later plan; provisioned now)
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=designforge
MINIO_SECRET_KEY=designforge_dev_password
```

- [ ] **Step 4: Write `.gitignore`**

```
# Node
node_modules/
.next/
.turbo/

# Java
target/
*.class

# Env
.env
.env.local

# OS
.DS_Store

# IDE
.idea/
.vscode/
```

- [ ] **Step 5: Update `README.md`**

```markdown
# DesignForge

Master Low-Level Design (LLD) & High-Level Design (HLD) Interviews.

## Monorepo layout

- `apps/api` — Spring Boot 3 backend (Java 21)
- `apps/web` — Next.js 14 frontend
- `packages/shared-types` — shared TypeScript DTOs
- `infra` — Docker Compose infra (Postgres, Redis, Kafka, Elasticsearch, MinIO)
- `docs/superpowers/specs` — design specs
- `docs/superpowers/plans` — implementation plans

## Local development

1. Copy `.env.example` to `.env` and fill in real secrets.
2. `docker compose -f infra/docker-compose.yml up -d`
3. Backend: `cd apps/api && ./mvnw spring-boot:run`
4. Frontend: `cd apps/web && npm install && npm run dev`
```

- [ ] **Step 6: Start infra and verify all containers are healthy**

Run: `docker compose -f infra/docker-compose.yml up -d && sleep 20 && docker compose -f infra/docker-compose.yml ps`
Expected: all six services (`postgres`, `redis`, `zookeeper`, `kafka`, `elasticsearch`, `minio`) show `Up` (postgres/redis/elasticsearch/minio show `healthy`).

- [ ] **Step 7: Commit**

```bash
git add infra/docker-compose.yml .env.example .gitignore README.md
git commit -m "Scaffold monorepo layout and Docker Compose infra"
```

---

### Task 2: Spring Boot skeleton + health endpoint

**Files:**
- Create: `apps/api/pom.xml`
- Create: `apps/api/src/main/java/com/designforge/api/DesignForgeApplication.java`
- Create: `apps/api/src/main/resources/application.yml`
- Create: `apps/api/src/main/java/com/designforge/api/common/HealthController.java`
- Test: `apps/api/src/test/java/com/designforge/api/common/HealthControllerTest.java`

**Interfaces:**
- Consumes: Postgres/Redis containers from Task 1 (`localhost:5432`, `localhost:6379`).
- Produces: `GET /api/health` → `{"status":"UP"}`. All later controllers follow the `com.designforge.api.<module>` package convention established here.

- [ ] **Step 1: Write `apps/api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>com.designforge</groupId>
  <artifactId>api</artifactId>
  <version>0.1.0</version>
  <name>api</name>
  <description>DesignForge backend</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Write the failing health test**

```java
package com.designforge.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd apps/api && ./mvnw -q test -Dtest=HealthControllerTest`
Expected: FAIL — compilation error, `HealthController` does not exist.

(If `mvnw` wrapper isn't present yet, generate it first: `mvn -N io.takari:maven:wrapper`.)

- [ ] **Step 4: Write `DesignForgeApplication.java`**

```java
package com.designforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DesignForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DesignForgeApplication.class, args);
    }
}
```

- [ ] **Step 5: Write `HealthController.java`**

```java
package com.designforge.api.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd apps/api && ./mvnw -q test -Dtest=HealthControllerTest`
Expected: PASS

- [ ] **Step 7: Write `application.yml` wiring Postgres from `.env`**

```yaml
spring:
  application:
    name: designforge-api
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:designforge}
    username: ${POSTGRES_USER:designforge}
    password: ${POSTGRES_PASSWORD:designforge_dev_password}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

jwt:
  secret: ${JWT_SECRET:replace-with-a-long-random-secret-min-32-bytes}
  access-token-ttl-minutes: ${JWT_ACCESS_TOKEN_TTL_MINUTES:15}
  refresh-token-ttl-days: ${JWT_REFRESH_TOKEN_TTL_DAYS:7}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 8: Verify the app boots against Docker Compose Postgres**

Run: `cd apps/api && ./mvnw -q spring-boot:run &` then `curl -s http://localhost:8080/api/health` then stop the process.
Expected: `{"status":"UP"}` and no datasource connection errors in the logs (there are no tables/migrations yet, so `ddl-auto: validate` with zero entities is a no-op — this only proves the JDBC connection succeeds).

- [ ] **Step 9: Commit**

```bash
git add apps/api
git commit -m "Add Spring Boot skeleton with health endpoint and Postgres/Redis config"
```

---

### Task 3: User registration (entity, migration, endpoint)

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V1__create_users_table.sql`
- Create: `apps/api/src/main/java/com/designforge/api/auth/User.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/UserRepository.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/RegisterRequest.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/UserResponse.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/AuthService.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/AuthController.java`
- Create: `apps/api/src/main/java/com/designforge/api/common/ApiException.java`
- Create: `apps/api/src/main/java/com/designforge/api/common/GlobalExceptionHandler.java`
- Create: `apps/api/src/main/java/com/designforge/api/config/SecurityConfig.java`
- Test: `apps/api/src/test/java/com/designforge/api/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UserRepository` (Spring Data JPA, from this task) used by later `AuthService.login`.
- Produces: `POST /api/auth/register` accepting `RegisterRequest{email, password, displayName, locale}` → 201 `UserResponse{id, email, displayName, locale}`; 409 on duplicate email. `AuthService.register(RegisterRequest): UserResponse` — the exact signature Task 4's login method sits next to.

- [ ] **Step 1: Write the failing controller test**

```java
package com.designforge.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_withValidBody_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@example.com", "password123", "Jane", "en");
        UserResponse response = new UserResponse("11111111-1111-1111-1111-111111111111", "jane@example.com", "Jane", "en");
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@example.com", "password123", "Jane", "en");
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ApiException(409, "Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/api && ./mvnw -q test -Dtest=AuthControllerTest`
Expected: FAIL — compilation error, none of `AuthController`, `AuthService`, `RegisterRequest`, `UserResponse`, `ApiException` exist yet.

- [ ] **Step 3: Write the Flyway migration**

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 4: Write `ApiException` and `GlobalExceptionHandler`**

```java
package com.designforge.api.common;

public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
```

```java
package com.designforge.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        return ResponseEntity.status(HttpStatus.valueOf(ex.getStatus()))
                .body(Map.of("message", ex.getMessage()));
    }
}
```

- [ ] **Step 5: Write `User` entity, `UserRepository`, `RegisterRequest`, `UserResponse`**

```java
package com.designforge.api.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String locale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String email, String passwordHash, String displayName, String locale) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.locale = locale;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getLocale() { return locale; }
}
```

```java
package com.designforge.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

```java
package com.designforge.api.auth;

public record RegisterRequest(String email, String password, String displayName, String locale) {}
```

```java
package com.designforge.api.auth;

public record UserResponse(String id, String email, String displayName, String locale) {
    static UserResponse from(User user) {
        return new UserResponse(user.getId().toString(), user.getEmail(), user.getDisplayName(), user.getLocale());
    }
}
```

- [ ] **Step 6: Write `AuthService.register` and `AuthController`**

```java
package com.designforge.api.auth;

import com.designforge.api.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(409, "Email already registered");
        }
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                request.locale() == null ? "en" : request.locale()
        );
        return UserResponse.from(userRepository.save(user));
    }
}
```

```java
package com.designforge.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
```

- [ ] **Step 7: Write `SecurityConfig` permitting auth endpoints (needed for `@WebMvcTest` to not 401 the test requests)**

```java
package com.designforge.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**", "/api/health").permitAll()
                    .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd apps/api && ./mvnw -q test -Dtest=AuthControllerTest`
Expected: PASS

- [ ] **Step 9: Verify the migration runs against real Postgres**

Run: `cd apps/api && ./mvnw -q spring-boot:run &` then `curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"jane@example.com","password":"password123","displayName":"Jane","locale":"en"}'` then stop the process.
Expected: HTTP 201 with the created user JSON; `docker exec designforge-postgres psql -U designforge -d designforge -c "select email from users;"` shows the row.

- [ ] **Step 10: Commit**

```bash
git add apps/api
git commit -m "Add user registration endpoint with Postgres-backed User entity"
```

---

### Task 4: Login endpoint + JWT issuance + Redis-backed refresh tokens

**Files:**
- Create: `apps/api/src/main/java/com/designforge/api/auth/LoginRequest.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/AuthResponse.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/JwtService.java`
- Create: `apps/api/src/main/java/com/designforge/api/auth/RefreshTokenStore.java`
- Modify: `apps/api/src/main/java/com/designforge/api/auth/AuthService.java` (add `login`)
- Modify: `apps/api/src/main/java/com/designforge/api/auth/AuthController.java` (add `POST /api/auth/login`)
- Test: `apps/api/src/test/java/com/designforge/api/auth/AuthControllerTest.java` (extend)
- Test: `apps/api/src/test/java/com/designforge/api/auth/JwtServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByEmail` (Task 3), `PasswordEncoder` bean (Task 3).
- Produces: `POST /api/auth/login` → 200 `AuthResponse{accessToken, refreshToken, user: UserResponse}`; 401 on bad credentials. `JwtService.generateAccessToken(User): String`, `JwtService.parseUserId(String token): UUID` — Task 5's filter consumes `parseUserId` exactly as named here. `RefreshTokenStore.store(UUID userId, String refreshToken)`, `RefreshTokenStore.isValid(UUID userId, String refreshToken): boolean`, `RefreshTokenStore.revoke(UUID userId)` — Task 5's logout endpoint calls `revoke` exactly as named here.

- [ ] **Step 1: Write the failing `JwtServiceTest`**

```java
package com.designforge.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void generateAndParse_roundTripsUserId() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@example.com", "hash", "Jane", "en");
        // reflectively unavailable id in a fresh entity, so parse straight from a hand-built token instead:
        String token = jwtService.generateAccessTokenForUserId(userId);

        UUID parsed = jwtService.parseUserId(token);

        assertEquals(userId, parsed);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/api && ./mvnw -q test -Dtest=JwtServiceTest`
Expected: FAIL — `JwtService` does not exist.

- [ ] **Step 3: Write `JwtService`**

```java
package com.designforge.api.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenTtlMinutes;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public String generateAccessToken(User user) {
        return generateAccessTokenForUserId(user.getId());
    }

    public String generateAccessTokenForUserId(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(accessTokenTtlMinutes))))
                .signWith(key)
                .compact();
    }

    public UUID parseUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/api && ./mvnw -q test -Dtest=JwtServiceTest`
Expected: PASS

- [ ] **Step 5: Write `RefreshTokenStore` (Redis-backed)**

```java
package com.designforge.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenTtlDays;

    public RefreshTokenStore(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-token-ttl-days:7}") long refreshTokenTtlDays
    ) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    private String key(UUID userId) {
        return "refresh:" + userId;
    }

    public void store(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, Duration.ofDays(refreshTokenTtlDays));
    }

    public boolean isValid(UUID userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && stored.equals(refreshToken);
    }

    public void revoke(UUID userId) {
        redisTemplate.delete(key(userId));
    }
}
```

- [ ] **Step 6: Write `LoginRequest`, `AuthResponse`, extend `AuthService` and `AuthController`**

```java
package com.designforge.api.auth;

public record LoginRequest(String email, String password) {}
```

```java
package com.designforge.api.auth;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
```

Add to `AuthService`:

```java
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;

    // constructor now takes all four collaborators:
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenStore refreshTokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(401, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(401, "Invalid email or password");
        }
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = java.util.UUID.randomUUID().toString();
        refreshTokenStore.store(user.getId(), refreshToken);
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }
```

Add to `AuthController`:

```java
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
```

- [ ] **Step 7: Extend `AuthControllerTest` with login cases**

```java
    @Test
    void login_withValidCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "password123");
        AuthResponse response = new AuthResponse(
                "access-token-value",
                "refresh-token-value",
                new UserResponse("11111111-1111-1111-1111-111111111111", "jane@example.com", "Jane", "en")
        );
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "wrong-password");
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ApiException(401, "Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd apps/api && ./mvnw -q test -Dtest=AuthControllerTest,JwtServiceTest`
Expected: PASS (all cases)

- [ ] **Step 9: Commit**

```bash
git add apps/api
git commit -m "Add login endpoint with JWT access tokens and Redis-backed refresh tokens"
```

---

### Task 5: JWT auth filter, `/api/auth/me`, and logout

**Files:**
- Create: `apps/api/src/main/java/com/designforge/api/auth/JwtAuthFilter.java`
- Modify: `apps/api/src/main/java/com/designforge/api/config/SecurityConfig.java` (register filter)
- Modify: `apps/api/src/main/java/com/designforge/api/auth/AuthController.java` (add `/me`, `/logout`)
- Test: `apps/api/src/test/java/com/designforge/api/auth/AuthControllerTest.java` (extend)

**Interfaces:**
- Consumes: `JwtService.parseUserId(String): UUID` (Task 4), `RefreshTokenStore.revoke(UUID)` (Task 4), `UserRepository.findById` (Task 3, inherited from `JpaRepository`).
- Produces: `GET /api/auth/me` → 401 unauthenticated / 200 `UserResponse` authenticated. `POST /api/auth/logout` → 204, revokes the refresh token. This is the last auth surface — later plans' protected endpoints reuse this same filter, they don't add their own.

- [ ] **Step 1: Write the failing test for `/me`**

```java
    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
```

Add the matching `get` static import if not already present: `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;`

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/api && ./mvnw -q test -Dtest=AuthControllerTest#me_withoutToken_returns401`
Expected: FAIL — no `/api/auth/me` route exists (404, not 401), or compile error since the endpoint isn't defined.

- [ ] **Step 3: Write `JwtAuthFilter`**

```java
package com.designforge.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UUID userId = jwtService.parseUserId(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Register the filter in `SecurityConfig`**

Modify the `securityFilterChain` bean to accept and register `JwtAuthFilter`:

```java
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
```

- [ ] **Step 5: Add `/me` and `/logout` to `AuthService` and `AuthController`**

Add to `AuthService`:

```java
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        return UserResponse.from(user);
    }

    public void logout(UUID userId) {
        refreshTokenStore.revoke(userId);
    }
```

Add to `AuthController`:

```java
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(java.security.Principal principal) {
        java.util.UUID userId = java.util.UUID.fromString(principal.getName());
        return ResponseEntity.ok(authService.me(userId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(java.security.Principal principal) {
        java.util.UUID userId = java.util.UUID.fromString(principal.getName());
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
```

Note: `Principal.getName()` returns the `UsernamePasswordAuthenticationToken`'s name, which stringifies the `UUID` principal set in `JwtAuthFilter` — this is why the filter stores the raw `UUID` as the token's principal.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd apps/api && ./mvnw -q test -Dtest=AuthControllerTest`
Expected: PASS — `/me` without a token returns 401 (Spring Security's default entry point rejects unauthenticated requests to a protected route).

- [ ] **Step 7: Manually verify the authenticated path end-to-end**

Run: start the app, register + login to get an `accessToken`, then `curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"`.
Expected: 200 with the user's `UserResponse`. Then `curl -s -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <accessToken>"` returns 204, and `redis-cli -h localhost get refresh:<userId>` (via `docker exec designforge-redis redis-cli get refresh:<userId>`) returns `(nil)`.

- [ ] **Step 8: Commit**

```bash
git add apps/api
git commit -m "Add JWT auth filter, /api/auth/me, and logout with refresh-token revocation"
```

---

### Task 6: Next.js app shell (theme, nav)

**Files:**
- Create: `apps/web/package.json`
- Create: `apps/web/tsconfig.json`
- Create: `apps/web/next.config.mjs`
- Create: `apps/web/tailwind.config.ts`
- Create: `apps/web/postcss.config.mjs`
- Create: `apps/web/app/layout.tsx`
- Create: `apps/web/app/page.tsx`
- Create: `apps/web/app/globals.css`
- Create: `apps/web/components/theme/ThemeProvider.tsx`
- Create: `apps/web/components/theme/ThemeToggle.tsx`
- Create: `apps/web/components/nav/AppShell.tsx`
- Create: `apps/web/vitest.config.ts`
- Create: `apps/web/vitest.setup.ts`
- Test: `apps/web/components/nav/AppShell.test.tsx`

**Interfaces:**
- Produces: `<AppShell>{children}</AppShell>` — the layout wrapper every future page (Learning Hub, Patterns, Interviews, auth pages) renders inside. Nav item list: `{ href: string; label: string }[]` defined in `AppShell.tsx`, extended (not replaced) by later plans.

- [ ] **Step 1: Scaffold the Next.js project**

```bash
cd apps/web
npx --yes create-next-app@14 . --typescript --tailwind --app --eslint --no-src-dir --import-alias "@/*" --use-npm
```

When prompted, accept defaults. This generates `package.json`, `tsconfig.json`, `next.config.mjs`, `tailwind.config.ts`, `postcss.config.mjs`, `app/layout.tsx`, `app/page.tsx`, `app/globals.css` — the remaining steps modify/replace the generated versions of these.

- [ ] **Step 2: Install additional dependencies**

```bash
npm install next-themes lucide-react clsx tailwind-merge class-variance-authority
npm install -D vitest @vitejs/plugin-react @testing-library/react @testing-library/jest-dom jsdom @playwright/test
```

- [ ] **Step 3: Write the failing `AppShell` test**

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  it("renders the primary navigation links", () => {
    render(<AppShell><div>content</div></AppShell>);

    expect(screen.getByRole("link", { name: /learning hub/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /pattern explorer/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /interviews/i })).toBeInTheDocument();
  });

  it("renders the theme toggle button", () => {
    render(<AppShell><div>content</div></AppShell>);

    expect(screen.getByRole("button", { name: /toggle theme/i })).toBeInTheDocument();
  });

  it("renders the page content passed as children", () => {
    render(<AppShell><div>unique-content-marker</div></AppShell>);

    expect(screen.getByText("unique-content-marker")).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Configure Vitest**

```ts
// apps/web/vitest.config.ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    globals: true,
  },
});
```

```ts
// apps/web/vitest.setup.ts
import "@testing-library/jest-dom/vitest";
```

Add to `apps/web/package.json` scripts: `"test": "vitest run"`.

- [ ] **Step 5: Run test to verify it fails**

Run: `cd apps/web && npm test`
Expected: FAIL — `./AppShell` module not found.

- [ ] **Step 6: Write `ThemeProvider` and `ThemeToggle`**

```tsx
// apps/web/components/theme/ThemeProvider.tsx
"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { ReactNode } from "react";

export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider attribute="class" defaultTheme="system" enableSystem>
      {children}
    </NextThemesProvider>
  );
}
```

```tsx
// apps/web/components/theme/ThemeToggle.tsx
"use client";

import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();

  return (
    <button
      aria-label="Toggle theme"
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
      className="rounded-md p-2 hover:bg-muted"
    >
      <Sun className="hidden dark:block h-5 w-5" />
      <Moon className="block dark:hidden h-5 w-5" />
    </button>
  );
}
```

- [ ] **Step 7: Write `AppShell`**

```tsx
// apps/web/components/nav/AppShell.tsx
import Link from "next/link";
import type { ReactNode } from "react";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

const NAV_ITEMS = [
  { href: "/learning", label: "Learning Hub" },
  { href: "/patterns", label: "Pattern Explorer" },
  { href: "/interviews", label: "Interviews" },
];

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="flex items-center justify-between border-b px-6 py-4">
        <span className="font-semibold">DesignForge</span>
        <nav className="flex items-center gap-4">
          {NAV_ITEMS.map((item) => (
            <Link key={item.href} href={item.href} className="text-sm hover:underline">
              {item.label}
            </Link>
          ))}
          <ThemeToggle />
        </nav>
      </header>
      <main className="px-6 py-8">{children}</main>
    </div>
  );
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd apps/web && npm test`
Expected: PASS

- [ ] **Step 9: Wire `layout.tsx` and `page.tsx`**

```tsx
// apps/web/app/layout.tsx
import "./globals.css";
import type { ReactNode } from "react";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { AppShell } from "@/components/nav/AppShell";

export const metadata = {
  title: "DesignForge",
  description: "Master Low-Level Design (LLD) & High-Level Design (HLD) Interviews.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <ThemeProvider>
          <AppShell>{children}</AppShell>
        </ThemeProvider>
      </body>
    </html>
  );
}
```

```tsx
// apps/web/app/page.tsx
export default function HomePage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">Welcome to DesignForge</h1>
      <p className="text-muted-foreground">Master LLD & HLD interviews.</p>
    </div>
  );
}
```

- [ ] **Step 10: Verify the app builds and runs**

Run: `cd apps/web && npm run build`
Expected: build succeeds with no type errors.

- [ ] **Step 11: Commit**

```bash
git add apps/web
git commit -m "Scaffold Next.js app shell with theme toggle and primary navigation"
```

---

### Task 7: Shared types package + frontend auth pages

**Files:**
- Create: `packages/shared-types/package.json`
- Create: `packages/shared-types/src/auth.ts`
- Create: `packages/shared-types/src/index.ts`
- Create: `apps/web/lib/api/client.ts`
- Create: `apps/web/lib/api/auth.ts`
- Create: `apps/web/app/(auth)/login/page.tsx`
- Create: `apps/web/app/(auth)/register/page.tsx`
- Create: `apps/web/components/auth/LoginForm.tsx`
- Create: `apps/web/components/auth/RegisterForm.tsx`
- Test: `apps/web/components/auth/LoginForm.test.tsx`
- Test: `apps/web/components/auth/RegisterForm.test.tsx`

**Interfaces:**
- Consumes: backend `POST /api/auth/login` and `POST /api/auth/register` (Tasks 3–4) via `AuthResponse`/`UserResponse` shapes mirrored in `shared-types`.
- Produces: `login(email: string, password: string): Promise<AuthResponse>` and `register(fields: RegisterFields): Promise<UserResponse>` from `apps/web/lib/api/auth.ts` — the functions Task 8's E2E test drives indirectly through the rendered forms.

- [ ] **Step 1: Write `packages/shared-types`**

```json
// packages/shared-types/package.json
{
  "name": "@designforge/shared-types",
  "version": "0.1.0",
  "private": true,
  "main": "src/index.ts",
  "types": "src/index.ts"
}
```

```ts
// packages/shared-types/src/auth.ts
export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  locale: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserResponse;
}

export interface RegisterFields {
  email: string;
  password: string;
  displayName: string;
  locale: string;
}
```

```ts
// packages/shared-types/src/index.ts
export * from "./auth";
```

- [ ] **Step 2: Add the workspace dependency and API client**

Add to `apps/web/package.json` `dependencies`: `"@designforge/shared-types": "file:../../packages/shared-types"`, then run `npm install` from `apps/web`.

```ts
// apps/web/lib/api/client.ts
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(body.message ?? "Request failed");
  }
  return response.json();
}
```

```ts
// apps/web/lib/api/auth.ts
import type { AuthResponse, RegisterFields, UserResponse } from "@designforge/shared-types";
import { apiFetch } from "./client";

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function register(fields: RegisterFields): Promise<UserResponse> {
  return apiFetch<UserResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(fields),
  });
}
```

- [ ] **Step 3: Write the failing `LoginForm` test**

```tsx
// apps/web/components/auth/LoginForm.test.tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import * as authApi from "@/lib/api/auth";
import { LoginForm } from "./LoginForm";

describe("LoginForm", () => {
  it("submits email and password and calls onSuccess", async () => {
    const loginSpy = vi.spyOn(authApi, "login").mockResolvedValue({
      accessToken: "token",
      refreshToken: "refresh",
      user: { id: "1", email: "jane@example.com", displayName: "Jane", locale: "en" },
    });
    const onSuccess = vi.fn();

    render(<LoginForm onSuccess={onSuccess} />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "jane@example.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "password123" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));

    await waitFor(() => expect(loginSpy).toHaveBeenCalledWith("jane@example.com", "password123"));
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });

  it("shows an error message when login fails", async () => {
    vi.spyOn(authApi, "login").mockRejectedValue(new Error("Invalid email or password"));

    render(<LoginForm onSuccess={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "jane@example.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd apps/web && npm test`
Expected: FAIL — `./LoginForm` module not found.

- [ ] **Step 5: Write `LoginForm`**

```tsx
// apps/web/components/auth/LoginForm.tsx
"use client";

import { useState } from "react";
import { login } from "@/lib/api/auth";

export function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password);
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 max-w-sm">
      <label className="flex flex-col gap-1">
        <span>Email</span>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      <label className="flex flex-col gap-1">
        <span>Password</span>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button type="submit" className="rounded-md bg-primary px-4 py-2 text-primary-foreground">
        Log in
      </button>
    </form>
  );
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd apps/web && npm test`
Expected: PASS

- [ ] **Step 7: Write the failing `RegisterForm` test**

```tsx
// apps/web/components/auth/RegisterForm.test.tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import * as authApi from "@/lib/api/auth";
import { RegisterForm } from "./RegisterForm";

describe("RegisterForm", () => {
  it("submits registration fields and calls onSuccess", async () => {
    const registerSpy = vi.spyOn(authApi, "register").mockResolvedValue({
      id: "1",
      email: "jane@example.com",
      displayName: "Jane",
      locale: "en",
    });
    const onSuccess = vi.fn();

    render(<RegisterForm onSuccess={onSuccess} />);
    fireEvent.change(screen.getByLabelText(/display name/i), { target: { value: "Jane" } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "jane@example.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "password123" } });
    fireEvent.click(screen.getByRole("button", { name: /create account/i }));

    await waitFor(() =>
      expect(registerSpy).toHaveBeenCalledWith({
        email: "jane@example.com",
        password: "password123",
        displayName: "Jane",
        locale: "en",
      })
    );
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });
});
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd apps/web && npm test`
Expected: FAIL — `./RegisterForm` module not found.

- [ ] **Step 9: Write `RegisterForm`**

```tsx
// apps/web/components/auth/RegisterForm.tsx
"use client";

import { useState } from "react";
import { register } from "@/lib/api/auth";

export function RegisterForm({ onSuccess }: { onSuccess: () => void }) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await register({ displayName, email, password, locale: "en" });
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 max-w-sm">
      <label className="flex flex-col gap-1">
        <span>Display name</span>
        <input
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      <label className="flex flex-col gap-1">
        <span>Email</span>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      <label className="flex flex-col gap-1">
        <span>Password</span>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button type="submit" className="rounded-md bg-primary px-4 py-2 text-primary-foreground">
        Create account
      </button>
    </form>
  );
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd apps/web && npm test`
Expected: PASS

- [ ] **Step 11: Wire the pages**

```tsx
// apps/web/app/(auth)/login/page.tsx
"use client";

import { useRouter } from "next/navigation";
import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  const router = useRouter();
  return (
    <div>
      <h1 className="text-xl font-semibold mb-4">Log in</h1>
      <LoginForm onSuccess={() => router.push("/")} />
    </div>
  );
}
```

```tsx
// apps/web/app/(auth)/register/page.tsx
"use client";

import { useRouter } from "next/navigation";
import { RegisterForm } from "@/components/auth/RegisterForm";

export default function RegisterPage() {
  const router = useRouter();
  return (
    <div>
      <h1 className="text-xl font-semibold mb-4">Create your account</h1>
      <RegisterForm onSuccess={() => router.push("/login")} />
    </div>
  );
}
```

- [ ] **Step 12: Commit**

```bash
git add packages/shared-types apps/web
git commit -m "Add shared-types package and frontend login/register pages"
```

---

### Task 8: End-to-end smoke test (Playwright)

**Files:**
- Create: `apps/web/playwright.config.ts`
- Create: `apps/web/e2e/auth-flow.spec.ts`
- Modify: `README.md` (add E2E run instructions)

**Interfaces:**
- Consumes: the full stack from Tasks 1–7 running together (Postgres, Redis, `apps/api` on `:8080`, `apps/web` on `:3000`).
- Produces: a passing E2E baseline that later plans extend with new specs (e.g., `interview-flow.spec.ts`) rather than replacing.

- [ ] **Step 1: Write `playwright.config.ts`**

```ts
// apps/web/playwright.config.ts
import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  use: {
    baseURL: "http://localhost:3000",
  },
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
```

Add to `apps/web/package.json` scripts: `"test:e2e": "playwright test"`.

- [ ] **Step 2: Write the failing E2E spec**

```ts
// apps/web/e2e/auth-flow.spec.ts
import { test, expect } from "@playwright/test";

test("a new user can register, log in, and see the app shell", async ({ page }) => {
  const uniqueEmail = `e2e-${Date.now()}@example.com`;

  await page.goto("/register");
  await page.getByLabel(/display name/i).fill("E2E Tester");
  await page.getByLabel(/email/i).fill(uniqueEmail);
  await page.getByLabel(/password/i).fill("password123");
  await page.getByRole("button", { name: /create account/i }).click();

  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel(/email/i).fill(uniqueEmail);
  await page.getByLabel(/password/i).fill("password123");
  await page.getByRole("button", { name: /log in/i }).click();

  await expect(page).toHaveURL("http://localhost:3000/");
  await expect(page.getByRole("link", { name: /learning hub/i })).toBeVisible();
  await expect(page.getByRole("link", { name: /pattern explorer/i })).toBeVisible();
  await expect(page.getByRole("link", { name: /interviews/i })).toBeVisible();
});
```

- [ ] **Step 3: Install Playwright browsers**

Run: `cd apps/web && npx playwright install --with-deps chromium`

- [ ] **Step 4: Run the E2E test with the real backend and infra up**

Run:
```bash
docker compose -f infra/docker-compose.yml up -d
cd apps/api && ./mvnw -q spring-boot:run &
cd apps/web && npm run test:e2e
```
Expected: PASS — 1 test passed. Then stop the backend process started above.

- [ ] **Step 5: Document how to run it**

Add to `README.md` under a new `## Testing` section:

```markdown
## Testing

- Backend unit tests: `cd apps/api && ./mvnw test`
- Frontend unit tests: `cd apps/web && npm test`
- End-to-end: bring up `infra/docker-compose.yml` and `apps/api`, then `cd apps/web && npm run test:e2e`
```

- [ ] **Step 6: Commit**

```bash
git add apps/web/playwright.config.ts apps/web/e2e apps/web/package.json README.md
git commit -m "Add Playwright end-to-end smoke test for register/login flow"
```

---

## Plan Self-Review Notes

- **Spec coverage:** This plan covers only the Foundation slice of the Phase 1 spec (infra provisioning, auth, app shell) — Learning Hub, Pattern Explorer, AI pipeline, and both simulators are intentionally out of scope here and will each get their own plan, per the writing-plans scope check (this spec covers multiple independent subsystems).
- **Kafka/Elasticsearch/MinIO:** provisioned in Task 1 but not wired into any code path yet — matches the spec's explicit statement that they're configured now and get real consumers in later plans.
- **Type consistency verified:** `JwtService.parseUserId` (Task 4) is the exact name `JwtAuthFilter` (Task 5) calls; `RefreshTokenStore.revoke` (Task 4) is the exact name `AuthService.logout` (Task 5) calls; `AuthService` constructor signature is updated consistently across Tasks 3–4 (Task 4's step 6 shows the full 4-arg constructor replacing Task 3's 2-arg one).
- **Next plans to write after this one ships:** (1) Design Pattern Explorer + Elasticsearch indexing, (2) Learning Hub + Interactive Learning, (3) AI pipeline (`ai-gateway` module) behind a provider interface with an Ollama implementation, (4) LLD Interview Simulator (WebSocket + Kafka `interview.completed` event), (5) HLD Interview Simulator, (6) multilingual/i18n layer, (7) deferred-module route stubs.
