package io.cvvexxx.services.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class ServiceTokenInterceptorTest {

    @Test
    @DisplayName("токен берётся один раз и переиспользуется, после 401 запрашивается новый")
    void cachesTokenAndRefreshesAfterUnauthorized() {
        RestClient.Builder authBuilder = RestClient.builder();
        MockRestServiceServer userService = MockRestServiceServer.bindTo(authBuilder).build();
        ServiceTokenInterceptor interceptor =
                new ServiceTokenInterceptor(authBuilder, "http://user-service", "services-service", "secret");

        RestClient.Builder billingBuilder = RestClient.builder().requestInterceptor(interceptor);
        MockRestServiceServer billing = MockRestServiceServer.bindTo(billingBuilder).build();
        RestClient client = billingBuilder.build();

        expectToken(userService, "t1");
        expectToken(userService, "t2");

        billing.expect(header("Authorization", "Bearer t1")).andRespond(withSuccess());
        billing.expect(header("Authorization", "Bearer t1")).andRespond(withSuccess());
        billing.expect(header("Authorization", "Bearer t1")).andRespond(withUnauthorizedRequest());
        billing.expect(header("Authorization", "Bearer t2")).andRespond(withSuccess());

        call(client);
        call(client);
        assertThatThrownBy(() -> call(client)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        call(client);

        userService.verify();
        billing.verify();
    }

    private static void expectToken(MockRestServiceServer userService, String token) {
        userService.expect(requestTo("http://user-service/internal/auth/service-token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"accessToken": "%s", "expiresInSeconds": 3600}
                        """.formatted(token), MediaType.APPLICATION_JSON));
    }

    private static void call(RestClient client) {
        client.get().uri("http://billing/any").retrieve().toBodilessEntity();
    }
}
