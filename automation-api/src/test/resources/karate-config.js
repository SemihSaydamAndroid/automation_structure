function fn() {
  var Automation = Java.type('io.github.semihsaydamandroid.automation.api.karate.KarateBridge');
  var config = Automation.config();
  karate.configure('connectTimeout', parseInt(config.connectTimeout));
  karate.configure('readTimeout', parseInt(config.readTimeout));
  return config;
}
