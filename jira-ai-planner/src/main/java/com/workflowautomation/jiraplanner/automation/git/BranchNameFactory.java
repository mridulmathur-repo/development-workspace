package com.workflowautomation.jiraplanner.automation.git;

import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class BranchNameFactory {

    public String forEpic(ProjectDefinition project, String epicKey, String epicSummary) {
        String safeKey = required(epicKey, "Epic key")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9-]", "-")
                .replaceAll("-+", "-");
        String slug = Normalizer.normalize(required(epicSummary, "Epic summary"), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("-+$", "");
        }
        if (slug.isBlank()) {
            slug = "work";
        }
        return project.branchPrefix() + safeKey + "-" + slug;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
