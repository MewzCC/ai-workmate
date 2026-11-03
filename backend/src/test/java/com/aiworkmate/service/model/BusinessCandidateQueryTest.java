package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class BusinessCandidateQueryTest {
    @ParameterizedTest
    @CsvSource({"0,20", "10001,20", "1,0", "1,101", "-1,20", "2147483647,20"})
    void rejectsPagingOutsideTheDomainBudget(int page, int size) {
        assertThatThrownBy(() -> new BusinessCandidateQuery(null, page, size))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }

    @Test void normalizesLabelsAndCalculatesBoundedOffsets() {
        var query = new BusinessCandidateQuery(" Synthetic candidate ", 10000, 100);
        assertThat(query.keyword()).isEqualTo("Synthetic candidate");
        assertThat(query.offset()).isEqualTo(999900L);
        assertThat(new BusinessCandidateQuery("  ", 1, 20).keyword()).isNull();
        assertThat(new BusinessCandidateQuery(null, 1, 20).offset()).isZero();
        assertThat(new BusinessCandidateQuery("x".repeat(120), 1, 1).keyword()).hasSize(120);
    }

    @Test void rejectsOverlongSearchInsteadOfTruncatingIt() {
        assertThatThrownBy(() -> new BusinessCandidateQuery("x".repeat(121), 1, 20))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
