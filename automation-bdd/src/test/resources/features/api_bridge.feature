@api
Feature: Cucumber scenarios can prepare and verify state through Karate

  Scenario: Create a user through the API and use the result
    When I call API feature "classpath:api/create-user.feature" with:
      | name | #{Name.firstName} |
    Then the API result "userId" should not be empty
    And the API result "status" should be "201"

  Scenario: The same bridge with Turkish sentences
    * "classpath:api/create-user.feature" API senaryosunu aşağıdaki değerlerle çağırırım:
      | name | Ada |
    * API sonucu "userName" değeri "Ada" olmalı
