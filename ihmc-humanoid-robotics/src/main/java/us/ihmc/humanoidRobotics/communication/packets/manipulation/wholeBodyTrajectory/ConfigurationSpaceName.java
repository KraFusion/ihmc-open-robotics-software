package us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory;

import gnu.trove.list.TByteList;
import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple4D.Quaternion;

public enum ConfigurationSpaceName
{
   X, Y, Z, ROLL, PITCH, YAW, SE3;

   public static final ConfigurationSpaceName[] values = values();

   public double getDefaultExplorationLowerLimit()
   {
      return -getDefaultExplorationAmplitude();
   }

   public double getDefaultExplorationUpperLimit()
   {
      return getDefaultExplorationAmplitude();
   }

   public double getDefaultExplorationAmplitude()
   {
      switch (this)
      {
      case X:
      case Y:
      case Z:
         return 1.0;
      case ROLL:
      case PITCH:
      case YAW:
         return 0.25 * Math.PI;
      case SE3:
         return 1.0;
      default:
         throw new RuntimeException("Unexpected value: " + this);
      }
   }

   /**
    * All configuration value for SE3 should be 0~1.
    */
   public RigidBodyTransform getLocalRigidBodyTransform(double... configuration)
   {
      RigidBodyTransform ret = new RigidBodyTransform();
      
      switch (this)
      {
      case X:
         ret.appendTranslation(configuration[0], 0, 0);
         break;
      case Y:
         ret.appendTranslation(0, configuration[0], 0);
         break;
      case Z:
         ret.appendTranslation(0, 0, configuration[0]);
         break;
      case ROLL:
         ret.appendRollRotation(configuration[0]);
         break;
      case PITCH:
         ret.appendPitchRotation(configuration[0]);
         break;
      case YAW:
         ret.appendYawRotation(configuration[0]);
         break;
      case SE3:
         Quaternion quat = new Quaternion();

         double s = configuration[0];
         double s1 = Math.sqrt(1 - s);
         double s2 = Math.sqrt(s);

         double theta1 = Math.PI * 2 * configuration[1];
         double theta2 = Math.PI * 2 * configuration[2];

         quat.set(Math.sin(theta1) * s1, Math.cos(theta1) * s1, Math.sin(theta2) * s2, Math.cos(theta2) * s2);
         quat.norm();

         ret.transform(quat);
         break;
      }

      return ret;
   }

   public byte toByte()
   {
      return (byte) ordinal();
   }

   public static ConfigurationSpaceName fromByte(byte enumAsByte)
   {
      if (enumAsByte == -1)
         return null;
      return values[enumAsByte];
   }

   public static byte[] toBytes(ConfigurationSpaceName[] enumArray)
   {
      if (enumArray == null)
         return null;
      byte[] byteArray = new byte[enumArray.length];
      for (int i = 0; i < enumArray.length; i++)
         byteArray[i] = enumArray[i].toByte();
      return byteArray;
   }

   public static ConfigurationSpaceName[] fromBytes(TByteList enumListAsBytes)
   {
      if (enumListAsBytes == null)
         return null;
      ConfigurationSpaceName[] enumArray = new ConfigurationSpaceName[enumListAsBytes.size()];
      for (int i = 0; i < enumListAsBytes.size(); i++)
         enumArray[i] = fromByte(enumListAsBytes.get(i));
      return enumArray;
   }

   public static ConfigurationSpaceName[] fromBytes(byte[] enumArrayAsBytes)
   {
      if (enumArrayAsBytes == null)
         return null;
      ConfigurationSpaceName[] enumArray = new ConfigurationSpaceName[enumArrayAsBytes.length];
      for (int i = 0; i < enumArrayAsBytes.length; i++)
         enumArray[i] = fromByte(enumArrayAsBytes[i]);
      return enumArray;
   }
}