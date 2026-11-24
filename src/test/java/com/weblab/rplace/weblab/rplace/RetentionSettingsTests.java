package com.weblab.rplace.weblab.rplace;

import com.weblab.rplace.weblab.rplace.core.retention.RetentionMode;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RETENTION_SWEEP_MODE and the brake: a value the run cannot use stops the start; a bad EVENT_ENDED_AT is ignored. */
class RetentionSettingsTests {

	@Test
	void theModeIsDryRunUnlessSetAndAnUnknownModeStopsTheStart() {
		assertThat(new RetentionSettings("", "", 50000, 500).mode()).isEqualTo(RetentionMode.DRY_RUN);
		assertThat(new RetentionSettings(null, "", 50000, 500).mode()).isEqualTo(RetentionMode.DRY_RUN);
		assertThat(new RetentionSettings(" Apply ", "", 50000, 500).mode()).isEqualTo(RetentionMode.APPLY);
		assertThat(new RetentionSettings("off", "", 50000, 500).mode()).isEqualTo(RetentionMode.OFF);
		assertThatThrownBy(() -> new RetentionSettings("dryrun", "", 50000, 500))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RETENTION_SWEEP_MODE");
	}

	@Test
	void theEventEndIsADateInTurkeyOrATimeWithItsOffsetOrIgnored() {
		assertThat(new RetentionSettings("dry-run", "", 50000, 500).eventEndedAt()).isNull();
		// The end of 18 May in Turkey (UTC+3).
		assertThat(new RetentionSettings("dry-run", "2025-05-18", 50000, 500).eventEndedAt())
				.isEqualTo(Instant.parse("2025-05-18T21:00:00Z"));
		assertThat(new RetentionSettings("dry-run", "2025-05-18T22:00:00+03:00", 50000, 500).eventEndedAt())
				.isEqualTo(Instant.parse("2025-05-18T19:00:00Z"));
		// Optional: a value that is not a date is logged, the application still starts; apply then changes nothing.
		assertThat(new RetentionSettings("dry-run", "18.05.2025", 50000, 500).eventEndedAt()).isNull();
		assertThat(new RetentionSettings("dry-run", "18.05.2025", 50000, 500).eventEndInvalid()).isTrue();
		assertThat(new RetentionSettings("dry-run", "", 50000, 500).eventEndInvalid()).isFalse();
		assertThat(new RetentionSettings("dry-run", "2025-05-18", 50000, 500).eventEndInvalid()).isFalse();
	}

	@Test
	void theBrakeAndTheBatchArePositive() {
		assertThatThrownBy(() -> new RetentionSettings("apply", "", 0, 500))
				.hasMessageContaining("RETENTION_SWEEP_MAX_ROWS");
		assertThatThrownBy(() -> new RetentionSettings("apply", "", 50000, 0))
				.hasMessageContaining("RETENTION_SWEEP_BATCH_SIZE");
	}
}
