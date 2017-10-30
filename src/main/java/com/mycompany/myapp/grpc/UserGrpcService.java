package com.mycompany.myapp.grpc;

import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.security.SecurityUtils;
import com.mycompany.myapp.service.MailService;
import com.mycompany.myapp.service.UserService;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import io.grpc.Status;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.LoggerFactory;

@GRpcService(interceptors = {AuthenticationInterceptor.class})
public class UserGrpcService extends RxUserServiceGrpc.UserServiceImplBase {

    private final org.slf4j.Logger log = LoggerFactory.getLogger(UserGrpcService.class);

    private final UserRepository userRepository;

    private final MailService mailService;

    private final UserService userService;

    private final UserProtoMapper userProtoMapper;

    public UserGrpcService(UserRepository userRepository, MailService mailService, UserService userService,
                        UserProtoMapper userProtoMapper) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.userService = userService;
        this.userProtoMapper = userProtoMapper;
    }

    @Override
    public Single<UserProto> createUser(Single<UserProto> request) {
        return request
            .doOnSuccess(userProto -> log.debug("gRPC request to save User : {}", userProto))
            .filter(userProto -> userProto.getIdOneofCase() != UserProto.IdOneofCase.ID)
            .switchIfEmpty(Single.error(Status.INVALID_ARGUMENT.withDescription("A new user cannot already have an ID").asException()))
            .filter(userProto -> !userRepository.findOneByLogin(userProto.getLogin().toLowerCase()).isPresent())
            .switchIfEmpty(Single.error(Status.ALREADY_EXISTS.withDescription("Login already in use").asException()))
            .filter(userProto -> !userRepository.findOneByEmailIgnoreCase(userProto.getEmail()).isPresent())
            .switchIfEmpty(Single.error(Status.ALREADY_EXISTS.withDescription("Email already in use").asException()))
            .map(userProtoMapper::userProtoToUserDTO)
            .map(userService::createUser)
            .doOnSuccess(mailService::sendCreationEmail)
            .map(userProtoMapper::userToUserProto);
    }

    @Override
    public Single<UserProto> updateUser(Single<UserProto> request) {
        return request
            .doOnSuccess(userProto -> log.debug("gRPC request to update User : {}", userProto))
            .filter(userProto -> !userRepository
                .findOneByEmailIgnoreCase(userProto.getEmail())
                .map(User::getId)
                .filter(id -> !id.equals(userProto.getId()))
                .isPresent()
            )
            .switchIfEmpty(Single.error(Status.ALREADY_EXISTS.withDescription("Email already in use").asException()))
            .filter(userProto -> !userRepository
                .findOneByLogin(userProto.getLogin().toLowerCase())
                .map(User::getId)
                .filter(id -> !id.equals(userProto.getId()))
                .isPresent()
            )
            .switchIfEmpty(Single.error(Status.ALREADY_EXISTS.withDescription("Login already in use").asException()))
            .map(userProtoMapper::userProtoToUserDTO)
            .map(user -> userService.updateUser(user).orElseThrow(Status.NOT_FOUND::asException))
            .map(userProtoMapper::userDTOToUserProto);
    }

    @Override
    public Flowable<UserProto> getAllUsers(Single<PageRequest> request) {
        log.debug("gRPC request to get all users");
        return request
            .map(ProtobufMappers::pageRequestProtoToPageRequest)
            .map(userService::getAllManagedUsers)
            .flatMapPublisher(Flowable::fromIterable)
            .map(userProtoMapper::userDTOToUserProto);
    }

    @Override
    public Single<UserProto> getUser(Single<StringValue> request) {
        return request
            .map(StringValue::getValue)
            .doOnSuccess(login -> log.debug("gRPC request to get User : {}", login))
            .map(login -> userService.getUserWithAuthoritiesByLogin(login).orElseThrow(Status.NOT_FOUND::asException))
            .map(userProtoMapper::userToUserProto);
    }

    @Override
    public Single<Empty> deleteUser(Single<StringValue> request) {
        return request
            .map(StringValue::getValue)
            .doOnSuccess(login -> log.debug("gRPC request to delete User: {}", login))
            .doOnSuccess(userService::deleteUser)
            .map(l -> Empty.newBuilder().build());
    }

    @Override
    public Flowable<StringValue> getAllAuthorities(Single<Empty> request) {
        return request
            .doOnSuccess(e -> log.debug("gRPC request to gat all authorities"))
            .filter(e -> SecurityUtils.isCurrentUserInRole(AuthoritiesConstants.ADMIN))
            .switchIfEmpty(Single.error(Status.PERMISSION_DENIED.asException()))
            .map(e -> userService.getAuthorities())
            .flatMapPublisher(Flowable::fromIterable)
            .map(authority -> StringValue.newBuilder().setValue(authority).build());
    }

}
