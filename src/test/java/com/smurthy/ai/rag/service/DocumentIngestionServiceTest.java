package com.smurthy.ai.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentIngestionService.extractPersonName().
 *
 * extractPersonName() is static and package-private, so this test lives in the
 * same package. No Spring context needed — these run in milliseconds.
 */
class DocumentIngestionServiceTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "alice_smith_resume.pdf,  Alice Smith",
        "alice_smith_resume.txt,  Alice Smith",
        "bob-jones-cv.pdf,        Bob Jones",
        "JohnDoe.pdf,             John Doe",
        "jane_doe_profile.pdf,    Jane Doe",
        "mary-ann-jones-resume.pdf, Mary Ann Jones",
    })
    void extractPersonName_commonConventions(String fileName, String expected) {
        assertThat(DocumentIngestionService.extractPersonName(fileName.strip()))
            .isEqualTo(expected.strip());
    }

    @Test
    void extractPersonName_resumeOnlyFilename_returnsNull() {
        // "resume.pdf" strips to empty after removing the resume suffix
        assertThat(DocumentIngestionService.extractPersonName("resume.pdf")).isNull();
    }

    @Test
    void extractPersonName_cvOnlyFilename_returnsNull() {
        assertThat(DocumentIngestionService.extractPersonName("cv.pdf")).isNull();
    }

    @ParameterizedTest
    @NullSource
    void extractPersonName_nullInput_returnsNull(String fileName) {
        assertThat(DocumentIngestionService.extractPersonName(fileName)).isNull();
    }

    @Test
    void extractPersonName_camelCaseWithResumeSuffix_parsedCorrectly() {
        // _Resume stripped first, then CamelCase regex splits "AliceSmith" → "Alice Smith"
        assertThat(DocumentIngestionService.extractPersonName("AliceSmith_Resume.pdf"))
            .isEqualTo("Alice Smith");
    }

    @Test
    void extractPersonName_singleWordName_titleCased() {
        // A single word (no separator, no CamelCase) is title-cased as-is
        assertThat(DocumentIngestionService.extractPersonName("alice.pdf"))
            .isEqualTo("Alice");
    }
}
