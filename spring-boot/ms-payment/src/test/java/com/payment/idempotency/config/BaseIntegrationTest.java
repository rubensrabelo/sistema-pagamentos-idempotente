package com.payment.idempotency.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres;

    @ServiceConnection
    protected static final RedisContainer redisContainer;

    static {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine");
        postgres.start();

        redisContainer = new RedisContainer(DockerImageName.parse("redis:8.10-alpine"));
        redisContainer.start();
    }
}
