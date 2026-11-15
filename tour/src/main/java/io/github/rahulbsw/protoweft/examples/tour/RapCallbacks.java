package io.github.rahulbsw.protoweft.examples.tour;

import io.github.rahulbsw.protoweft.rap.RapCallbackEndpoint;
import io.github.rahulbsw.protoweft.server.ServletEndpoint;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * RAP answers through HTTP callbacks: the tool server POSTs results and subscription events back
 * to the caller. The tour's RAP client receives them on this application at
 * {@code /rap/callback/*}, so they show up in the wire panel next to the calls.
 */
@Configuration(proxyBeanMethods = false)
public class RapCallbacks {

    @Bean
    public RapCallbackEndpoint rapCallbackEndpoint(Environment environment) {
        return RapCallbackEndpoint.create(() -> "http://localhost:" + environment.getProperty("local.server.port"));
    }

    @Bean
    public ServletRegistrationBean<?> rapCallbackServlet(RapCallbackEndpoint endpoint) {
        ServletEndpoint callbacks = endpoint.endpoints().get(0);
        ServletRegistrationBean<?> bean = new ServletRegistrationBean<>(callbacks.servlet(),
                callbacks.urlPatterns().toArray(String[]::new));
        bean.setName("rap-callbacks");
        bean.setAsyncSupported(callbacks.asyncSupported());
        return bean;
    }
}
