package com.weblab.rplace.weblab.rplace.core.security.eskylab;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.Prompt;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Place's side of e-skylab's Keycloak (ADR 0060): the backend is the confidential
 * client "place" and runs the authorization code flow with PKCE. Keycloak's
 * endpoints come from its discovery document, read once and kept; its signing keys
 * are cached and read again when an unknown key shows up. Keycloak's tokens never
 * leave this class: the code is redeemed here, the ID token is checked here, and
 * only its checked claims go out.
 */
@Component
public class EskylabClient {

    private static final int TIMEOUT_MILLIS = 5000;

    // Keycloak signs with one of these; symmetric and unsigned tokens are never accepted.
    private static final Set<JWSAlgorithm> SIGNING_ALGORITHMS = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512);

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    // Keycloak's endpoints and the ID token checks built on them, once discovery worked.
    private volatile Discovered discovered;

    private record Discovered(OIDCProviderMetadata metadata, IDTokenValidator idTokenValidator) {
    }

    public EskylabClient(@Value("${keycloak.issuer:}") String issuer,
                         @Value("${keycloak.client-id:}") String clientId,
                         @Value("${keycloak.client-secret:}") String clientSecret,
                         @Value("${keycloak.redirect-uri:}") String redirectUri) {
        this.issuer = issuer.trim();
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri.trim();
    }

    /** The environment variables the e-skylab login still needs; empty when it is configured. */
    public List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        if (issuer.isEmpty()) {
            missing.add("KEYCLOAK_ISSUER");
        }
        if (clientId.isEmpty()) {
            missing.add("KEYCLOAK_CLIENT_ID");
        }
        if (clientSecret.isBlank()) {
            missing.add("KEYCLOAK_CLIENT_SECRET");
        }
        if (redirectUri.isEmpty()) {
            missing.add("KEYCLOAK_REDIRECT_URI");
        }
        return missing;
    }

    /** Keycloak's authorization endpoint with this login's state, nonce and PKCE challenge. */
    public URI authorizationRequest(State state, Nonce nonce, CodeVerifier codeVerifier, boolean silent) throws EskylabLoginException {
        var request = new AuthenticationRequest.Builder(
                new ResponseType(ResponseType.Value.CODE),
                new Scope(OIDCScopeValue.OPENID),
                new ClientID(clientId),
                URI.create(redirectUri))
                .endpointURI(discover().metadata().getAuthorizationEndpointURI())
                .state(state)
                .nonce(nonce)
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256);
        if (silent) {
            request.prompt(new Prompt(Prompt.Type.NONE));
        }
        return request.build().toURI();
    }

    /**
     * Redeems the code with the client secret and the PKCE verifier and returns the
     * checked claims of the ID token: signature (Keycloak's keys), issuer, audience
     * containing this client, nonce, expiry and issue time.
     */
    public IDTokenClaimsSet redeem(String code, CodeVerifier codeVerifier, Nonce nonce) throws EskylabLoginException {
        Discovered keycloak = discover();

        HTTPRequest request = new TokenRequest(
                keycloak.metadata().getTokenEndpointURI(),
                new ClientSecretBasic(new ClientID(clientId), new Secret(clientSecret)),
                new AuthorizationCodeGrant(new AuthorizationCode(code), URI.create(redirectUri), codeVerifier))
                .toHTTPRequest();
        request.setConnectTimeout(TIMEOUT_MILLIS);
        request.setReadTimeout(TIMEOUT_MILLIS);

        TokenResponse response;
        try {
            response = OIDCTokenResponseParser.parse(request.send());
        } catch (IOException e) {
            throw new EskylabLoginException("token endpoint unreachable: " + e.getClass().getSimpleName());
        } catch (ParseException e) {
            // The parse error may quote the response, and the response holds the tokens.
            throw new EskylabLoginException("token endpoint answer could not be read");
        }
        if (!response.indicatesSuccess()) {
            var error = response.toErrorResponse().getErrorObject();
            throw new EskylabLoginException("token endpoint refused the code: " + error.getCode() + " (HTTP " + error.getHTTPStatusCode() + ")");
        }

        JWT idToken = ((OIDCTokenResponse) response.toSuccessResponse()).getOIDCTokens().getIDToken();
        if (idToken == null) {
            throw new EskylabLoginException("token endpoint answer has no ID token");
        }
        try {
            return keycloak.idTokenValidator().validate(idToken, nonce);
        } catch (BadJOSEException | JOSEException e) {
            throw new EskylabLoginException("ID token rejected: " + e.getMessage());
        }
    }

    /**
     * The roles the person has on this client, as Keycloak puts them in the token:
     * resource_access.<client id>.roles. Roles of other clients and realm roles are not
     * read. Anything shaped otherwise counts as no roles.
     */
    public Set<String> clientRoles(IDTokenClaimsSet claims) {
        if (claims.getClaim("resource_access") instanceof Map<?, ?> clients
                && clients.get(clientId) instanceof Map<?, ?> client
                && client.get("roles") instanceof Collection<?> roles) {
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private Discovered discover() throws EskylabLoginException {
        Discovered known = discovered;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (discovered == null) {
                discovered = fetchDiscovery();
            }
            return discovered;
        }
    }

    private Discovered fetchDiscovery() throws EskylabLoginException {
        try {
            OIDCProviderMetadata metadata = OIDCProviderMetadata.resolve(new Issuer(issuer), TIMEOUT_MILLIS, TIMEOUT_MILLIS);
            JWKSource<SecurityContext> keys = JWKSourceBuilder
                    .create(metadata.getJWKSetURI().toURL(), new DefaultResourceRetriever(TIMEOUT_MILLIS, TIMEOUT_MILLIS))
                    .build();
            var validator = new IDTokenValidator(metadata.getIssuer(), new ClientID(clientId),
                    new JWSVerificationKeySelector<>(SIGNING_ALGORITHMS, keys), null);
            return new Discovered(metadata, validator);
        } catch (GeneralException | IOException e) {
            throw new EskylabLoginException("Keycloak discovery at " + issuer + " failed: " + e.getMessage());
        }
    }
}
