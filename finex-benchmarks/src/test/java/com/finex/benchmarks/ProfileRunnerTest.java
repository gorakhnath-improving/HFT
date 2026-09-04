package com.finex.benchmarks;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finex.loadgenerator.LoadConfig;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileRunnerTest {

    @Test
    void jfrRecordingIsCreated(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("profile.jfr");
        ProfileRunner.ProfileResult result = ProfileRunner.run(LoadConfig.defaults(), output);

        assertThat(result.jfrFile()).isEqualTo(output);
        assertThat(Files.size(output)).isGreaterThan(0);
        assertThat(result.loadResult().submitted()).isGreaterThan(0);
    }
}
