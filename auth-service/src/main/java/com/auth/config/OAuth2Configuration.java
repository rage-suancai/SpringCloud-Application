package com.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import javax.annotation.Resource;

@EnableAuthorizationServer
@Configuration
public class OAuth2Configuration extends AuthorizationServerConfigurerAdapter {

    @Resource
    private AuthenticationManager manager;
    /*@Resource
    private UserDetailsService service;*/
    @Resource
    private TokenStore store;
    @Resource
    private JwtAccessTokenConverter converter;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {

        clients
                .inMemory()
                .withClient("web")
                .secret(encoder.encode("654321"))
                .autoApprove(false)
                .scopes("book", "user", "borrow")
                .redirectUris("http://localhost:8101/login", "http://localhost:8201/login", "http://localhost:8301/login")
                .authorizedGrantTypes("client_credentials", "password", "implicit", "authorization_code", "refresh_token");

    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {

        security
                .passwordEncoder(encoder)
                .allowFormAuthenticationForClients()
                .checkTokenAccess("permitAll()");

    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {

        endpoints
                //.userDetailsService(service);
                .tokenServices(serverTokenServices())
                .authenticationManager(manager);

    }

    private AuthorizationServerTokenServices serverTokenServices() {

        DefaultTokenServices services = new DefaultTokenServices();
        services.setSupportRefreshToken(true);
        services.setTokenStore(store);
        services.setTokenEnhancer(converter);
        return services;

    }

}
