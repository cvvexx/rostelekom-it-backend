package io.cvvexxx.services.config;


import io.cvvexxx.services.client.ServiceTokenInterceptor;
import io.cvvexxx.services.client.BillingServiceRestClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public BillingServiceRestClientImpl billingServiceRestClient(
            @Value("${rostelekom.uri.billing-service}") String billingServiceUrl,
            ServiceTokenInterceptor serviceTokenInterceptor,
            RestClient.Builder builder//бин от спринг бута, чтобы он подтянул пропети из spring.http.clients*
    ) {
        return new BillingServiceRestClientImpl(
            builder
                    .baseUrl(billingServiceUrl)
                    .requestInterceptor(serviceTokenInterceptor)
                    .build()
        );
    }

    
}
