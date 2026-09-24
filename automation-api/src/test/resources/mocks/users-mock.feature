Feature: In-memory users API used by the framework's own tests

  Background:
    * def users = { '1': { id: 1, name: 'Ada' } }
    * def nextId = 2

  Scenario: pathMatches('/users/{id}') && methodIs('get')
    * def user = users[pathParams.id]
    * def responseStatus = user ? 200 : 404
    * def response = user ? user : { error: 'not found' }

  Scenario: pathMatches('/users') && methodIs('post')
    * def user = { id: '#(nextId)', name: '#(request.name)' }
    * users[nextId + ''] = user
    * def nextId = nextId + 1
    * def responseStatus = 201
    * def response = user

  Scenario:
    * def responseStatus = 404
