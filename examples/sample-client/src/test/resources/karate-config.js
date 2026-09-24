function fn() {
  var Automation = Java.type('io.github.semihsaydamandroid.automation.api.karate.KarateBridge');
  var config = Automation.config();   // api.* in camelCase (baseUrl, readTimeout...) + env, profile, runId
  karate.configure('connectTimeout', parseInt(config.connectTimeout));
  karate.configure('readTimeout', parseInt(config.readTimeout));
  karate.configure('logPrettyRequest', true);
  return config;
}
