package io.github.drjkn7.shoestep.data.model

// Stored in the database by name; never rename or remove a value without a migration.

enum class ShoeType { ROAD_RUNNING, TRAIL_RUNNING, HIKING, WALKING, GYM_TRAINING, CASUAL, FORMAL_WORK, OTHER }

enum class SessionState { RUNNING, PAUSED, ENDED }

enum class EndReason { USER, SWITCH }

enum class StepsStatus { PENDING, PROVISIONAL, FINAL, UNAVAILABLE }
