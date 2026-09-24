package com.example.qa.bdd;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/** Glue, plugins and parallelism are configured in junit-platform.properties. */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features.bdd")
public class RunCucumberTest {
}
