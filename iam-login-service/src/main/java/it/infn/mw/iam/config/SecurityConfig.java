package it.infn.mw.iam.config;

import org.mitre.oauth2.service.OAuth2TokenEntityService;
import org.mitre.oauth2.web.CorsFilter;
import org.mitre.openid.connect.client.OIDCAuthenticationProvider;
import org.mitre.openid.connect.web.AuthenticationTimeStamper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationManager;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationProcessingFilter;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenEndpointFilter;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.oauth2.provider.expression.OAuth2WebSecurityExpressionHandler;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import it.infn.mw.iam.oidc.OidcAccessDeniedHandler;
import it.infn.mw.iam.oidc.SaveRequestOidcAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Configuration
  @Order(100)
  public static class UserLoginConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private AuthenticationTimeStamper authenticationTimeStamper;

    @Autowired
    private OAuth2WebSecurityExpressionHandler oAuth2WebSecurityExpressionHandler;

    @Autowired
    @Qualifier("mitreAuthzRequestFilter")
    private GenericFilterBean authorizationRequestFilter;

    @Autowired
    @Qualifier("iamUserDetailsService")
    private UserDetailsService iamUserDetailsService;

    @Autowired
    public void configureGlobal(final AuthenticationManagerBuilder auth)
      throws Exception {

      auth.userDetailsService(iamUserDetailsService);
    }

    @Override
    public void configure(final WebSecurity web) throws Exception {

      web.expressionHandler(oAuth2WebSecurityExpressionHandler);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      // @formatter:off

      http
        .requestMatchers()
          .antMatchers("/","/login**","/logout", "/authorize", "/manage/**")
          .and()
        .sessionManagement()
          .enableSessionUrlRewriting(false)
        .and()
          .authorizeRequests()
            .antMatchers("/login**").permitAll()
            .antMatchers("/authorize**").permitAll()
            .antMatchers("/").authenticated()
        .and()
          .formLogin()
            .loginPage("/login")
            .failureUrl("/login?error=failure")
            .successHandler(authenticationTimeStamper)
        .and()
          .exceptionHandling()
            .accessDeniedHandler(new OidcAccessDeniedHandler())
            .and()
          .addFilterBefore(authorizationRequestFilter, SecurityContextPersistenceFilter.class)
        .logout()
          .logoutUrl("/logout")
          .and()
        .anonymous()
        .and()
        .csrf()
          .requireCsrfProtectionMatcher(new AntPathRequestMatcher("/authorize"))
        .disable();
      ;
      // @formatter:on

    }

    @Bean
    public OAuth2WebSecurityExpressionHandler oAuth2WebSecurityExpressionHandler() {

      return new OAuth2WebSecurityExpressionHandler();
    }
  }

  @Configuration
  @Order(105)
  public static class ExternalOidcLogin extends WebSecurityConfigurerAdapter {

    @Autowired
    @Qualifier("OIDCAuthenticationManager")
    private AuthenticationManager oidcAuthManager;

    @Autowired
    OIDCAuthenticationProvider authProvider;

    @Autowired
    @Qualifier("openIdConnectAuthenticationFilter")
    private SaveRequestOidcAuthenticationFilter oidcFilter;

    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {

      return oidcAuthManager;
    }

    @Bean(name = "ExternalAuthenticationEntryPoint")
    public LoginUrlAuthenticationEntryPoint authenticationEntryPoint() {

      return new LoginUrlAuthenticationEntryPoint("/openid_connect_login");
    }

    @Override
    protected void configure(final AuthenticationManagerBuilder auth)
      throws Exception {

      auth.authenticationProvider(authProvider);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      //@formatter:off
      http
        .antMatcher("/openid_connect_login**")
        .exceptionHandling()
          .authenticationEntryPoint(authenticationEntryPoint())
          .accessDeniedHandler(new OidcAccessDeniedHandler())
          .and()
        .addFilterAfter(oidcFilter, SecurityContextPersistenceFilter.class)
        .authorizeRequests()
          .antMatchers("/openid_connect_login**").permitAll()
          .and()
        .sessionManagement()
          .enableSessionUrlRewriting(false)
          .sessionCreationPolicy(SessionCreationPolicy.ALWAYS);
      //@formatter:on
    }
  }

  @Configuration
  @Order(9)
  public static class OAuthResourceServerConfiguration {

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private OAuth2TokenEntityService tokenService;

    @Bean
    public FilterRegistrationBean disabledAutomaticFilterRegistration(
      OAuth2AuthenticationProcessingFilter f) {

      FilterRegistrationBean b = new FilterRegistrationBean(f);
      b.setEnabled(false);
      return b;
    }

    @Bean(name = "resourceServerFilter")
    public OAuth2AuthenticationProcessingFilter oauthResourceServerFilter() {

      OAuth2AuthenticationManager manager = new OAuth2AuthenticationManager();
      manager.setTokenServices(tokenService);

      OAuth2AuthenticationProcessingFilter filter = new OAuth2AuthenticationProcessingFilter();
      filter.setAuthenticationEntryPoint(authenticationEntryPoint);
      filter.setAuthenticationManager(manager);
      return filter;
    }

  }

  @Configuration
  @Order(10)
  public static class ApiEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationProcessingFilter resourceFilter;

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    public void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/api/**")
        .addFilterBefore(resourceFilter, SecurityContextPersistenceFilter.class)
        .exceptionHandling()
        .authenticationEntryPoint(authenticationEntryPoint).and()
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER)
        .and().csrf().disable();
      // @formatter:on

    }
  }

  /**
   * @author cecco
   *
   */
  @Configuration
  @Order(11)
  public static class ResourceEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationProcessingFilter resourceFilter;

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private CorsFilter corsFilter;

    @Override

    public void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/resource/**")
        .exceptionHandling().authenticationEntryPoint(authenticationEntryPoint)
          .and()
        .addFilterAfter(resourceFilter, SecurityContextPersistenceFilter.class)
        .addFilterBefore(corsFilter, WebAsyncManagerIntegrationFilter.class)
        .sessionManagement()
          .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
          .and()
        .authorizeRequests()
          .antMatchers("/resource/**")
          .permitAll()
          .and()
        .csrf()
          .disable();
      // @formatter:on
    }
  }

  /**
   * @author cecco
   *
   */
  @Configuration
  @Order(12)
  public static class RegisterEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationProcessingFilter resourceFilter;

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private CorsFilter corsFilter;

    @Override
    public void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/register/**")
        .exceptionHandling().authenticationEntryPoint(authenticationEntryPoint)
          .and()
        .addFilterAfter(resourceFilter, SecurityContextPersistenceFilter.class)
        .addFilterBefore(corsFilter, WebAsyncManagerIntegrationFilter.class)
        .sessionManagement()
          .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
          .and()
        .authorizeRequests()
          .antMatchers("/register/**").permitAll()
          .and()
        .csrf()
          .disable();
      // @formatter:on
    }
  }

  /**
   * @author cecco
   *
   */
  @Configuration
  @Order(13)
  public static class UserInfoEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationProcessingFilter resourceFilter;

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private CorsFilter corsFilter;

    @Override

    public void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/userinfo**")
        .exceptionHandling().authenticationEntryPoint(authenticationEntryPoint)
          .and()
        .addFilterAfter(resourceFilter, SecurityContextPersistenceFilter.class)
        .addFilterBefore(corsFilter, WebAsyncManagerIntegrationFilter.class)
       .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
       .csrf().disable();
      // @formatter:on
    }
  }

  @Configuration
  @Order(15)
  public static class IntrospectEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    @Qualifier("clientUserDetailsService")
    private UserDetailsService userDetailsService;

    @Autowired
    private CorsFilter corsFilter;

    @Override
    protected void configure(final AuthenticationManagerBuilder auth)
      throws Exception {

      auth.userDetailsService(userDetailsService);
    }

    private ClientCredentialsTokenEndpointFilter clientCredentialsEndpointFilter()
      throws Exception {

      ClientCredentialsTokenEndpointFilter filter = new ClientCredentialsTokenEndpointFilter(
        "/introspect");
      filter.setAuthenticationManager(authenticationManager());
      return filter;
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
       .antMatcher("/introspect**")
       .httpBasic()
        .authenticationEntryPoint(authenticationEntryPoint)
        .and()
      .addFilterBefore(corsFilter, SecurityContextPersistenceFilter.class)
      .addFilterBefore(clientCredentialsEndpointFilter(), BasicAuthenticationFilter.class)
      .exceptionHandling()
        .authenticationEntryPoint(authenticationEntryPoint)
        .and()
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
      .csrf().disable();
      // @formatter:on
    }
  }

  @Configuration
  @Order(16)
  public static class RevokeEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    @Qualifier("clientUserDetailsService")
    private UserDetailsService userDetailsService;

    @Autowired
    private CorsFilter corsFilter;

    @Override
    protected void configure(final AuthenticationManagerBuilder auth)
      throws Exception {

      auth.userDetailsService(userDetailsService);
    }

    private ClientCredentialsTokenEndpointFilter clientCredentialsEndpointFilter()
      throws Exception {

      ClientCredentialsTokenEndpointFilter filter = new ClientCredentialsTokenEndpointFilter(
        "/revoke");
      filter.setAuthenticationManager(authenticationManager());
      return filter;
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/revoke**")
        .httpBasic()
          .authenticationEntryPoint(authenticationEntryPoint)
          .and()
        .addFilterBefore(corsFilter, SecurityContextPersistenceFilter.class)
        .addFilterBefore(clientCredentialsEndpointFilter(), BasicAuthenticationFilter.class)
        .exceptionHandling()
          .authenticationEntryPoint(authenticationEntryPoint)
          .and()
        .sessionManagement()
          .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
      // @formatter:on
    }
  }

  @Configuration
  @Order(17)
  public static class JwkEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private Http403ForbiddenEntryPoint http403ForbiddenEntryPoint;

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/jwk**")
      .exceptionHandling()
        .authenticationEntryPoint(http403ForbiddenEntryPoint)
        .and()
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
    .authorizeRequests()
      .antMatchers("/**")
      .permitAll();
      // @formatter:on
    }
  }

  @Configuration
  @Order(18)
  public static class ScimApiEndpointConfig
    extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2AuthenticationProcessingFilter resourceFilter;

    @Autowired
    private OAuth2AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private CorsFilter corsFilter;
    
    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      // @formatter:off
      http
        .antMatcher("/scim/**")
        .exceptionHandling().authenticationEntryPoint(authenticationEntryPoint)
          .and()
        .addFilterAfter(resourceFilter, SecurityContextPersistenceFilter.class)
        .addFilterBefore(corsFilter, WebAsyncManagerIntegrationFilter.class)
        .sessionManagement()
          .sessionCreationPolicy(SessionCreationPolicy.NEVER)
          .and()
        .authorizeRequests()
          .antMatchers("/scim/**")
          .authenticated()
          .and()
        .csrf()
          .disable();
      // @formatter:on
    }
  }

  @Configuration
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @Profile("dev")
  public static class H2ConsoleEndpointAuthorizationConfig
    extends WebSecurityConfigurerAdapter {

    protected void configure(final HttpSecurity http) throws Exception {

      HttpSecurity h2Console = http.requestMatchers()
        .antMatchers("/h2-console", "/h2-console/**")
        .and()
        .csrf()
        .disable();

      h2Console.httpBasic();
      h2Console.headers()
        .frameOptions()
        .disable();

      h2Console.authorizeRequests()
        .antMatchers("/h2-console/**", "/h2-console")
        .permitAll();
    }

    @Override
    public void configure(WebSecurity builder) throws Exception {

      builder.debug(true);
    }
  }

}
