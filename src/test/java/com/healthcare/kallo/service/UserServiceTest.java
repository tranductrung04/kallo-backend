package com.healthcare.kallo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.dto.request.DeleteAccountRequest;
import com.healthcare.kallo.dto.request.UpdateUserRequest;
import com.healthcare.kallo.model.User;
import com.healthcare.kallo.model.enums.Role;
import com.healthcare.kallo.repository.UserRepository;
import com.healthcare.kallo.service.impl.UserServiceImpl;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock UserRepository users;
  @Mock StorageService storage;
  @Mock PasswordEncoder passwordEncoder;
  @Mock GoogleTokenVerifier googleTokenVerifier;

  private UserServiceImpl service;
  private User user;

  @BeforeEach
  void setUp() {
    service =
        new UserServiceImpl(
            users,
            storage,
            new StorageProperties(
                "http://minio:9000",
                "access",
                "secret",
                "kallo",
                "http://localhost:9000",
                Duration.ofMinutes(15)),
            passwordEncoder,
            googleTokenVerifier);
    user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("user@example.com");
    user.setDisplayName("User");
    user.setTimezone("Asia/Ho_Chi_Minh");
    user.setRole(Role.USER);
    user.setEmailVerified(true);
    user.setPasswordHash("hash");
    lenient().when(users.findById(user.getId())).thenReturn(Optional.of(user));
  }

  @Test
  void updatesProfileWithValidTimezone() {
    doAnswer(
            invocation -> {
              user.setDisplayName("Updated");
              user.setTimezone("Europe/Paris");
              return 1;
            })
        .when(users)
        .updateProfile(eq(user.getId()), eq("Updated"), eq("Europe/Paris"), any());
    var result =
        service.updateMe(user.getId(), new UpdateUserRequest(" Updated ", "Europe/Paris"));

    assertThat(result.displayName()).isEqualTo("Updated");
    assertThat(result.timezone()).isEqualTo("Europe/Paris");
    verify(users).updateProfile(eq(user.getId()), eq("Updated"), eq("Europe/Paris"), any());
  }

  @Test
  void rejectsAvatarWithInvalidMagicBytes() {
    var file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.updateAvatar(user.getId(), file))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid file");
    verifyNoInteractions(storage);
  }

  @Test
  void uploadsValidJpegAndReturnsPresignedUrl() {
    var file =
        new MockMultipartFile(
            "file", "avatar.png", "image/png", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    when(storage.presignedUrl(anyString(), any())).thenReturn("http://localhost:9000/avatar");
    when(users.updateAvatarKey(eq(user.getId()), anyString(), any())).thenReturn(1);
    user.setAvatarUrl("avatars/" + user.getId() + "/old.jpg");

    var result = service.updateAvatar(user.getId(), file);

    assertThat(result.avatarUrl()).isEqualTo("http://localhost:9000/avatar");
    verify(storage).upload(anyString(), any(), eq(3L), eq("image/jpeg"));
    verify(users).updateAvatarKey(eq(user.getId()), anyString(), any());
    verify(storage).delete("avatars/" + user.getId() + "/old.jpg");
  }

  @Test
  void keepsNewAvatarWhenOldObjectDeletionFails() {
    var file =
        new MockMultipartFile(
            "file", "avatar.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    when(storage.presignedUrl(anyString(), any())).thenReturn("http://localhost:9000/avatar");
    when(users.updateAvatarKey(eq(user.getId()), anyString(), any())).thenReturn(1);
    user.setAvatarUrl("avatars/" + user.getId() + "/old.jpg");
    doThrow(new IllegalStateException("storage unavailable"))
        .when(storage)
        .delete("avatars/" + user.getId() + "/old.jpg");

    var result = service.updateAvatar(user.getId(), file);

    assertThat(result.avatarUrl()).isEqualTo("http://localhost:9000/avatar");
    verify(users).updateAvatarKey(eq(user.getId()), anyString(), any());
  }

  @Test
  void rejectsAvatarWhenDatabaseUpdateAffectsNoUser() {
    var file =
        new MockMultipartFile(
            "file", "avatar.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    when(users.updateAvatarKey(eq(user.getId()), anyString(), any())).thenReturn(0);

    assertThatThrownBy(() -> service.updateAvatar(user.getId(), file))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Authentication required");
    verify(storage).upload(anyString(), any(), eq(3L), eq("image/jpeg"));
    verify(storage, never()).delete(anyString());
  }

  @Test
  void rejectsAvatarOverTwoMegabytes() {
    var file =
        new MockMultipartFile(
            "file", "avatar.jpg", "image/jpeg", new byte[(2 * 1024 * 1024) + 1]);

    assertThatThrownBy(() -> service.updateAvatar(user.getId(), file))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("too large");
    verifyNoInteractions(storage);
  }

  @Test
  void deletesAccountAndCleansAllAvatarObjectsEvenWhenStorageFails() {
    when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);
    doThrow(new IllegalStateException("storage unavailable"))
        .when(storage)
        .deletePrefix("avatars/" + user.getId() + "/");

    service.deleteAccount(user.getId(), new DeleteAccountRequest("Password1", null));

    verify(storage).deletePrefix("avatars/" + user.getId() + "/");
    verify(users).delete(user);
  }

  @Test
  void rejectsAccountDeletionWithWrongPassword() {
    when(passwordEncoder.matches("WrongPassword1", "hash")).thenReturn(false);

    assertThatThrownBy(
            () -> service.deleteAccount(user.getId(), new DeleteAccountRequest("WrongPassword1", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("confirmation is invalid");
    verify(users, never()).delete(user);
  }

  @Test
  void deletesGoogleAccountWithFreshMatchingToken() {
    user.setPasswordHash(null);
    user.setGoogleSub("google-sub");
    when(googleTokenVerifier.verifyFresh("fresh-token"))
        .thenReturn(new GoogleTokenVerifier.GoogleClaims("google-sub", "user@example.com", "User", null));

    service.deleteAccount(user.getId(), new DeleteAccountRequest(null, "fresh-token"));

    verify(googleTokenVerifier).verifyFresh("fresh-token");
    verify(storage).deletePrefix("avatars/" + user.getId() + "/");
    verify(users).delete(user);
  }

  @Test
  void googleAccountDeletionRequiresFreshGoogleToken() {
    user.setPasswordHash(null);
    user.setGoogleSub("google-sub");
    when(googleTokenVerifier.verifyFresh("old-token"))
        .thenThrow(new BusinessException(com.healthcare.kallo.common.ErrorCode.GOOGLE_TOKEN_INVALID));

    assertThatThrownBy(
            () -> service.deleteAccount(user.getId(), new DeleteAccountRequest(null, "old-token")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Google token is invalid");
    verify(users, never()).delete(user);
  }
}
