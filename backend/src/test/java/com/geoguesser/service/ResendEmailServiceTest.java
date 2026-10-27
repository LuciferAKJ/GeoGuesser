package com.geoguesser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoguesser.exception.EmailDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendEmailServiceTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Primary Spring constructor is annotated with @Autowired to prevent NoSuchMethodException")
    void testPrimaryConstructorHasAutowiredAnnotation() throws NoSuchMethodException {
        Constructor<ResendEmailService> autowiredConstructor = ResendEmailService.class.getConstructor(
                String.class, String.class, String.class, boolean.class, ObjectMapper.class
        );
        assertNotNull(autowiredConstructor);
        assertTrue(autowiredConstructor.isAnnotationPresent(Autowired.class),
                "Primary constructor must be annotated with @Autowired for Spring container instantiation");
    }

    @Test
    @DisplayName("Primary constructor instantiates bean correctly with default HttpClient")
    void testPrimaryConstructorInstantiation() {
        ResendEmailService service = new ResendEmailService(
                "re_test_key_123",
                "GeoGuesser <test@resend.dev>",
                "https://geoguesser.example.com",
                true,
                objectMapper
        );

        assertTrue(service.isConfigured());
        assertTrue(service.isVerificationRequired());
    }

    @Test
    @DisplayName("isConfigured returns false when API key is empty or blank")
    void testIsConfiguredWithEmptyKey() {
        ResendEmailService service = new ResendEmailService(
                "",
                "GeoGuesser <test@resend.dev>",
                "http://localhost:5500",
                true,
                objectMapper,
                mockHttpClient
        );

        assertFalse(service.isConfigured());
        assertTrue(service.isVerificationRequired());
    }

    @Test
    @DisplayName("isConfigured returns false when frontendBaseUrl is empty or blank")
    void testIsConfiguredWithEmptyFrontendBaseUrl() {
        ResendEmailService service = new ResendEmailService(
                "re_test_key_123",
                "GeoGuesser <test@resend.dev>",
                "",
                true,
                objectMapper,
                mockHttpClient
        );

        assertFalse(service.isConfigured());
        assertTrue(service.isVerificationRequired());
    }

    @Test
    @DisplayName("sendVerificationEmail throws IllegalStateException if API key is missing when verification is required")
    void testSendVerificationEmailFailsWhenUnconfigured() {
        ResendEmailService service = new ResendEmailService(
                "",
                "GeoGuesser <test@resend.dev>",
                "http://localhost:5500",
                true,
                objectMapper,
                mockHttpClient
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.sendVerificationEmail("player@example.com", "explorer", "raw-token-123")
        );
        assertTrue(ex.getMessage().contains("Email service is not configured"));
    }

    @Test
    @DisplayName("sendVerificationEmail skips sending when verificationRequired is false")
    void testSendVerificationEmailSkipsWhenDisabled() {
        ResendEmailService service = new ResendEmailService(
                "re_key",
                "GeoGuesser <test@resend.dev>",
                "http://localhost:5500",
                false,
                objectMapper,
                mockHttpClient
        );

        assertDoesNotThrow(() ->
                service.sendVerificationEmail("player@example.com", "explorer", "raw-token-123")
        );
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @DisplayName("sendVerificationEmail successfully sends HTTP POST request to Resend API")
    void testSendVerificationEmailSuccess() throws IOException, InterruptedException {
        ResendEmailService service = new ResendEmailService(
                "re_test_secret",
                "GeoGuesser <test@resend.dev>",
                "https://geoguesser.example.com",
                true,
                objectMapper,
                mockHttpClient
        );

        when(mockHttpResponse.statusCode()).thenReturn(200);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        assertDoesNotThrow(() ->
                service.sendVerificationEmail("player@example.com", "JaneDoe", "tokenABC123")
        );

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());

        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("POST", sentRequest.method());
        assertEquals("https://api.resend.com/emails", sentRequest.uri().toString());
        assertTrue(sentRequest.headers().firstValue("Authorization").orElse("").contains("re_test_secret"));
    }

    @Test
    @DisplayName("sendVerificationEmail throws EmailDeliveryException when Resend API returns non-2xx status")
    void testSendVerificationEmailApiError() throws IOException, InterruptedException {
        ResendEmailService service = new ResendEmailService(
                "re_test_secret",
                "GeoGuesser <test@resend.dev>",
                "https://geoguesser.example.com",
                true,
                objectMapper,
                mockHttpClient
        );

        when(mockHttpResponse.statusCode()).thenReturn(403);
        when(mockHttpResponse.body()).thenReturn("{\"message\":\"Domain not verified\"}");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () ->
                service.sendVerificationEmail("player@example.com", "JaneDoe", "tokenABC123")
        );

        assertTrue(ex.getMessage().contains("Domain not verified"));
    }
}
