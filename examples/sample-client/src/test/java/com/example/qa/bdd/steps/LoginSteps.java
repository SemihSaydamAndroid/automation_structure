package com.example.qa.bdd.steps;

import com.example.qa.ui.pages.LoginPage;

import io.cucumber.java.en.Given;
import io.github.semihsaydamandroid.automation.bdd.support.ScenarioContext;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.ui.driver.DriverManager;

/**
 * Domain sentences of this project. One business step hides many UI actions and reuses page
 * objects, which keeps features readable and resilient; the framework's generic sentences stay
 * available for everything else.
 */
public class LoginSteps {

    private final ScenarioContext context;

    public LoginSteps(ScenarioContext context) {
        this.context = context;
    }

    @Given("I am logged in as {string}")
    @Given("{string} kullanıcısı ile giriş yapmış olayım")
    public void loggedInAs(String user) {
        // Passwords come from configuration/secrets, never from feature files.
        String password = AutomationConfig.get().get("users." + user + ".password");
        new LoginPage(DriverManager.start()).open().loginAs(user, password);
        context.set("currentUser", user);
    }
}
