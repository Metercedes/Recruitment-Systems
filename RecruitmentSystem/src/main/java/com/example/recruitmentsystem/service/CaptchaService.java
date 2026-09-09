package com.example.recruitmentsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    @Value("${app.recaptcha.secret:}")
    private String recaptchaSecret;

    @Value("${app.recaptcha.enabled:false}")
    private boolean recaptchaEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean validateCaptcha(String captchaResponse) {
        if (!recaptchaEnabled) {
            // For development/testing - accept mock token
            return "mock-recaptcha-token".equals(captchaResponse);
        }

        if (recaptchaSecret == null || recaptchaSecret.isEmpty()) {
            log.warn("reCAPTCHA is enabled but no secret is configured; verification is being skipped");
            return true;
        }

        try {
            String url = "https://www.google.com/recaptcha/api/siteverify";
            Map<String, String> params = new HashMap<>();
            params.put("secret", recaptchaSecret);
            params.put("response", captchaResponse);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, params, Map.class);
            
            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (Exception e) {
            System.err.println("reCAPTCHA validation failed: " + e.getMessage());
            return false;
        }
    }
}