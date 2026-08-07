package me.rerere.rikkahub.service

/**
 * Maximum successful-run history the current cron worker retains for a job.
 *
 * `max_runs` must not exceed this value because completion is derived from retained
 * successful run rows. A larger target could never be observed once older successes are
 * trimmed, causing the recurring job to continue indefinitely.
 */
internal const val MAX_HISTORY_RETENTION = 100
