package com.mycompany.myapp.grpc;

import com.mycompany.myapp.AgathaApp;

import com.google.protobuf.Empty;
import io.github.jhipster.config.JHipsterProperties;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgathaApp.class)
public class ProfileInfoServiceIntTest {

    @Mock
    private JHipsterProperties jHipsterProperties;

    @Mock
    private org.springframework.core.env.Environment environment;

    private Server mockServer;

    private ProfileInfoServiceGrpc.ProfileInfoServiceBlockingStub stub;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        String mockProfile[] = {"other", "test"};
        JHipsterProperties.Ribbon ribbon = new JHipsterProperties.Ribbon();
        ribbon.setDisplayOnActiveProfiles(mockProfile);
        when(jHipsterProperties.getRibbon()).thenReturn(ribbon);

        String activeProfiles[] = {"test"};
        when(environment.getDefaultProfiles()).thenReturn(activeProfiles);
        when(environment.getActiveProfiles()).thenReturn(activeProfiles);
        ProfileInfoService service = new ProfileInfoService(jHipsterProperties, environment);
        String uniqueServerName = "Mock server for " + ProfileInfoService.class;
        mockServer = InProcessServerBuilder
            .forName(uniqueServerName).directExecutor().addService(service).build().start();
        InProcessChannelBuilder channelBuilder =
            InProcessChannelBuilder.forName(uniqueServerName).directExecutor();
        stub = ProfileInfoServiceGrpc.newBlockingStub(channelBuilder.build());
    }

    @After
    public void tearDown() {
        mockServer.shutdownNow();
    }

    @Test
    public void getProfileInfoWithRibbon() throws Exception {
        ProfileInfo profileInfo = stub.getActiveProfiles(Empty.newBuilder().build());
        assertThat(profileInfo.getActiveProfilesList()).containsExactly("test");
        assertThat(profileInfo.getRibbonEnv()).isEqualTo("test");
    }

    @Test
    public void getProfileInfoWithoutRibbon() throws Exception {
        JHipsterProperties.Ribbon ribbon = new JHipsterProperties.Ribbon();
        ribbon.setDisplayOnActiveProfiles(null);
        when(jHipsterProperties.getRibbon()).thenReturn(ribbon);

        ProfileInfo profileInfo = stub.getActiveProfiles(Empty.newBuilder().build());
        assertThat(profileInfo.getActiveProfilesList()).containsExactly("test");
        assertThat(profileInfo.getRibbonEnv()).isEmpty();
    }

    @Test
    public void getProfileInfoWithoutActiveProfiles() throws Exception {
        String emptyProfile[] = {};
        when(environment.getDefaultProfiles()).thenReturn(emptyProfile);
        when(environment.getActiveProfiles()).thenReturn(emptyProfile);

        ProfileInfo profileInfo = stub.getActiveProfiles(Empty.newBuilder().build());
        assertThat(profileInfo.getActiveProfilesList()).isEmpty();
        assertThat(profileInfo.getRibbonEnv()).isEmpty();
    }
}
