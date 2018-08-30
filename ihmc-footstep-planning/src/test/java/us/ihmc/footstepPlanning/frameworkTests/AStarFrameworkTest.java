package us.ihmc.footstepPlanning.frameworkTests;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Before;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.continuousIntegration.ContinuousIntegrationAnnotations.ContinuousIntegrationPlan;
import us.ihmc.continuousIntegration.ContinuousIntegrationTools;
import us.ihmc.continuousIntegration.IntegrationCategory;
import us.ihmc.footstepPlanning.FootstepPlannerType;
import us.ihmc.footstepPlanning.frameworkTests.FootstepPlannerFrameworkTest;
import us.ihmc.footstepPlanning.ui.StandaloneFootstepPlannerUI;
import us.ihmc.footstepPlanning.ui.StandaloneFootstepPlannerUILauncher;

@ContinuousIntegrationPlan(categories = IntegrationCategory.FAST)
public class AStarFrameworkTest extends FootstepPlannerFrameworkTest
{
   @Override
   public FootstepPlannerType getPlannerType()
   {
      return FootstepPlannerType.A_STAR;
   }


   @Before
   public void setup()
   {
      VISUALIZE = VISUALIZE && !ContinuousIntegrationTools.isRunningOnContinuousIntegrationServer();


      StandaloneFootstepPlannerUILauncher launcher = new StandaloneFootstepPlannerUILauncher(VISUALIZE);
      PlatformImpl.startup(() -> {
         Platform.runLater(new Runnable()
         {
            @Override
            public void run()
            {
               try
               {
                  launcher.start(new Stage());
               }
               catch (Exception e)
               {
                  e.printStackTrace();
               }
            }
         });
      });

      while (launcher.getUI() == null)
         ThreadTools.sleep(100);

      ui = launcher.getUI();
   }

   @After
   public void tearDown() throws Exception
   {
      ui.stop();
      ui = null;
   }

}
