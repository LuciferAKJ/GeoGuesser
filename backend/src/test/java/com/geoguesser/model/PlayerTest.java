package com.geoguesser.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    @DisplayName("Default 3-arg constructor initializes player as unverified (isVerified = false)")
    void testDefaultConstructorSetsUnverified() {
        Player player = new Player("alex_test", "alex@example.com", "hashed_pw");

        assertNotNull(player.getIsVerified(), "isVerified should not be null");
        assertFalse(player.getIsVerified());
        assertFalse(player.isAccountVerified(), "New player should not be verified by default");
    }

    @Test
    @DisplayName("4-arg constructor correctly sets verified status for legacy grandfathered accounts")
    void testConstructorWithVerifiedStatus() {
        Player verifiedPlayer = new Player("legacy_pro", "pro@example.com", "hashed_pw", true);
        assertTrue(verifiedPlayer.getIsVerified());
        assertTrue(verifiedPlayer.isAccountVerified());

        Player unverifiedPlayer = new Player("new_user", "new@example.com", "hashed_pw", false);
        assertFalse(unverifiedPlayer.getIsVerified());
        assertFalse(unverifiedPlayer.isAccountVerified());
    }

    @Test
    @DisplayName("isAccountVerified returns false when isVerified is false, and true when true or legacy null")
    void testIsAccountVerifiedNullSafety() {
        Player player = new Player();
        player.setIsVerified(false);
        assertFalse(player.isAccountVerified(), "False isVerified must evaluate to false");

        player.setIsVerified(null);
        assertTrue(player.isAccountVerified(), "Null isVerified gracefully treats legacy account as verified");

        player.setIsVerified(true);
        assertTrue(player.isAccountVerified(), "True isVerified must evaluate to true");
    }

    @Test
    @DisplayName("prePersist sets createdAt if null")
    void testPrePersist() {
        Player player = new Player("test_user", "test@example.com", "pw");
        assertNull(player.getCreatedAt());

        player.prePersist();
        assertNotNull(player.getCreatedAt());
    }
}
