package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobListener
import net.nemerosa.ontrack.job.JobRunProgress
import net.nemerosa.ontrack.job.JobStatus
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.support.ApplicationLogEntry
import net.nemerosa.ontrack.model.support.ApplicationLogService
import net.nemerosa.ontrack.model.support.SettingsRepository
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

open class DefaultJobListener(
        private val logService: ApplicationLogService,
        private val meterRegistry: MeterRegistry,
        private val settingsRepository: SettingsRepository
) : JobListener {

    private val JobKey.metricTags: List<Tag>
        get() = listOf(
                Tag.of("job-id", id),
                Tag.of("job-type", type.key),
                Tag.of("job-category", type.category.key)
        )

    override fun onJobStart(key: JobKey) {
    }

    @Transactional
    override fun onJobPaused(key: JobKey) {
        settingsRepository.setBoolean(JobListener::class.java, key.toString(), true)
    }

    @Transactional
    override fun onJobResumed(key: JobKey) {
        settingsRepository.delete(JobListener::class.java, key.toString())
    }

    override fun onJobEnd(key: JobKey, milliseconds: Long) {
        meterRegistry.timer("job-duration", key.metricTags).record(milliseconds, TimeUnit.MILLISECONDS)
    }

    override fun onJobError(status: JobStatus, ex: Exception) {
        val key = status.key
        meterRegistry.counter("job-error", key.metricTags).increment()
        logService.log(
                ApplicationLogEntry.error(
                        ex,
                        NameDescription.nd(
                                key.type.toString(),
                                key.type.name
                        ),
                        status.description
                ).withDetail("job.key", key.id)
                        .withDetail("job.progress", status.progressText)
        )
    }

    override fun onJobComplete(key: JobKey) {
    }

    override fun onJobProgress(key: JobKey, progress: JobRunProgress) {}

    override fun isPausedAtStartup(key: JobKey): Boolean {
        return settingsRepository.getBoolean(
                JobListener::class.java,
                key.toString(),
                false
        )
    }
}