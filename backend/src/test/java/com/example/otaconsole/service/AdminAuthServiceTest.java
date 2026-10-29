package com.example.otaconsole.service;
import com.example.otaconsole.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminAuthServiceTest {
 @Test void passwordRotationAcceptsWeakPasswordAndInvalidatesAllSessions() {
  var repo=mock(AdminUserRepository.class); var stored=new AtomicReference<String>();
  doAnswer(i->{stored.set(i.getArgument(0));return null;}).when(repo).insertAdminIfMissing(anyString());
  when(repo.findPasswordHash("admin")).thenAnswer(i->Optional.ofNullable(stored.get()));
  when(repo.updatePassword(anyString(),anyString())).thenAnswer(i->{if(!stored.get().equals(i.getArgument(0)))return false;stored.set(i.getArgument(1));return true;});
  var service=new AdminAuthService("old",repo);service.initializeAdmin();
  String first=service.login("admin","old").get("token"), second=service.login("admin","old").get("token");
  assertThrows(ResponseStatusException.class,()->service.changePassword(null,"old","123456"));
  assertThrows(ResponseStatusException.class,()->service.changePassword(first,"wrong","123456"));
  assertThrows(ResponseStatusException.class,()->service.changePassword(first,"old",""));
  assertTrue(service.isValid(first));
  service.changePassword(first,"old","123456");
  assertFalse(service.isValid(first));assertFalse(service.isValid(second));
  assertThrows(ResponseStatusException.class,()->service.login("admin","old"));
  String token=service.login("admin","123456").get("token");
  service.changePassword(token,"123456","空 格!$x");
  assertNotNull(service.login("admin","空 格!$x").get("token"));
  assertNotEquals("空 格!$x",stored.get());
 }
}
