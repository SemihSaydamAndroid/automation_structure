@ignore
Feature: Reusable OAuth2 client-credentials token request (shipped with automation-api)

  # Usage from any client feature:
  #   * def auth = call read('classpath:automation/common/auth/oauth2-client-credentials.feature') { tokenUrl: '#(tokenUrl)', clientId: 'my-client', clientSecret: '#(secret)', scope: 'read' }
  #   * header Authorization = 'Bearer ' + auth.accessToken
  # Wrap it in karate.callSingle() to fetch the token once per test run.

  Scenario:
    * def fields = { grant_type: 'client_credentials', client_id: '#(clientId)', client_secret: '#(clientSecret)' }
    * def requestedScope = karate.get('scope')
    * if (requestedScope) fields.scope = requestedScope
    Given url tokenUrl
    And form fields fields
    When method post
    Then status 200
    * def accessToken = response.access_token
    * def expiresIn = response.expires_in
