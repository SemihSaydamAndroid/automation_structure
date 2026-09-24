Feature: Secure area login

  @ui
  Scenario: Elements found by what users see, no locators
    Given I open "/login"
    When I type "tomsmith" into "username"
    And I type "SuperSecretPassword!" into "password"
    And I click "Login"
    Then I should see "You logged into a secure area!"

  @api
  Scenario: Hybrid - prepare data through the API, then continue
    When I call API feature "classpath:features/api/create-post.feature" with:
      | title | #{unique:cucumber} |
    Then the API result "postId" should not be empty
