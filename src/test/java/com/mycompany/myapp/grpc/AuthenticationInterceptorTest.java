
package com.mycompany.myapp.grpc;

import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.security.jwt.TokenProvider;

import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.*;

/**
 * Test class for the AuthenticationInterceptor gRPC interceptor class.
 *
 * @see AuthenticationInterceptor
 */
public class AuthenticationInterceptorTest {

    @Mock
    private TokenProvider tokenProvider;

    private Server fakeServer;

    private ManagedChannel inProcessChannel;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(true).when(tokenProvider).validateToken(anyString());
        doReturn(new UsernamePasswordAuthenticationToken("user", "user",
            Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
        ).when(tokenProvider).getAuthentication(anyString());

        String uniqueServerName = "fake server for " + getClass();
        fakeServer = InProcessServerBuilder.forName(uniqueServerName)
            .addService(ServerInterceptors.intercept(new LoggersServiceGrpc.LoggersServiceImplBase() {}, new AuthenticationInterceptor(tokenProvider)))
            .directExecutor()
            .build()
            .start();
        inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName)
            .directExecutor()
            .build();
    }

    @After
    public void tearDown() {
        inProcessChannel.shutdownNow();
        fakeServer.shutdownNow();
    }

    @Test
    public void testIntercept() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer dXNlcjp1c2Vy");
        LoggersServiceGrpc.LoggersServiceBlockingStub stub = MetadataUtils.attachHeaders(LoggersServiceGrpc.newBlockingStub(inProcessChannel), metadata);
        assertGetLoggersReturnsCode(stub, Status.Code.UNIMPLEMENTED);
        verify(tokenProvider).getAuthentication("dXNlcjp1c2Vy");
        verify(tokenProvider).validateToken("dXNlcjp1c2Vy");
    }

    @Test
    public void testCapitalizedAuthorizationHeader() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer dXNlcjp1c2Vy");
        LoggersServiceGrpc.LoggersServiceBlockingStub stub = MetadataUtils.attachHeaders(LoggersServiceGrpc.newBlockingStub(inProcessChannel), metadata);
        assertGetLoggersReturnsCode(stub, Status.Code.UNIMPLEMENTED);
        verify(tokenProvider).getAuthentication("dXNlcjp1c2Vy");
        verify(tokenProvider).validateToken("dXNlcjp1c2Vy");
    }

    @Test
    public void testNoAuthorization() {
        LoggersServiceGrpc.LoggersServiceBlockingStub stub = LoggersServiceGrpc.newBlockingStub(inProcessChannel);
        assertGetLoggersReturnsCode(stub, Status.Code.UNAUTHENTICATED);
    }

    @Test
    public void testAnonymousUserDenied() {
        doReturn(new UsernamePasswordAuthenticationToken("user", "user",
            Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.ANONYMOUS)))
        ).when(tokenProvider).getAuthentication(anyString());
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer dXNlcjp1c2Vy");
        LoggersServiceGrpc.LoggersServiceBlockingStub stub = MetadataUtils.attachHeaders(LoggersServiceGrpc.newBlockingStub(inProcessChannel), metadata);
        assertGetLoggersReturnsCode(stub, Status.Code.PERMISSION_DENIED);
    }

    @Test
    public void testMissingScheme() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "dXNlcjp1c2Vy");
        LoggersServiceGrpc.LoggersServiceBlockingStub stub = MetadataUtils.attachHeaders(LoggersServiceGrpc.newBlockingStub(inProcessChannel), metadata);
        assertGetLoggersReturnsCode(stub, Status.Code.UNAUTHENTICATED);
    }

    @Test
    public void testInvalidToken() {
        doReturn(false).when(tokenProvider).validateToken(anyString());

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer user_token");
        LoggersServiceGrpc.LoggersServiceBlockingStub stub = MetadataUtils.attachHeaders(LoggersServiceGrpc.newBlockingStub(inProcessChannel), metadata);
        assertGetLoggersReturnsCode(stub, Status.Code.UNAUTHENTICATED);
    }

    private static void assertGetLoggersReturnsCode(LoggersServiceGrpc.LoggersServiceBlockingStub stub, Status.Code code) {
        try {
            stub.getLoggers(Empty.getDefaultInstance()).forEachRemaining(l -> {});
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(code);
        }
    }

}
