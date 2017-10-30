package com.mycompany.myapp.grpc;

import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.security.jwt.TokenProvider;
import io.grpc.*;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthenticationInterceptor implements ServerInterceptor {

    private final Logger log = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    private final TokenProvider tokenProvider;

    public AuthenticationInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String authorizationValue = metadata.get(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER));
        if(authorizationValue == null) {
            // Some implementations don't support uppercased metadata keys
            authorizationValue = metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
        }
        if (StringUtils.hasText(authorizationValue) && authorizationValue.startsWith("Bearer ")) {
            String token = authorizationValue.substring(7, authorizationValue.length());
            if (StringUtils.hasText(token)) {
                if (this.tokenProvider.validateToken(token)) {
                    Authentication authentication = this.tokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    if (authentication.getAuthorities().stream()
                        .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(AuthoritiesConstants.ANONYMOUS))
                        ) {
                        log.error("Anonymous user permission denied");
                        serverCall.close(Status.PERMISSION_DENIED, metadata);
                    }
                    return serverCallHandler.startCall(serverCall, metadata);
                }
            }
        }
        log.error("Missing basic authorization metadata");
        serverCall.close(Status.UNAUTHENTICATED, metadata);
        return serverCallHandler.startCall(serverCall, metadata);
    }
}
