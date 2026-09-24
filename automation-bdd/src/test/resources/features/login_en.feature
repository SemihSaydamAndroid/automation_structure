@ui
Feature: Login with ready-made sentences

  Elements are found by what a user sees (placeholder, button text); no locators needed.

  Scenario Outline: A user signs in
    Given I open "/login.html"
    When I type "<user>" into "Username"
    And I type "#{Internet.password}" into "Password"
    And I click "Sign in"
    Then I should see "Welcome, <user>"
    And the page title should be "Login"

    Examples:
      | user  |
      | semih |
      | ayşe  |

  Scenario: Values can be remembered and reused
    Given I open "/login.html"
    When I fill in:
      | Username | #{unique:qa} |
      | Password | s3cret       |
    And I click "Sign in"
    And I remember the text of "greeting" as "message"
    Then I should see "${message}"
