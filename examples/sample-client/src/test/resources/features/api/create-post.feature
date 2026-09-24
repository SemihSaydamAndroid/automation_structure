@ignore
Feature: Reusable call (from Karate with call read(...) or from Cucumber API steps). Arguments: title

  Scenario:
    Given url baseUrl
    And path 'posts'
    And request { title: '#(title)', body: 'from cucumber', userId: 1 }
    When method post
    Then status 201
    * def postId = response.id
