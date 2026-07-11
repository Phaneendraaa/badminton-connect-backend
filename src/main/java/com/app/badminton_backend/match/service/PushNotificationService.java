package com.app.badminton_backend.match.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends push notifications to Expo's push service.
 *
 * No Expo SDK required — Expo exposes a simple HTTP endpoint:
 *   POST https://exp.host/--/api/v2/push/send
 * that accepts a JSON body and handles iOS APNs + Android FCM routing.
 *
 * Calls are @Async so they don't block the request thread. A failed push
 * never rolls back an in-progress transaction (we just log the error).
 *
 * Usage in other services:
 *   pushNotificationService.sendPush(user.getPushToken(), "Title", "Body", dataMap);
 * — pass null token to silently no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    @org.springframework.beans.factory.annotation.Value("${expo.push_url}")
    private String expoPushUrl;

    private final RestTemplate restTemplate;

    /**
     * Sends a single push notification to the given Expo push token.
     *
     * @param expoPushToken  the device token ("ExponentPushToken[...]"). If null/blank, this is a no-op.
     * @param title          notification title (bold first line)
     * @param body           notification body text
     * @param data           arbitrary key-value pairs the frontend receives in the notification response;
     *                       used for deep-linking (e.g. { "type": "JOIN_REQUEST", "postId": "..." })
     */
    @Async
    public void sendPush(String expoPushToken, String title, String body, Map<String, Object> data) {
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return; // Permission denied or user hasn't registered — silently skip
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("to", expoPushToken);
            message.put("title", title);
            message.put("body", body);
            message.put("sound", "default");
            message.put("channelId", "default");
            if (data != null && !data.isEmpty()) {
                message.put("data", data);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip, deflate");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            restTemplate.postForObject(expoPushUrl, request, String.class);

            log.debug("[Push] Sent '{}' to token ...{}", title,
                    expoPushToken.length() > 10 ? expoPushToken.substring(expoPushToken.length() - 10) : expoPushToken);

        } catch (Exception ex) {
            // Non-fatal: log and continue. A failed push must never break the main flow.
            log.warn("[Push] Failed to send notification '{}': {}", title, ex.getMessage());
        }
    }
}
