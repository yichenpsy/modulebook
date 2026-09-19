async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();

    if (!response.ok) {
        throw new Error(text || `${response.status} ${response.statusText}`);
    }

    return text ? JSON.parse(text) : null;
}

async function loadStudyPrograms() {
    return requestJson("/api/study-programs");
}

async function createStudyProgram(request) {
    return requestJson("/api/study-programs", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });
}

async function deleteStudyProgram(studyProgramId) {
    await requestJson(`/api/study-programs/${studyProgramId}`, {
        method: "DELETE"
    });
}

async function loadAllModules() {
    return requestJson("/api/modules");
}

async function loadModulesForStudyProgram(studyProgramId) {
    return requestJson(`/api/study-programs/${studyProgramId}/modules`);
}

async function loadModule(moduleId) {
    return requestJson(`/api/modules/${moduleId}`);
}

async function loadStudyProgramChangeSets(studyProgramId) {
    return requestJson(`/api/study-programs/${studyProgramId}/change-sets`);
}

async function loadModulebookTags(studyProgramId) {
    return requestJson(`/api/study-programs/${studyProgramId}/modulebook-tags`);
}

async function loadModulebookTag(tagId) {
    return requestJson(`/api/modulebook-tags/${tagId}`);
}

async function createModulebookTag(studyProgramId, request) {
    return requestJson(`/api/study-programs/${studyProgramId}/modulebook-tags`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });
}

async function deleteModulebookTag(tagId) {
    await requestJson(`/api/modulebook-tags/${tagId}`, {
        method: "DELETE"
    });
}

async function restoreModulebookTag(tagId) {
    return requestJson(`/api/modulebook-tags/${tagId}/restore`, {
        method: "POST"
    });
}

async function loadChangeSetFieldChanges(changeSetId) {
    return requestJson(`/api/change-sets/${changeSetId}/field-changes`);
}

async function restoreBeforeChangeSet(changeSetId, force = false) {
    return requestJson(`/api/change-sets/${changeSetId}/restore-before?force=${force ? "true" : "false"}`, {
        method: "POST"
    });
}

async function resetToInitialImport(changeSetId) {
    return requestJson(`/api/change-sets/${changeSetId}/reset-to-initial-import`, {
        method: "POST"
    });
}

async function loadModuleFieldChanges(moduleId) {
    return requestJson(`/api/modules/${moduleId}/field-changes`);
}

async function loadTeachingForms() {
    return requestJson("/api/teaching-forms");
}

async function updateModule(moduleId, request) {
    return requestJson(`/api/modules/${moduleId}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });
}

async function createModule(request) {
    return requestJson("/api/modules", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });
}

async function deleteModule(moduleId) {
    await requestJson(`/api/modules/${moduleId}`, {
        method: "DELETE"
    });
}

async function normalizePdf(formData) {
    return requestJson("/api/pdf-import/normalize", {
        method: "POST",
        body: formData
    });
}

async function saveImportedModulesRequest(request) {
    return requestJson("/api/pdf-import/modules", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function numberOrNull(value) {
    return value === "" ? null : Number(value);
}
