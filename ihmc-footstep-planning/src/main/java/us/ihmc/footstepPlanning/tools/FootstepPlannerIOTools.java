package us.ihmc.footstepPlanning.tools;

import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.tools.EuclidCoreIOTools;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.footstepPlanning.FootstepPlannerType;
import us.ihmc.pathPlanning.visibilityGraphs.tools.VisibilityGraphsIOTools;
import us.ihmc.robotics.PlanarRegionFileTools;
import us.ihmc.robotics.geometry.PlanarRegionsList;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FootstepPlannerIOTools extends VisibilityGraphsIOTools
{

   private static final String TYPE_FIELD_OPEN = "<Type,";
   private static final String TYPE_FIELD_CLOSE = ",Type>";

   private static final String TIMEOUT_FIELD_OPEN = "<Timeout,";
   private static final String TIMEOUT_FIELD_CLOSE = ",Timeout>";

   public static class FootstepPlannerUnitTestDataset extends VisibilityGraphsIOTools.VisibilityGraphsUnitTestDataset
   {
      private List<FootstepPlannerType> plannerTypes;
      private Double[] timeouts;

      private FootstepPlannerUnitTestDataset(Class<?> clazz, String datasetResourceName)
      {
         super(clazz, datasetResourceName);

         if (plannerTypes == null)
            throw new RuntimeException("Could not load the planner types. Data folder: " + datasetResourceName);
      }

      public List<FootstepPlannerType> getTypes()
      {
         return plannerTypes;
      }

      public double getTimeout(FootstepPlannerType plannerType)
      {
         if (plannerTypes.contains(plannerType))
            return timeouts[plannerTypes.indexOf(plannerType)];

         return Double.NaN;
      }

      @Override
      protected void loadFields(BufferedReader bufferedReader)
      {
         super.loadFields(bufferedReader);

         plannerTypes = parseField(bufferedReader, TYPE_FIELD_OPEN, TYPE_FIELD_CLOSE, FootstepPlannerIOTools::parsePlannerTypes);
         timeouts = parseField(bufferedReader, TIMEOUT_FIELD_OPEN, TIMEOUT_FIELD_CLOSE, FootstepPlannerIOTools::parsePlannerTimeouts);
      }
   }

   public static boolean exportDataset(Path containingFolder, String datasetName, PlanarRegionsList planarRegionsList, Point3DReadOnly start,
                                       Point3DReadOnly goal, FootstepPlannerType type)
   {
      File datasetFolder = new File(containingFolder + File.separator + datasetName);
      if (datasetFolder.exists())
         return false;
      boolean success = datasetFolder.mkdir();
      if (!success)
         return false;

      Path planarRegionsFolder = Paths.get(datasetFolder.getPath() + File.separator + PlanarRegionFileTools.createDefaultTimeStampedFolderName());
      success = PlanarRegionFileTools.exportPlanarRegionData(planarRegionsFolder, planarRegionsList);
      if (!success)
         return false;

      success = exportParameters(datasetFolder, start, goal, type);
      if (!success)
         return false;

      return true;
   }

   private static boolean exportParameters(File containingFolder, Point3DReadOnly start, Point3DReadOnly goal, FootstepPlannerType type)
   {

      if (containingFolder == null || !containingFolder.exists())
      {
         PrintTools.error("The given folder does not exist or is null.");
         return false;
      }

      if (start == null || goal == null || type == null)
      {
         PrintTools.error("Must export start, goal AND planner type.");
         return false;
      }

      File parametersFile = new File(containingFolder.getAbsolutePath() + File.separator + INPUTS_PARAMETERS_FILENAME);
      writeField(parametersFile, START_FIELD_OPEN, START_FIELD_CLOSE, () -> getPoint3DString(start));
      writeField(parametersFile, GOAL_FIELD_OPEN, GOAL_FIELD_END, () -> getPoint3DString(goal));
      writeField(parametersFile, TYPE_FIELD_OPEN, TYPE_FIELD_CLOSE, () -> type.name());
//      writeField(parametersFile, TYPE_FIELD_OPEN, TYPE_FIELD_CLOSE, () -> getFootstepPlannerTypeString(type));

      return true;
   }

   public static List<FootstepPlannerUnitTestDataset> loadAllFootstepPlannerDatasets(Class<?> loadingClass)
   {
      List<String> childDirectories = PlanarRegionFileTools.listResourceDirectoryContents(loadingClass, TEST_DATA_URL);
      List<FootstepPlannerUnitTestDataset> datasets = new ArrayList<>();

      for (int i = 0; i < childDirectories.size(); i++)
      {
         PrintTools.info("trying to load:");
         PrintTools.info(TEST_DATA_URL + "/" + childDirectories.get(i));

         datasets.add(loadDataset(loadingClass, TEST_DATA_URL + "/" + childDirectories.get(i)));
      }

      return datasets;
   }

   public static FootstepPlannerUnitTestDataset loadDataset(Class<?> clazz, String datasetResourceName)
   {
      return new FootstepPlannerUnitTestDataset(clazz, datasetResourceName);
   }


   private static List<FootstepPlannerType> parsePlannerTypes(String stringPlannerTypes)
   {
      List<FootstepPlannerType> footstepPlannerTypes = new ArrayList<>();

      boolean containsData = true;

      while (containsData)
      {
         stringPlannerTypes = parsePlannerType(footstepPlannerTypes, stringPlannerTypes);

         if (stringPlannerTypes == null)
            containsData = false;
      }

      return footstepPlannerTypes;
   }

   private static String parsePlannerType(List<FootstepPlannerType> footstepPlannerTypes, String stringPlannerTypes)
   {
      if (stringPlannerTypes.contains(","))
      {
         footstepPlannerTypes.add(FootstepPlannerType.fromString(stringPlannerTypes.substring(0, stringPlannerTypes.indexOf(","))));
         stringPlannerTypes = stringPlannerTypes.substring(stringPlannerTypes.indexOf(",") + 1);

         while (stringPlannerTypes.startsWith(" "))
            stringPlannerTypes = stringPlannerTypes.replaceFirst(" ", "");

         return stringPlannerTypes;
      }

      return null;
   }

   private static Double[] parsePlannerTimeouts(String stringTimeouts)
   {
      List<Double> footstepPlannerTimeouts = new ArrayList<>();

      boolean containsData = true;

      while (containsData)
      {
         stringTimeouts = parsePlannerTimeout(footstepPlannerTimeouts, stringTimeouts);

         if (stringTimeouts == null)
            containsData = false;
      }

      Double[] typeArray = new Double[footstepPlannerTimeouts.size()];
      footstepPlannerTimeouts.toArray(typeArray);

      return typeArray;
   }

   private static String parsePlannerTimeout(List<Double> footstepPlannerTimeouts, String stringTimeouts)
   {
      if (stringTimeouts.contains(","))
      {
         footstepPlannerTimeouts.add(Double.parseDouble(stringTimeouts.substring(0, stringTimeouts.indexOf(","))));
         stringTimeouts = stringTimeouts.substring(stringTimeouts.indexOf(",") + 1);

         while (stringTimeouts.startsWith(" "))
            stringTimeouts = stringTimeouts.replaceFirst(" ", "");

         return stringTimeouts;
      }

      return null;
   }

}