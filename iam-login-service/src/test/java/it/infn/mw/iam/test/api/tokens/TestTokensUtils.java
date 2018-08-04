/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare (INFN). 2016-2018
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.infn.mw.iam.test.api.tokens;

import static it.infn.mw.iam.api.tokens.AbstractTokensController.APPLICATION_JSON_CONTENT_TYPE;
import static it.infn.mw.iam.api.tokens.Constants.ACCESS_TOKENS_ENDPOINT;
import static it.infn.mw.iam.api.tokens.Constants.ACCESS_TOKENS_ME_ENDPOINT;
import static it.infn.mw.iam.api.tokens.Constants.REFRESH_TOKENS_ENDPOINT;
import static it.infn.mw.iam.api.tokens.Constants.REFRESH_TOKENS_ME_ENDPOINT;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;

import org.mitre.oauth2.model.ClientDetailsEntity;
import org.mitre.oauth2.model.OAuth2AccessTokenEntity;
import org.mitre.oauth2.model.OAuth2RefreshTokenEntity;
import org.mitre.oauth2.service.ClientDetailsEntityService;
import org.mitre.oauth2.service.impl.DefaultOAuth2ProviderTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.infn.mw.iam.api.common.ListResponseDTO;
import it.infn.mw.iam.api.tokens.model.AccessToken;
import it.infn.mw.iam.api.tokens.model.RefreshToken;
import it.infn.mw.iam.core.user.exception.IamAccountException;
import it.infn.mw.iam.persistence.model.IamAccount;
import it.infn.mw.iam.persistence.repository.IamAccountRepository;
import it.infn.mw.iam.persistence.repository.IamOAuthAccessTokenRepository;
import it.infn.mw.iam.persistence.repository.IamOAuthRefreshTokenRepository;
import it.infn.mw.iam.test.util.oauth.MockOAuth2Request;

public class TestTokensUtils {

  @Autowired
  protected IamOAuthAccessTokenRepository accessTokenRepository;

  @Autowired
  protected IamOAuthRefreshTokenRepository refreshTokenRepository;

  @Autowired
  private ClientDetailsEntityService clientDetailsService;

  @Autowired
  protected IamAccountRepository accountRepository;

  @Autowired
  protected DefaultOAuth2ProviderTokenService tokenService;

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper mapper;

  protected MockMvc mvc;

  public void initMvc() {
    initMvc(context);
  }

  public void initMvc(WebApplicationContext context) {
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .alwaysDo(print())
      .build();
  }

  private OAuth2Authentication oauth2Authentication(ClientDetailsEntity client, String username,
      String[] scopes) {

    Authentication userAuth = null;

    if (username != null) {
      userAuth = new UsernamePasswordAuthenticationToken(username, "");
    }

    MockOAuth2Request req = new MockOAuth2Request(client.getClientId(), scopes);
    OAuth2Authentication auth = new OAuth2Authentication(req, userAuth);

    return auth;
  }

  public ClientDetailsEntity loadTestClient(String clientId) {
    return clientDetailsService.loadClientByClientId(clientId);
  }

  public IamAccount loadTestUser(String userId) {
    return accountRepository.findByUsername(userId)
      .orElseThrow(() -> new IamAccountException("User not found"));
  }

  public OAuth2AccessTokenEntity buildAccessToken(ClientDetailsEntity client, String username,
      String[] scopes) {
    return tokenService.createAccessToken(oauth2Authentication(client, username, scopes));
  }

  public OAuth2AccessTokenEntity buildAccessToken(ClientDetailsEntity client, String username,
      String[] scopes, Date expiration) {
    OAuth2AccessTokenEntity token =
        tokenService.createAccessToken(oauth2Authentication(client, username, scopes));
    token.setExpiration(expiration);
    return token;
  }

  public OAuth2AccessTokenEntity buildExpiredAccessToken(ClientDetailsEntity client,
      String username, String[] scopes) {

    OAuth2AccessTokenEntity token =
        tokenService.createAccessToken(oauth2Authentication(client, username, scopes));
    token.setExpiration(getDateOffsetBy(-10));
    accessTokenRepository.save(token);
    return token;
  }

  public OAuth2AccessTokenEntity buildAccessTokenWithExpiredRefreshToken(ClientDetailsEntity client,
      String username, String[] scopes) {

    return buildAccessTokenOfflineAccessCustomExpiration(client, username, scopes,
        getDateOffsetBy(-10));
  }

  public OAuth2AccessTokenEntity buildAccessTokenOfflineAccessCustomExpiration(
      ClientDetailsEntity client, String username, String[] scopes, Date refreshTokenExpiration) {

    OAuth2AccessTokenEntity token =
        tokenService.createAccessToken(oauth2Authentication(client, username, scopes));
    OAuth2RefreshTokenEntity refreshToken = token.getRefreshToken();
    refreshToken.setExpiration(refreshTokenExpiration);
    refreshTokenRepository.save(refreshToken);
    return token;
  }

