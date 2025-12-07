package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

public class OAuth2AuthCodePKCE {
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String redirectUri;
    private final String[] scopes;

    private final AuthorizationService authService;
    private AuthorizationServiceConfiguration serviceConfig;
    private TokenResponse tokenResponse;

    public OAuth2AuthCodePKCE(Context context,
                              String authorizationUrl,
                              String tokenUrl,
                              String clientId,
                              String redirectUri,
                              String[] scopes) {
        this.authorizationUrl = authorizationUrl;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.authService = new AuthorizationService(context);
    }

    public void startAuth(Activity activity) {
        serviceConfig = new AuthorizationServiceConfiguration(
                Uri.parse(authorizationUrl),
                Uri.parse(tokenUrl)
        );

        AuthorizationRequest authRequest = new AuthorizationRequest.Builder(
                serviceConfig,
                clientId,
                "code",
                Uri.parse(redirectUri)
        )
                .setScopes(scopes)
                .build();

        Intent authIntent = authService.getAuthorizationRequestIntent(authRequest);
        activity.startActivityForResult(authIntent, 1001);
    }

    public void handleAuthResponse(Intent intent, Callback<TokenResponse, Exception> callback) {
        AuthorizationResponse resp = AuthorizationResponse.fromIntent(intent);
        AuthorizationException ex = AuthorizationException.fromIntent(intent);
        if (resp != null) {
            authService.performTokenRequest(resp.createTokenExchangeRequest(),
                    (response, ex2) -> {
                        if (ex2 != null) callback.onError(ex2);
                        else {
                            tokenResponse = response;
                            callback.onSuccess(response);
                        }
                    });
        } else if (ex != null) {
            callback.onError(ex);
        }
    }

    public String getAccessToken() {
        return tokenResponse != null ? tokenResponse.accessToken : null;
    }

//    public void refreshAccessToken(Callback<TokenResponse> callback) {
//        if (tokenResponse == null || tokenResponse.refreshToken == null) {
//            callback.onError(new Exception("No refresh token"));
//            return;
//        }
//
//        TokenRequest request = tokenResponse.createRefreshRequest();
//        authService.performTokenRequest(request, (response, ex) -> {
//            if (ex != null) callback.onError(ex);
//            else {
//                tokenResponse = response;
//                callback.onSuccess(response);
//            }
//        });
//    }

    public interface Callback<T, E> {
        void onSuccess(T result);
        void onError(E e);
    }
}