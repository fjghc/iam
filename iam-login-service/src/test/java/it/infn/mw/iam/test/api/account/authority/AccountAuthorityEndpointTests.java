package it.infn.mw.iam.test.api.account.authority;

import static java.lang.String.format;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.transaction.Transactional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import it.infn.mw.iam.IamLoginService;
import it.infn.mw.iam.persistence.repository.IamAccountRepository;
import it.infn.mw.iam.persistence.repository.IamAuthoritiesRepository;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = IamLoginService.class)
@WebAppConfiguration
@Transactional
public class AccountAuthorityEndpointTests {

  public static final String TEST_100 = "test_100";
  public static final String TEST_100_UUID = "f2ce8cb2-a1db-4884-9ef0-d8842cc02b4a";

  public static final String INVALID_USER_ID = "6cbc791d-561c-43c3-af31-dd89f41e3b29";

  public static final String ROLE_USER = "ROLE_USER";
  public static final String ROLE_ADMIN = "ROLE_ADMIN";


  @Autowired
  WebApplicationContext context;

  @Autowired
  IamAccountRepository iamAccountRepo;

  @Autowired
  IamAuthoritiesRepository iamAuthoritiesRepo;

  MockMvc mvc;

  @Before
  public void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private void addUserAuthority(String userId, String authority) {
    iamAuthoritiesRepo.findByAuthority(authority)
      .ifPresent(auth -> iamAccountRepo.findByUuid(userId).ifPresent(a -> {
        a.getAuthorities().add(auth);
        iamAccountRepo.save(a);
      }));
  }

  private void removeUserAuthority(String userId, String authority) {
    iamAuthoritiesRepo.findByAuthority(authority)
      .ifPresent(auth -> iamAccountRepo.findByUuid(userId).ifPresent(a -> {
        a.getAuthorities().remove(auth);
        iamAccountRepo.save(a);
      }));
  }

  @Test
  public void anonymousAccessToAuthorityEndpointFails() throws Exception {
    mvc.perform(get("/iam/account/{id}/authorities", TEST_100_UUID))
      .andDo(print())
      .andExpect(status().isUnauthorized());

    mvc
      .perform(post("/iam/account/{id}/authorities", TEST_100_UUID).param("authority", "ROLE_ADMIN")
        .contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isUnauthorized());


    mvc
      .perform(delete("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", "ROLE_USER").contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser("test")
  public void unprivilegedAccessToAuthorityEndpointFails() throws Exception {
    mvc.perform(get("/iam/account/{id}/authorities", TEST_100_UUID))
      .andDo(print())
      .andExpect(status().isForbidden());

    mvc
      .perform(post("/iam/account/{id}/authorities", TEST_100_UUID).param("authority", "ROLE_ADMIN")
        .contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isForbidden());

    mvc
      .perform(delete("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", "ROLE_USER").contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void getAuthoritiesWorks() throws Exception {
    mvc.perform(get("/iam/account/{id}/authorities", TEST_100_UUID))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.authorities", not(empty())))
      .andExpect(jsonPath("$.authorities", contains("ROLE_USER")));
  }


  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void getMeAuthoritiesWorks() throws Exception {
    mvc.perform(get("/iam/me/authorities"))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.authorities", not(empty())))
      .andExpect(jsonPath("$.authorities", contains("ROLE_USER", "ROLE_ADMIN")));
  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void AddInvalidAuthorityFails() throws Exception {

    String invalidAuthority = "CICCIO_PAGLIA";
    String expectedErrorMessage = String.format("Invalid authority: '%s'", invalidAuthority);

    mvc
      .perform(post("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", invalidAuthority).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));

  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void AddEmptyAuthorityFails() throws Exception {

    String invalidAuthority = "";
    String expectedErrorMessage = String.format("Authority cannot be an empty string");

    mvc
      .perform(post("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", invalidAuthority).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));

  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void AddSuperLongAuthorityFails() throws Exception {

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < 255; i++) {
      sb.append("A");
    }

    String invalidAuthority = sb.toString();
    String expectedErrorMessage = String.format("Invalid authority size");

    mvc
      .perform(post("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", invalidAuthority).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));

  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void AddAlreadyBoundAuthorityGetsBadRequest() throws Exception {

    String alreadyBoundAuthority = "ROLE_USER";
    String expectedErrorMessage = String.format("Authority '%s' is already bound to user '%s' (%s)",
        alreadyBoundAuthority, TEST_100, TEST_100_UUID);

    mvc
      .perform(post("/iam/account/{id}/authorities", TEST_100_UUID).param("authority", "ROLE_USER")
        .contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));
  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void AddAuthorityWorks() throws Exception {

    String authority = ROLE_ADMIN;

    mvc.perform(get("/iam/account/{id}/authorities", TEST_100_UUID))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.authorities", contains(ROLE_USER)));

    mvc.perform(post("/iam/account/{id}/authorities", TEST_100_UUID).param("authority", authority)
      .contentType(APPLICATION_FORM_URLENCODED_VALUE)).andDo(print()).andExpect(status().isOk());

    mvc.perform(get("/iam/account/{id}/authorities", TEST_100_UUID))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.authorities", hasSize(2)))
      .andExpect(jsonPath("$.authorities", containsInAnyOrder(ROLE_USER, ROLE_ADMIN)));


    // remove
    removeUserAuthority(TEST_100_UUID, ROLE_ADMIN);

  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void DeleteAuthorityWorks() throws Exception {

    String authority = "ROLE_USER";

    mvc.perform(delete("/iam/account/{id}/authorities", TEST_100_UUID).param("authority", authority)
      .contentType(APPLICATION_FORM_URLENCODED_VALUE)).andDo(print()).andExpect(status().isOk());

    mvc.perform(get("/iam/account/{id}/authorities", TEST_100_UUID))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.authorities", empty()));


    // Readd authority
    addUserAuthority(TEST_100_UUID, ROLE_USER);

  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void DeleteUnboundAuthoritySilentlySucceeds() throws Exception {

    String unboundAuthority = ROLE_ADMIN;

    mvc
      .perform(delete("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", unboundAuthority).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void deleteInvalidAuthorityGetsBadRequest() throws Exception {

    String invalidAuthority = "CICCIO_PAGLIA";
    String expectedErrorMessage = String.format("Invalid authority: '%s'", invalidAuthority);

    mvc
      .perform(delete("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", invalidAuthority).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));
  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void deleteEmptyAuthorityGetsBadRequest() throws Exception {

    String emptyAuthority = "";
    String expectedErrorMessage = String.format("Authority cannot be an empty string");


    mvc
      .perform(delete("/iam/account/{id}/authorities", TEST_100_UUID)
        .param("authority", emptyAuthority).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));
  }

  @Test
  @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
  public void invalidUserIdFailsWithNotFound() throws Exception {

    String expectedErrorMessage = format("No account found for id '%s'", INVALID_USER_ID);

    mvc.perform(get("/iam/account/{id}/authorities", INVALID_USER_ID))
      .andDo(print())
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));

    mvc
      .perform(post("/iam/account/{id}/authorities", INVALID_USER_ID).param("authority", ROLE_ADMIN)
        .contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));

    mvc
      .perform(delete("/iam/account/{id}/authorities", INVALID_USER_ID)
        .param("authority", ROLE_USER).contentType(APPLICATION_FORM_URLENCODED_VALUE))
      .andDo(print())
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.error", equalTo(expectedErrorMessage)));

  }

}