package us.ihmc.atlas.generatedTestSuites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import us.ihmc.utilities.code.unitTesting.runner.BambooTestSuiteRunner;

/** WARNING: AUTO-GENERATED FILE. DO NOT MAKE MANUAL CHANGES TO THIS FILE. **/
@RunWith(Suite.class)
@Suite.SuiteClasses
({
   us.ihmc.atlas.AtlasMultiContactTest.class,
   us.ihmc.atlas.AtlasPushRecoveryMultiStepTest.class,
   us.ihmc.atlas.AtlasPushRecoveryStandingTest.class
})

public class AtlasBFastTestSuite
{
   public static void main(String[] args)
   {
      new BambooTestSuiteRunner(AtlasBFastTestSuite.class);
   }
}

