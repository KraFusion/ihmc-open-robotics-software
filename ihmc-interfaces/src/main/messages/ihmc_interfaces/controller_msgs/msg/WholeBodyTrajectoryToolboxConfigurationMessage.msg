# Message used to configure the IHMC whole-body trajetory planner.
# Main usage is the IHMC WholeBodyTrajectoryToolbox.

# Unique ID used to identify this message, should preferably be consecutively increasing.
uint32 sequence_id

int32 number_of_initial_guesses -1
int32 maximum_expansion_size -1
KinematicsToolboxOutputStatus initial_configuration