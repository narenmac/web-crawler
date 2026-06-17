package com.webcrawler.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CurrentUserProvider {

    @Value("${app.security.local-user-id:local-dev-user}")
    private String localUserId;

    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return localUserId;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            if (StringUtils.hasText(jwt.getClaimAsString("preferred_username"))) {
                return jwt.getClaimAsString("preferred_username");
            }
            if (StringUtils.hasText(jwt.getClaimAsString("oid"))) {
                return jwt.getClaimAsString("oid");
            }
        }

        return StringUtils.hasText(authentication.getName()) ? authentication.getName() : localUserId;
    }
}
