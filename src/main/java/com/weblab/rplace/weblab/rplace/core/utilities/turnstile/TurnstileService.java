package com.weblab.rplace.weblab.rplace.core.utilities.turnstile;

import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TurnstileService {

    private final String SECRET_KEY = "SECRET_KEY";
    private final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    public boolean verifyToken(String token) {
        if (token == null || token.isEmpty()) return false;

        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("secret", SECRET_KEY);
        map.add("response", token);

        try {
            Map response = restTemplate.postForObject(VERIFY_URL, map, Map.class);
            return (Boolean) response.get("success");
        } catch (Exception e) {
            return false;
        }
    }
}