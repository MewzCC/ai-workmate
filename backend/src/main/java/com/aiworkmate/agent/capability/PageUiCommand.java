package com.aiworkmate.agent.capability;

/**
 * UI-only commands may change the current page state, but never execute a
 * domain side effect. Business writes must always use a registered Agent tool.
 */
public enum PageUiCommand {
    NAVIGATE("ui.navigate"),
    APPLY_FILTER("ui.applyFilter"),
    OPEN_DETAIL("ui.openDetail"),
    OPEN_CREATE_FORM("ui.openCreateForm"),
    FILL_FORM("ui.fillForm"),
    PREVIEW_SUBMISSION("ui.previewSubmission"),
    REFRESH_PAGE("ui.refreshPage");

    private final String code;

    PageUiCommand(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
