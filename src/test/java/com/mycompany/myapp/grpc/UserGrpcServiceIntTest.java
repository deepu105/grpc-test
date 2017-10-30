package com.mycompany.myapp.grpc;

import com.mycompany.myapp.AgathaApp;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.service.MailService;
import com.mycompany.myapp.service.UserService;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.util.*;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

/**
 * Test class for the UserGrpcService gRPC endpoint.
 *
 * @see UserGrpcService
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgathaApp.class)
public class UserGrpcServiceIntTest {

    private static final String DEFAULT_LOGIN = "johndoe";
    private static final String UPDATED_LOGIN = "jhipster";

    private static final String DEFAULT_PASSWORD = "passjohndoe";
    private static final String UPDATED_PASSWORD = "passjhipster";

    private static final String DEFAULT_EMAIL = "johndoe@localhost";
    private static final String UPDATED_EMAIL = "jhipster@localhost";

    private static final String DEFAULT_FIRSTNAME = "john";
    private static final String UPDATED_FIRSTNAME = "jhipsterFirstName";

    private static final String DEFAULT_LASTNAME = "doe";
    private static final String UPDATED_LASTNAME = "jhipsterLastName";

    private static final String DEFAULT_IMAGEURL = "http://placehold.it/50x50";
    private static final String UPDATED_IMAGEURL = "http://placehold.it/40x40";

    private static final String DEFAULT_LANGKEY = "en";
    private static final String UPDATED_LANGKEY = "fr";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserProtoMapper userProtoMapper;

    private Server mockServer;

    private UserServiceGrpc.UserServiceBlockingStub stub;

    private User user;

    @Before
    public void setUp() throws IOException {
        UserGrpcService userGrpcService = new UserGrpcService(userRepository, mailService, userService, userProtoMapper);
        String uniqueServerName = "Mock server for " + UserGrpcService.class;
        mockServer = InProcessServerBuilder
            .forName(uniqueServerName).directExecutor().addService(userGrpcService).build().start();
        InProcessChannelBuilder channelBuilder =
            InProcessChannelBuilder.forName(uniqueServerName).directExecutor();
        stub = UserServiceGrpc.newBlockingStub(channelBuilder.build());
    }

    @After
    public void tearDown() {
        mockServer.shutdownNow();
    }

    /**
     * Create a User.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which has a required relationship to the User entity.
     */
    public static User createEntity(EntityManager em) {
        User user = new User();
        user.setLogin(DEFAULT_LOGIN);
        user.setPassword(RandomStringUtils.random(60));
        user.setActivated(true);
        user.setEmail(DEFAULT_EMAIL);
        user.setFirstName(DEFAULT_FIRSTNAME);
        user.setLastName(DEFAULT_LASTNAME);
        user.setImageUrl(DEFAULT_IMAGEURL);
        user.setLangKey(DEFAULT_LANGKEY);
        return user;
    }

    @Before
    public void initTest() {
        user = createEntity(null);
    }

    @Test
    @Transactional
    public void createUser() throws Exception {
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        // Create the User
        UserProto userProto = UserProto.newBuilder()
            .setLogin(DEFAULT_LOGIN)
            .setPassword(DEFAULT_PASSWORD)
            .setFirstName(DEFAULT_FIRSTNAME)
            .setLastName(DEFAULT_LASTNAME)
            .setEmail(DEFAULT_EMAIL)
            .setActivated(true)
            .setImageUrl(DEFAULT_IMAGEURL)
            .setLangKey(DEFAULT_LANGKEY)
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        stub.createUser(userProto);

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate + 1);
        User testUser = userList.get(userList.size() - 1);
        assertThat(testUser.getLogin()).isEqualTo(DEFAULT_LOGIN);
        assertThat(testUser.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
        assertThat(testUser.getLastName()).isEqualTo(DEFAULT_LASTNAME);
        assertThat(testUser.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(testUser.getImageUrl()).isEqualTo(DEFAULT_IMAGEURL);
        assertThat(testUser.getLangKey()).isEqualTo(DEFAULT_LANGKEY);
    }

    @Test
    @Transactional
    public void createUserWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        UserProto userProto = UserProto.newBuilder()
            .setId(1L)
            .setLogin(DEFAULT_LOGIN)
            .setPassword(DEFAULT_PASSWORD)
            .setFirstName(DEFAULT_FIRSTNAME)
            .setLastName(DEFAULT_LASTNAME)
            .setEmail(DEFAULT_EMAIL)
            .setActivated(true)
            .setImageUrl(DEFAULT_IMAGEURL)
            .setLangKey(DEFAULT_LANGKEY)
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        try {
            stub.createUser(userProto);
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            // Validate the User in the database
            List<User> userList = userRepository.findAll();
            assertThat(userList).hasSize(databaseSizeBeforeCreate);
        }

    }

    @Test
    @Transactional
    public void createUserWithExistingLogin() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        UserProto userProto = UserProto.newBuilder()
            .setLogin(DEFAULT_LOGIN)
            .setPassword(DEFAULT_PASSWORD)
            .setFirstName(DEFAULT_FIRSTNAME)
            .setLastName(DEFAULT_LASTNAME)
            .setEmail("anothermail@localhost")
            .setActivated(true)
            .setImageUrl(DEFAULT_IMAGEURL)
            .setLangKey(DEFAULT_LANGKEY)
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        try {
            // Create the User
            stub.createUser(userProto);
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
            // Validate the User in the database
            List<User> userList = userRepository.findAll();
            assertThat(userList).hasSize(databaseSizeBeforeCreate);
        }

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void createUserWithExistingEmail() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        UserProto userProto = UserProto.newBuilder()
            .setLogin("anotherlogin")
            .setPassword(DEFAULT_PASSWORD)
            .setFirstName(DEFAULT_FIRSTNAME)
            .setLastName(DEFAULT_LASTNAME)
            .setEmail(DEFAULT_EMAIL)
            .setActivated(true)
            .setImageUrl(DEFAULT_IMAGEURL)
            .setLangKey(DEFAULT_LANGKEY)
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        try {
            // Create the User
            stub.createUser(userProto);
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
            // Validate the User in the database
            List<User> userList = userRepository.findAll();
            assertThat(userList).hasSize(databaseSizeBeforeCreate);
        }

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllUsers() throws Exception {
        // Initialize the database
        User savedUser = userRepository.saveAndFlush(user);

        // Get all the users
        PageRequest pageRequest = PageRequest.newBuilder()
            .addOrders(Order.newBuilder().setProperty("id").setDirection(Direction.DESC))
            .build();
        Optional<UserProto> maybeUser = StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(stub.getAllUsers(pageRequest), Spliterator.ORDERED),
            false)
            .filter(userProto -> savedUser.getId().equals(userProto.getId()))
            .findAny();

        assertThat(maybeUser).isPresent();
        UserProto foundUser = maybeUser.orElse(null);
        assertThat(foundUser.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
        assertThat(foundUser.getLastName()).isEqualTo(DEFAULT_LASTNAME);
        assertThat(foundUser.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(foundUser.getImageUrl()).isEqualTo(DEFAULT_IMAGEURL);
        assertThat(foundUser.getLangKey()).isEqualTo(DEFAULT_LANGKEY);
    }

    @Test
    @Transactional
    public void getUser() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);

        UserProto userProto = stub.getUser(StringValue.newBuilder().setValue(user.getLogin()).build());

        assertThat(userProto.getLogin()).isEqualTo(DEFAULT_LOGIN);
        assertThat(userProto.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
        assertThat(userProto.getLastName()).isEqualTo(DEFAULT_LASTNAME);
        assertThat(userProto.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(userProto.getImageUrl()).isEqualTo(DEFAULT_IMAGEURL);
        assertThat(userProto.getLangKey()).isEqualTo(DEFAULT_LANGKEY);
    }

    @Test
    @Transactional
    public void getNonExistingUser() throws Exception {
        try {
            stub.getUser(StringValue.newBuilder().setValue("unknown").build());
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    @Test
    @Transactional
    public void updateUser() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeUpdate = userRepository.findAll().size();

        // Update the user
        User updatedUser = userRepository.findOne(user.getId());

        UserProto userProto = UserProto.newBuilder()
            .setId(updatedUser.getId())
            .setLogin(updatedUser.getLogin())
            .setPassword(UPDATED_PASSWORD)
            .setFirstName(UPDATED_FIRSTNAME)
            .setLastName(UPDATED_LASTNAME)
            .setEmail(UPDATED_EMAIL)
            .setActivated(updatedUser.getActivated())
            .setImageUrl(UPDATED_IMAGEURL)
            .setLangKey(UPDATED_LANGKEY)
            .setCreatedBy(updatedUser.getCreatedBy())
            .setCreatedDate(ProtobufMappers.instantToTimestamp(updatedUser.getCreatedDate()))
            .setLastModifiedBy(updatedUser.getLastModifiedBy())
            .setLastModifiedDate(ProtobufMappers.instantToTimestamp(updatedUser.getLastModifiedDate()))
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        stub.updateUser(userProto);

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeUpdate);
        User testUser = userList.get(userList.size() - 1);
        assertThat(testUser.getFirstName()).isEqualTo(UPDATED_FIRSTNAME);
        assertThat(testUser.getLastName()).isEqualTo(UPDATED_LASTNAME);
        assertThat(testUser.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testUser.getImageUrl()).isEqualTo(UPDATED_IMAGEURL);
        assertThat(testUser.getLangKey()).isEqualTo(UPDATED_LANGKEY);
    }

    @Test
    @Transactional
    public void updateUserLogin() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeUpdate = userRepository.findAll().size();

        // Update the user
        User updatedUser = userRepository.findOne(user.getId());

        UserProto userProto = UserProto.newBuilder()
            .setId(updatedUser.getId())
            .setLogin(UPDATED_LOGIN)
            .setPassword(UPDATED_PASSWORD)
            .setFirstName(UPDATED_FIRSTNAME)
            .setLastName(UPDATED_LASTNAME)
            .setEmail(UPDATED_EMAIL)
            .setActivated(updatedUser.getActivated())
            .setImageUrl(UPDATED_IMAGEURL)
            .setLangKey(UPDATED_LANGKEY)
            .setCreatedBy(updatedUser.getCreatedBy())
            .setCreatedDate(ProtobufMappers.instantToTimestamp(updatedUser.getCreatedDate()))
            .setLastModifiedBy(updatedUser.getLastModifiedBy())
            .setLastModifiedDate(ProtobufMappers.instantToTimestamp(updatedUser.getLastModifiedDate()))
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        stub.updateUser(userProto);

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeUpdate);
        User testUser = userList.get(userList.size() - 1);
        assertThat(testUser.getLogin()).isEqualTo(UPDATED_LOGIN);
        assertThat(testUser.getFirstName()).isEqualTo(UPDATED_FIRSTNAME);
        assertThat(testUser.getLastName()).isEqualTo(UPDATED_LASTNAME);
        assertThat(testUser.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testUser.getImageUrl()).isEqualTo(UPDATED_IMAGEURL);
        assertThat(testUser.getLangKey()).isEqualTo(UPDATED_LANGKEY);
    }

    @Test
    @Transactional
    public void updateUserExistingEmail() throws Exception {
        // Initialize the database with 2 users
        userRepository.saveAndFlush(user);

        User anotherUser = new User();
        anotherUser.setLogin("jhipster");
        anotherUser.setPassword(RandomStringUtils.random(60));
        anotherUser.setActivated(true);
        anotherUser.setEmail("jhipster@localhost");
        anotherUser.setFirstName("java");
        anotherUser.setLastName("hipster");
        anotherUser.setImageUrl("");
        anotherUser.setLangKey("en");
        userRepository.saveAndFlush(anotherUser);

        // Update the user
        User updatedUser = userRepository.findOne(user.getId());

        UserProto userProto = UserProto.newBuilder()
            .setId(updatedUser.getId())
            .setLogin(updatedUser.getLogin())
            .setPassword(updatedUser.getPassword())
            .setFirstName(updatedUser.getFirstName())
            .setLastName(updatedUser.getLastName())
            .setEmail("jhipster@localhost")  // this email should already be used by anotherUser
            .setActivated(updatedUser.getActivated())
            .setImageUrl(updatedUser.getImageUrl())
            .setLangKey(updatedUser.getLangKey())
            .setCreatedBy(updatedUser.getCreatedBy())
            .setCreatedDate(ProtobufMappers.instantToTimestamp(updatedUser.getCreatedDate()))
            .setLastModifiedBy(updatedUser.getLastModifiedBy())
            .setLastModifiedDate(ProtobufMappers.instantToTimestamp(updatedUser.getLastModifiedDate()))
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        try {
            // Create the User
            stub.updateUser(userProto);
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        }
    }

    @Test
    @Transactional
    public void updateUserExistingLogin() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);

        User anotherUser = new User();
        anotherUser.setLogin("jhipster");
        anotherUser.setPassword(RandomStringUtils.random(60));
        anotherUser.setActivated(true);
        anotherUser.setEmail("jhipster@localhost");
        anotherUser.setFirstName("java");
        anotherUser.setLastName("hipster");
        anotherUser.setImageUrl("");
        anotherUser.setLangKey("en");
        userRepository.saveAndFlush(anotherUser);

        // Update the user
        User updatedUser = userRepository.findOne(user.getId());

        UserProto userProto = UserProto.newBuilder()
            .setId(updatedUser.getId())
            .setLogin("jhipster") // this login should already be used by anotherUser
            .setPassword(updatedUser.getPassword())
            .setFirstName(updatedUser.getFirstName())
            .setLastName(updatedUser.getLastName())
            .setEmail(updatedUser.getEmail())
            .setActivated(updatedUser.getActivated())
            .setImageUrl(updatedUser.getImageUrl())
            .setLangKey(updatedUser.getLangKey())
            .setCreatedBy(updatedUser.getCreatedBy())
            .setCreatedDate(ProtobufMappers.instantToTimestamp(updatedUser.getCreatedDate()))
            .setLastModifiedBy(updatedUser.getLastModifiedBy())
            .setLastModifiedDate(ProtobufMappers.instantToTimestamp(updatedUser.getLastModifiedDate()))
            .addAuthorities(AuthoritiesConstants.USER)
            .build();

        try {
            // Create the User
            stub.updateUser(userProto);
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        }
    }

    @Test
    @Transactional
    public void deleteUser() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeDelete = userRepository.findAll().size();

        // Delete the user
        stub.deleteUser(StringValue.newBuilder().setValue(user.getLogin()).build());

        // Validate the database is empty
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    public void getAllAuthorities() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            DEFAULT_EMAIL,
            DEFAULT_PASSWORD,
            Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        List<String> roles = new ArrayList<>();
        stub.getAllAuthorities(Empty.getDefaultInstance()).forEachRemaining(role -> roles.add(role.getValue()));
        assertThat(roles).contains(AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER);
    }

    @Test
    public void getAllAuthoritiesRejected() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            DEFAULT_EMAIL,
            DEFAULT_PASSWORD,
            Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            List<String> roles = new ArrayList<>();
            stub.getAllAuthorities(Empty.getDefaultInstance()).forEachRemaining(role -> roles.add(role.getValue()));
            failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
        } catch (StatusRuntimeException e){
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        }
    }

}
