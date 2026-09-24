// Shared helpers shipped with automation-api.
// Usage: * def utils = call read('classpath:automation/common/utils.js')
function fn() {
  var Bridge = Java.type('io.github.semihsaydamandroid.automation.api.karate.KarateBridge');
  var UUID = Java.type('java.util.UUID');
  var Instant = Java.type('java.time.Instant');
  var Base64 = Java.type('java.util.Base64');
  var JString = Java.type('java.lang.String');
  var Thread = Java.type('java.lang.Thread');
  return {
    uuid: function () { return UUID.randomUUID() + ''; },
    unique: function (prefix) { return Bridge.unique(prefix); },
    nowIso: function () { return Instant.now().toString(); },
    basicAuth: function (user, password) {
      var bytes = new JString(user + ':' + password).getBytes('UTF-8');
      return 'Basic ' + Base64.getEncoder().encodeToString(bytes);
    },
    sleep: function (millis) { Thread.sleep(millis); }
  };
}
