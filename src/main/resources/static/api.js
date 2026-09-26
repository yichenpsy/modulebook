async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();

    if (!response.ok) {
        throw new Error(formatHttpError(response, text));
    }

    return text ? JSON.parse(text) : null;
}

function formatHttpError(response, text) {
    const body = parseJsonOrNull(text);

    if (!body) {
        return text || `${response.status} ${response.statusText}`;
    }

    const statusText = body.error || response.statusText || "Request failed";
    const detail = body.message || body.detail;
    const path = body.path ? ` (${body.path})` : "";

    if (detail && detail !== "No message available") {
        return `${response.status} ${statusText}: ${detail}${path}`;
    }

    return `${response.status} ${statusText}${path}`;
}

function parseJsonOrNull(text) {
    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch (error) {
        return null;
    }
}

async function loadModulebooks() {
    return requestJson("/api/modulebooks");
}

async function createModulebook(request) {
    return requestJson("/api/modulebooks", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });
}

async function deleteModulebook(modulebookId) {
    await requestJson(`/api/modulebooks/${modulebookId}`, {
        method: "DELETE"
    });
}

async function loadAllModules() {
    return requestJson("/api/modules");
}

async function loadModulesForModulebook(modulebookId) {
    return requestJson(`/api/modulebooks/${modulebookId}/modules`);
}

async function loadModule(moduleId) {
    return requestJson(`/api/modules/${moduleId}`);
}

async function loadModulebookChangeSets(modulebookId) {
    return requestJson(`/api/modulebooks/${modulebookId}/change-sets`);
}

async function loadModulebookVersions(modulebookId) {
    return requestJson(`/api/modulebooks/${modulebookId}/modulebook-versions`);
}

async function loadModulebookVersion(versionId) {
    return requestJson(`/api/modulebook-versions/${versionId}`);
}

async function createModulebookVersion(modulebookId, request) {
    return requestJson(`/api/modulebooks/${modulebookId}/modulebook-versions`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            versionMessage: request.versionMessage ?? request.tagMessage,
            modulebookReleaseDate: request.modulebookReleaseDate,
            studyExaminationRegulationsDate: request.studyExaminationRegulationsDate
        })
    });
}

async function deleteModulebookVersion(versionId) {
    await requestJson(`/api/modulebook-versions/${versionId}`, {
        method: "DELETE"
    });
}

async function restoreModulebookVersion(versionId) {
    return requestJson(`/api/modulebook-versions/${versionId}/restore`, {
        method: "POST"
    });
}

async function loadChangeSetModuleChanges(changeSetId) {
    return requestJson(`/api/change-sets/${changeSetId}/module-changes`);
}

async function loadChangeSetFieldChanges(changeSetId) {
    return loadChangeSetModuleChanges(changeSetId);
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

async function loadModuleChanges(moduleId) {
    return requestJson(`/api/modules/${moduleId}/module-changes`);
}

async function loadModuleFieldChanges(moduleId) {
    return loadModuleChanges(moduleId);
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

async function updateModuleStatus(moduleId, moduleStatus) {
    return requestJson(`/api/modules/${moduleId}/status`, {
        method: "PATCH",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ moduleStatus })
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

async function deleteModule(moduleId, confirmation) {
    await requestJson(`/api/modules/${moduleId}?confirmation=${encodeURIComponent(confirmation)}`, {
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

function changePathOf(change) {
    return change.changePath ?? change.fieldPath;
}
