Feature: Users API

  Background:
    * url baseUrl
    * def utils = call read('classpath:automation/common/utils.js')

  @smoke
  Scenario: A user can be fetched
    Given path 'users', 1
    When method get
    Then status 200
    And match response contains { id: 1, name: '#string', email: '#string' }

  Scenario: A post can be created
    * def title = utils.unique('post')
    Given path 'posts'
    And request { title: '#(title)', body: 'created by automation', userId: 1 }
    When method post
    Then status 201
    And match response == { id: '#number', title: '#(title)', body: '#string', userId: 1 }

  Scenario Outline: Unknown resources return 404: <resource>
    Given path '<resource>', 999999
    When method get
    Then status 404

    Examples:
      | resource |
      | users    |
      | posts    |
