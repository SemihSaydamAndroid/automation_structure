package com.example.qa.ui.pages;

import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.element.UiElement;
import io.github.semihsaydamandroid.automation.ui.page.BasePage;

/** Page object: locators carry a human description (logs, reports, self-healing). */
public class LoginPage extends BasePage {

    private final UiElement username = $(Locator.id("username", "Username field"));
    private final UiElement password = $(Locator.id("password", "Password field"));
    private final UiElement login = $(Locator.css("button[type=submit]", "Login button"));
    private final UiElement flash = $(Locator.id("flash", "Notification"));

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public LoginPage open() {
        open("/login");
        return this;
    }

    public SecureAreaPage loginAs(String user, String pass) {
        username.type(user);
        password.type(pass);
        login.click();
        return new SecureAreaPage(driver);
    }

    public LoginPage loginExpectingError(String user, String pass) {
        username.type(user);
        password.type(pass);
        login.click();
        return this;
    }

    public UiElement notification() {
        return flash;
    }
}