  public Date getDateOffsetBy(int offsetMins) {

    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.MINUTE, offsetMins);
    return cal.getTime();
  }

  public OAuth2AccessTokenEntity buildAccessToken(ClientDetailsEntity client, String[] scopes) {
    OAuth2AccessTokenEntity token =
        tokenService.createAccessToken(oauth2Authentication(client, null, scopes));
    return token;
  }

  public void clearAllTokens() {
    accessTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
  }

  public Authentication anonymousAuthenticationToken() {
    return new AnonymousAuthenticationToken("key", "anonymous",
        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
  }

  protected ListResponseDTO<AccessToken> getAccessTokenList() throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    return getAccessTokenList(new LinkedMultiValueMap<String, String>());
  }

  protected ListResponseDTO<AccessToken> getAccessTokenList(MultiValueMap<String, String> params)
      throws JsonParseException, JsonMappingException, UnsupportedEncodingException, IOException,
      Exception {

    return getAccessTokenList(ACCESS_TOKENS_ENDPOINT, params);
  }

  protected ListResponseDTO<AccessToken> getAccessTokenMeList() throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    return getAccessTokenList(ACCESS_TOKENS_ME_ENDPOINT, new LinkedMultiValueMap<String, String>());
  }

  protected ListResponseDTO<AccessToken> getAccessTokenMeList(MultiValueMap<String, String> params)
      throws JsonParseException, JsonMappingException, UnsupportedEncodingException, IOException,
      Exception {

    return getAccessTokenList(ACCESS_TOKENS_ME_ENDPOINT, params);
  }

  private ListResponseDTO<AccessToken> getAccessTokenList(String endpoint,
      MultiValueMap<String, String> params) throws JsonParseException, JsonMappingException,
      UnsupportedEncodingException, IOException, Exception {

    /* @formatter:off */
    return mapper.readValue(
        mvc.perform(get(endpoint)
            .contentType(APPLICATION_JSON_CONTENT_TYPE)
            .params(params))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(), new TypeReference<ListResponseDTO<AccessToken>>() {});
    /* @formatter:on */
  }

  protected ListResponseDTO<RefreshToken> getRefreshTokenList() throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    return getRefreshTokenList(new LinkedMultiValueMap<String, String>());
  }

  protected ListResponseDTO<RefreshToken> getRefreshTokenList(MultiValueMap<String, String> params)
      throws JsonParseException, JsonMappingException, UnsupportedEncodingException, IOException,
      Exception {

    return getRefreshTokenList(REFRESH_TOKENS_ENDPOINT, params);
  }

  protected ListResponseDTO<RefreshToken> getRefreshTokenMeList() throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    return getRefreshTokenMeList(new LinkedMultiValueMap<String, String>());
  }

  protected ListResponseDTO<RefreshToken> getRefreshTokenMeList(
      MultiValueMap<String, String> params) throws JsonParseException, JsonMappingException,
      UnsupportedEncodingException, IOException, Exception {

    return getRefreshTokenList(REFRESH_TOKENS_ME_ENDPOINT, params);
  }

  private ListResponseDTO<RefreshToken> getRefreshTokenList(String endpoint,
      MultiValueMap<String, String> params) throws JsonParseException, JsonMappingException,
      UnsupportedEncodingException, IOException, Exception {

    /* @formatter:off */
    return mapper.readValue(
        mvc.perform(get(endpoint)
            .contentType(APPLICATION_JSON_CONTENT_TYPE)
            .params(params))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(), new TypeReference<ListResponseDTO<RefreshToken>>() {});
    /* @formatter:on */
  }

  protected AccessToken getAccessToken(String id) throws JsonParseException, JsonMappingException,
      UnsupportedEncodingException, IOException, Exception {

    return getAccessToken(ACCESS_TOKENS_ENDPOINT, id);
  }

  protected AccessToken getAccessTokenMe(String id) throws JsonParseException, JsonMappingException,
    UnsupportedEncodingException, IOException, Exception {

    return getAccessToken(ACCESS_TOKENS_ME_ENDPOINT, id);
  }

  protected AccessToken getAccessToken(String endpoint, String id) throws JsonParseException, JsonMappingException,
      UnsupportedEncodingException, IOException, Exception {

    /* @formatter:off */
    return mapper.readValue(doGetTokenId(endpoint, id)
        .andReturn()
        .getResponse()
        .getContentAsString(),
        new TypeReference<AccessToken>() {});
    /* @formatter:on */
  }

  protected ResultActions doGetTokenId(String endpoint, String id) throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    return mvc.perform(get(endpoint + "/" + id).contentType(APPLICATION_JSON_CONTENT_TYPE));
    
  }

  protected RefreshToken getRefreshToken(String id) throws JsonParseException, JsonMappingException,
      UnsupportedEncodingException, IOException, Exception {

    return getRefreshToken(REFRESH_TOKENS_ENDPOINT, id);
  }

  protected RefreshToken getRefreshTokenMe(String id) throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    return getRefreshToken(REFRESH_TOKENS_ME_ENDPOINT, id);
  }

  protected RefreshToken getRefreshToken(String endpoint, String id) throws JsonParseException,
      JsonMappingException, UnsupportedEncodingException, IOException, Exception {

    /* @formatter:off */
    return mapper.readValue(doGetTokenId(endpoint, id)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(), new TypeReference<RefreshToken>() {});
    /* @formatter:on */
  }
}
