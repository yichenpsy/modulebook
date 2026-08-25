package de.bht.modulebook.module.version;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// compare two snapshots
@Service
public class ModuleDiffService {
    private final ModuleVersionRepository versionRepository;
    private final JsonMapper jsonMapper;

    public ModuleDiffService(ModuleVersionRepository versionRepository, JsonMapper jsonMapper) {
        this.versionRepository = versionRepository;
        this.jsonMapper = jsonMapper;
    }

    public ModuleDiff compare(Long moduleId, int fromVersionNumber, int toVersionNumber) {
        ModuleVersion fromVersion = versionRepository.findByModuleIdAndVersionNumber(moduleId, fromVersionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "From version not found"));
        ModuleVersion toVersion = versionRepository.findByModuleIdAndVersionNumber(moduleId, toVersionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "To version not found"));

        try {
            JsonNode fromSnapshot = jsonMapper.readTree(fromVersion.getSnapshotJson());
            JsonNode toSnapshot = jsonMapper.readTree(toVersion.getSnapshotJson());
            List<ModuleDiff.FieldChange> changes = new ArrayList<>();

            compareNodes("", fromSnapshot, toSnapshot, changes);
            changes.sort(Comparator.comparing(ModuleDiff.FieldChange::getPath));

            return new ModuleDiff(fromVersionNumber, toVersionNumber, changes);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not compare module snapshots");
        }
    }

    private void compareNodes(String path, JsonNode oldNode, JsonNode newNode, List<ModuleDiff.FieldChange> changes) {
        if (sameValue(oldNode, newNode)) {
            return;
        }

        if (oldNode == null || newNode == null || oldNode.isValueNode() || newNode.isValueNode()) {
            addChangeIfDisplayValuesDiffer(path, oldNode, newNode, changes);
            return;
        }

        if (oldNode.isObject() && newNode.isObject()) {
            List<String> fieldNames = StreamSupport.stream(oldNode.propertyNames().spliterator(), false).toList();
            List<String> newFieldNames = StreamSupport.stream(newNode.propertyNames().spliterator(), false).toList();
            List<String> allFieldNames = new ArrayList<>(fieldNames);

            newFieldNames.stream()
                    .filter(fieldName -> !allFieldNames.contains(fieldName))
                    .forEach(allFieldNames::add);

            for (String fieldName : allFieldNames) {
                compareNodes(joinPath(path, fieldName), oldNode.get(fieldName), newNode.get(fieldName), changes);
            }
            return;
        }

        if (oldNode.isArray() && newNode.isArray()) {
            int maxSize = Math.max(oldNode.size(), newNode.size());
            for (int index = 0; index < maxSize; index++) {
                JsonNode oldChild = index < oldNode.size() ? oldNode.get(index) : null;
                JsonNode newChild = index < newNode.size() ? newNode.get(index) : null;
                compareNodes(path + "[" + index + "]", oldChild, newChild, changes);
            }
            return;
        }

        addChangeIfDisplayValuesDiffer(path, oldNode, newNode, changes);
    }

    private boolean sameValue(JsonNode oldNode, JsonNode newNode) {
        if (oldNode == null || newNode == null) {
            return oldNode == newNode;
        }

        return Objects.equals(oldNode, newNode);
    }

    private String displayValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        return node.toString();
    }

    private void addChangeIfDisplayValuesDiffer(
            String path,
            JsonNode oldNode,
            JsonNode newNode,
            List<ModuleDiff.FieldChange> changes
    ) {
        String oldDisplayValue = displayValue(oldNode);
        String newDisplayValue = displayValue(newNode);

        if (Objects.equals(
                normalizeForDisplayComparison(oldDisplayValue),
                normalizeForDisplayComparison(newDisplayValue)
        )) {
            return;
        }

        changes.add(new ModuleDiff.FieldChange(path, oldDisplayValue, newDisplayValue));
    }

    private String normalizeForDisplayComparison(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String joinPath(String parent, String child) {
        return parent.isBlank() ? child : parent + "." + child;
    }
}
