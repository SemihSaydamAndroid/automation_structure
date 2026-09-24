Feature: Users API

  Background:
    * url baseUrl
    * def utils = call read('classpath:automation/common/utils.js')

  @smoke
  Scenario: an existing user can be fetched
    Given path 'users', 1
    When method get
    Then status 200
    And match response == { id: 1, name: 'Ada' }

  Scenario: a user can be created
    * def name = utils.unique('user')
    Given path 'users'
    And request { name: '#(name)' }
    When method post
    Then status 201
    And match response == { id: '#number', name: '#(name)' }

  Scenario: an unknown user is reported as missing
    Given path 'users', 999
    When method get
    Then status 404
