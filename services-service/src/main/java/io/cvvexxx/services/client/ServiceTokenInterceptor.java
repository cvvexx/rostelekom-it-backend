package io.cvvexxx.services.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

//Добавляет сервисный токен от user-service в межсервисные запросы (billing и т.п.).
//Токен держим в памяти и берём новый за REFRESH_BEFORE до истечения, refresh-токена у сервисного нет
@Component
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    //Запас, чтобы токен не истёк, пока запрос идёт по сети
    private static final Duration REFRESH_BEFORE = Duration.ofSeconds(30);

    private final RestClient userServiceClient;
    private final ServiceTokenRequest credentials;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile CachedToken token;

    public ServiceTokenInterceptor(RestClient.Builder builder,
                                   @Value("${rostelekom.uri.user-service}") String userServiceUri,
                                   @Value("${app.service-auth.client-id}") String clientId,
                                   @Value("${app.service-auth.client-secret}") String clientSecret) {
        this.userServiceClient = builder.baseUrl(userServiceUri).build();
        this.credentials = new ServiceTokenRequest(clientId, clientSecret);
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution
    ) throws IOException {
        request.getHeaders().setBearerAuth(token());
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            token = null;
        }
        return response;
    }

    private String token() {
        CachedToken current = token;
        if (current != null && Instant.now().isBefore(current.refreshAt())) {
            return current.value();
        }
        lock.lock();
        try {
            //Пока ждали lock, токен мог обновить другой поток
            current = token;
            if (current != null && Instant.now().isBefore(current.refreshAt())) {
                return current.value();
            }
            ServiceTokenResponse response = userServiceClient
                    .post()
                    .uri("/internal/auth/service-token")
                    .body(credentials)
                    .retrieve()
                    .body(ServiceTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("user-service вернул пустой сервисный токен");
            }
            Instant refreshAt = Instant.now().plusSeconds(response.expiresInSeconds()).minus(REFRESH_BEFORE);
            token = new CachedToken(response.accessToken(), refreshAt);
            return response.accessToken();
        } finally {
            lock.unlock();
        }
    }

    private record CachedToken(String value, Instant refreshAt) {
    }

    private record ServiceTokenRequest(String clientId, String clientSecret) {
    }

    private record ServiceTokenResponse(String accessToken, long expiresInSeconds) {
    }
}
