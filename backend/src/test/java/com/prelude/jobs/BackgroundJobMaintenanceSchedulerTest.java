package com.prelude.jobs;

import com.prelude.jobs.infrastructure.BackgroundJobMaintenanceScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BackgroundJobMaintenanceSchedulerTest {

    @Test
    void publicationResubmissionIsBoundedByBatchAndInFlightLimits() {
        var recoveryService = mock(BackgroundJobRecoveryService.class);
        var publications = mock(IncompleteEventPublications.class);
        var scheduler = new BackgroundJobMaintenanceScheduler(recoveryService, publications);

        scheduler.resubmitIncompletePublications();

        var captor = ArgumentCaptor.forClass(ResubmissionOptions.class);
        verify(publications).resubmitIncompletePublications(captor.capture());
        var options = captor.getValue();
        assertThat(options.getMinAge()).isEqualTo(Duration.ofMinutes(2));
        assertThat(options.getBatchSize()).isEqualTo(100);
        assertThat(options.getMaxInFlight()).isEqualTo(100);
    }
}
