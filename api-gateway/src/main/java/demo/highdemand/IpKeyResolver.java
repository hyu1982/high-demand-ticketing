package demo.highdemand;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component("ipKeyResolver")
public class IpKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(
            org.springframework.web.server.ServerWebExchange exchange) {

        var remoteAddress = exchange.getRequest().getRemoteAddress();

        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return Mono.just("unknown");
        }

        return Mono.just(
                remoteAddress.getAddress().getHostAddress()
        );
    }
}