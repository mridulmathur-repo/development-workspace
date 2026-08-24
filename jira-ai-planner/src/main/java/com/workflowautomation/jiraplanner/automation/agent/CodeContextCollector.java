package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.config.AiProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class CodeContextCollector {
    private static final Set<String> EXTENSIONS = Set.of("java", "jsx", "js", "tsx", "ts", "css", "html", "xml", "yml", "yaml", "json", "md");
    private static final Set<String> EXCLUDED = Set.of(".git", "target", "node_modules", "data", ".idea", "dist", "build");
    private final AiProperties properties;

    public CodeContextCollector(AiProperties properties) { this.properties = properties; }

    public String collect(Path projectRoot, AgentAssignment assignment) {
        List<String> terms = Stream.of(assignment.storySummary(), assignment.storyDescription(), assignment.developmentTaskDescription())
                .filter(value -> value != null)
                .flatMap(value -> Stream.of(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")))
                .filter(value -> value.length() >= 4).distinct().limit(30).toList();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Candidate> candidates = paths.filter(Files::isRegularFile)
                    .filter(path -> allowed(projectRoot, path))
                    .map(path -> candidate(projectRoot, path, terms)).filter(candidate -> candidate != null)
                    .sorted(Comparator.comparingInt(Candidate::score).reversed().thenComparing(candidate -> candidate.relative().toString()))
                    .limit(Math.max(1, properties.getCodeMaxFiles())).toList();
            StringBuilder context = new StringBuilder();
            int maximum = Math.max(2000, properties.getCodeMaxContextChars());
            for (Candidate candidate : candidates) {
                String section = "\n--- FILE: DemoMart/" + candidate.relative().toString().replace('\\', '/') + " ---\n" + candidate.content() + "\n";
                if (context.length() + section.length() > maximum) break;
                context.append(section);
            }
            if (context.isEmpty()) throw new IllegalStateException("No safe DemoMart source context could be collected");
            return context.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to collect DemoMart source context", exception);
        }
    }

    private Candidate candidate(Path root, Path path, List<String> terms) {
        try {
            if (Files.size(path) > 100_000) return null;
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String searchable = (root.relativize(path) + " " + content).toLowerCase(Locale.ROOT);
            int score = terms.stream().mapToInt(term -> searchable.contains(term) ? 2 : 0).sum();
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.equals("pom.xml") || name.equals("package.json") || name.equals("compose.yaml")) score += 5;
            return new Candidate(root.relativize(path), content, score);
        } catch (IOException ignored) { return null; }
    }

    private boolean allowed(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) if (EXCLUDED.contains(part.toString().toLowerCase(Locale.ROOT))) return false;
        String name = path.getFileName().toString();
        if (name.equalsIgnoreCase(".env") || name.toLowerCase(Locale.ROOT).contains("secret")) return false;
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private record Candidate(Path relative, String content, int score) { }
}
