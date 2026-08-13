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

async function loadAllModules() {
    return requestJson("/api/modules");
}

async function loadModulesForStudyProgram(studyProgramId) {
    return requestJson(`/api/study-programs/${studyProgramId}/modules`);
}

async function loadModule(moduleId) {
    return requestJson(`/api/modules/${moduleId}`);
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
