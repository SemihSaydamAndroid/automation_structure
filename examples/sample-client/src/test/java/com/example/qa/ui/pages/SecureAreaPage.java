package com.example.qa.ui.pages;

import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.element.UiElement;
import io.github.semihsaydamandroid.automation.ui.page.BasePage;

public class SecureAreaPage extends BasePage {

    private final UiElement flash = $(Locator.id("flash", "Notification"));
    private final UiElement logout = $(Locator.css("a[href='/logout']", "Logout link"));

    public SecureAreaPage(WebDriver driver) {
        super(driver);
    }

    public UiElement notification() {
        return flash;
    }

    public LoginPage logout() {
        logout.click();
        return new LoginPage(driver);
    }
}
