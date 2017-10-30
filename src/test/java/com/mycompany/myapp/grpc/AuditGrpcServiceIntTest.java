package com.mycompany.myapp.grpc;

import com.mycompany.myapp.AgathaApp;
import com.mycompany.myapp.config.audit.AuditEventConverter;
import com.mycompany.myapp.domain.PersistentAuditEvent;
import com.mycompany.myapp.repository.PersistenceAuditEventRepository;
import com.mycompany.myapp.service.AuditEventService;

import com.google.protobuf.Int64Value;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgathaApp.class)
@Transactional
public class AuditGrpcServiceIntTest {

    private static final String SAMPLE_PRINCIPAL = "SAMPLE_PRINCIPAL";
    private static final String SAMPLE_TYPE = "SAMPLE_TYPE";
    private static final Instant SAMPLE_TIMESTAMP = Instant.parse("2015-08-04T10:11:30Z");

    @Autowired
    private PersistenceAuditEventRepository auditEventRepository;

    @Autowired
    private AuditEventConverter auditEventConverter;

    private PersistentAuditEvent auditEvent;

    private Server mockServer;

    private AuditServiceGrpc.AuditServiceBlockingStub stub;

    @Before
    public void setUp() throws IOException {
        AuditEventService auditEventService =
            new AuditEventService(auditEventRepository, auditEventConverter);
        AuditGrpcService service = new AuditGrpcService(auditEventService);
        String uniqueServerName = "Mock server for " + AuditGrpcService.class;
        mockServer = InProcessServerBuilder
            .forName(uniqueServerName).directExecutor().addService(service).build().start();
        InProcessChannelBuilder channelBuilder =
            InProcessChannelBuilder.forName(uniqueServerName).directExecutor();
        stub = AuditServiceGrpc.newBlockingStub(channelBuilder.build());
    }

    @After
    public void tearDown() {
        mockServer.shutdownNow();
    }

    @Before
    public void initTest() {
        auditEventRepository.deleteAll();
        auditEvent = new PersistentAuditEvent();
        auditEvent.setAuditEventType(SAMPLE_TYPE);
        auditEvent.setPrincipal(SAMPLE_PRINCIPAL);
        auditEvent.setAuditEventDate(SAMPLE_TIMESTAMP);
    }

    @Test
    public void getAllAudits() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        assertThat(stub.getAuditEvents(AuditRequest.newBuilder().build())).extracting("principal").contains(SAMPLE_PRINCIPAL);
    }

    @Test
    public void getAudit() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        // Get the audit
        AuditEvent event = stub.getAuditEvent(Int64Value.newBuilder().setValue(auditEvent.getId()).build());
        assertThat(event.getPrincipal()).isEqualTo(SAMPLE_PRINCIPAL);
    }

    @Test
    public void getAuditsByDate() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        AuditRequest request = AuditRequest.newBuilder()
            .setFromDate(Date.newBuilder().setYear(2015).setMonth(8).setDay(1))
            .setToDate(Date.newBuilder().setYear(2015).setMonth(8).setDay(20))
            .build();
        assertThat(stub.getAuditEvents(request)).extracting("principal").contains(SAMPLE_PRINCIPAL);
    }

    @Test
    public void getNonExistingAuditsByDate() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        // Query audits but expect no results
        AuditRequest request = AuditRequest.newBuilder()
            .setFromDate(Date.newBuilder().setYear(2015).setMonth(9).setDay(1))
            .setToDate(Date.newBuilder().setYear(2015).setMonth(9).setDay(20))
            .build();
        assertThat(stub.getAuditEvents(request)).isEmpty();
    }

    @Test
    public void getNonExistingAudit() throws Exception {
        // Get the audit
        try {
            stub.getAuditEvent(Int64Value.newBuilder().setValue(Long.MAX_VALUE).build());
            failBecauseExceptionWasNotThrown(StatusException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        }
    }
}
