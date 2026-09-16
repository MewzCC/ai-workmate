package com.aiworkmate.oa.page;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OaPageTest {

    @Test
    void keepsRouteAndComponentPairsUniqueAndRejectsRemapping() {
        assertThat(OaPage.values()).hasSize(41);
        assertThat(Arrays.stream(OaPage.values()).map(OaPage::routeKey)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(OaPage.values()).map(OaPage::componentKey)).doesNotHaveDuplicates();
        assertThat(OaPage.supportsEnabledRoute("meeting-room", "MEETING_ROOM")).isTrue();
        assertThat(OaPage.supportsEnabledRoute("meeting-room", "ASSET_LEDGER")).isFalse();
        assertThat(OaPage.supportsEnabledRoute("unknown-page", "MEETING_ROOM")).isFalse();
    }
}
