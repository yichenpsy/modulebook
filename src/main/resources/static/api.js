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

async function loadModuleVersions(moduleId) {
    return requestJson(`/api/modules/${moduleId}/versions`);
}

async function loadStudyProgramHistory(studyProgramId) {
    return requestJson(`/api/study-programs/${studyProgramId}/history`);
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

async function loadChangeSetFieldChanges(changeSetId) {
    return requestJson(`/api/change-sets/${changeSetId}/field-changes`);
}

async function loadModuleFieldChanges(moduleId) {
    return requestJson(`/api/modules/${moduleId}/field-changes`);
}

async function restoreModuleVersion(moduleId, versionNumber) {
    return requestJson(`/api/modules/${moduleId}/versions/${versionNumber}/restore`, {
        method: "POST"
    });
}

async function restoreDeletedModuleVersion(versionId) {
    return requestJson(`/api/module-versions/${versionId}/restore-deleted`, {
        method: "POST"
    });
}

async function compareModuleVersions(moduleId, fromVersionNumber, toVersionNumber) {
    return requestJson(
        `/api/modules/${moduleId}/versions/compare?from=${fromVersionNumber}&to=${toVersionNumber}`
    );
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
