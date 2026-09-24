package com.example.qa.ui;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import com.example.qa.ui.pages.LoginPage;

import io.github.semihsaydamandroid.automation.ui.junit.UiTest;

/**
 * Where the browser runs is configuration, not code: local Chrome on a laptop, a pod per test in
 * CI (automation-ci.properties), or -Dui.execution=kubernetes locally.
 */
@UiTest
class LoginUiTest {

    @Test
    void validUserReachesTheSecureArea(WebDriver driver) {
        new LoginPage(driver).open()
                .loginAs("tomsmith", "SuperSecretPassword!")
                .notification().shouldContainText("You logged into a secure area!");
    }

    @Test
    void invalidPasswordIsRejected(WebDriver driver) {
        new LoginPage(driver).open()
                .loginExpectingError("tomsmith", "wrong")
                .notification().shouldContainText("Your password is invalid!");
    }
}
