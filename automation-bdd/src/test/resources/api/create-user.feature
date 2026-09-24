@ignore
Feature: Reusable API call used from Cucumber (arguments: name)

  Scenario:
    Given url baseUrl
    And path 'users'
    And request { name: '#(name)' }
    When method post
    Then status 201
    * def userId = response.id
    * def userName = response.name
    * def status = responseStatus
