package com.smarthome.ui;

import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.service.RuleValidationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dependency Graph view rendering a visual node/edge DAG canvas of active automation rules.
 */
public class DependencyGraphView extends VBox {

    private final RuleValidationService ruleValidationService;
    private final Pane graphCanvas = new Pane();
    private final ScrollPane scrollPane;

    public DependencyGraphView(RuleValidationService ruleValidationService) {
        this.ruleValidationService = Objects.requireNonNull(ruleValidationService, "ruleValidationService cannot be null");

        setSpacing(12);
        setPadding(new Insets(16));
        getStyleClass().add("graph-view");

        buildHeader();

        graphCanvas.getStyleClass().add("graph-canvas");
        graphCanvas.setMinSize(1200, 800);

        scrollPane = new ScrollPane(graphCanvas);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);
        renderGraph(Collections.emptyList());
    }

    private void buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox textGroup = new VBox(2);
        Label title = new Label("Rule Dependency Graph (DAG)");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label("Visual map of dependency edges: Rule A -> Rule B indicates A's actuations trigger B's conditions.");
        subtitle.getStyleClass().add("section-subtitle");
        textGroup.getChildren().addAll(title, subtitle);
        HBox.setHgrow(textGroup, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Graph");
        refreshBtn.getStyleClass().add("btn-neutral");
        refreshBtn.setOnAction(e -> renderGraph(Collections.emptyList()));

        header.getChildren().addAll(textGroup, refreshBtn);
        getChildren().add(header);
    }

    /**
     * Renders the dependency graph of all enabled rules, optionally highlighting a cycle path.
     *
     * @param cyclePathToHighlight optional list of rule IDs forming a detected cycle to highlight
     */
    public void renderGraph(List<String> cyclePathToHighlight) {
        graphCanvas.getChildren().clear();

        List<Rule> enabledRules = ruleValidationService.findAllRules().stream()
                .filter(Rule::isEnabled)
                .toList();

        if (enabledRules.isEmpty()) {
            Label emptyLabel = new Label("No active rules configured. Build and enable rules to visualize the DAG.");
            emptyLabel.setLayoutX(40);
            emptyLabel.setLayoutY(40);
            emptyLabel.getStyleClass().add("placeholder-label");
            graphCanvas.getChildren().add(emptyLabel);
            return;
        }

        // Build adjacency map: Rule A -> Rule B if A.writeSet intersects B.readSet
        Map<String, Set<String>> edges = new HashMap<>();
        Map<String, Rule> rulesById = new HashMap<>();
        for (Rule r : enabledRules) {
            rulesById.put(r.getId(), r);
            edges.put(r.getId(), new HashSet<>());
        }

        for (Rule a : enabledRules) {
            Set<DeviceStateKey> writeSetA = a.getWriteSet();
            for (Rule b : enabledRules) {
                if (a.getId().equals(b.getId())) continue;
                for (DeviceStateKey readKeyB : b.getReadSet()) {
                    if (writeSetA.contains(readKeyB)) {
                        edges.get(a.getId()).add(b.getId());
                        break;
                    }
                }
            }
        }

        // Compute layout coordinates (grouping nodes in columns/tiers)
        Map<String, Double> nodeX = new HashMap<>();
        Map<String, Double> nodeY = new HashMap<>();

        double startX = 60;
        double startY = 60;
        double colWidth = 240;
        double rowHeight = 120;

        // Group by PriorityTier
        Map<PriorityTier, List<Rule>> byTier = new HashMap<>();
        for (PriorityTier t : PriorityTier.values()) {
            byTier.put(t, new ArrayList<>());
        }
        for (Rule r : enabledRules) {
            byTier.get(r.getTier()).add(r);
        }

        int col = 0;
        for (PriorityTier tier : PriorityTier.values()) {
            List<Rule> tierRules = byTier.get(tier);
            int row = 0;
            for (Rule r : tierRules) {
                nodeX.put(r.getId(), startX + (col * colWidth));
                nodeY.put(r.getId(), startY + (row * rowHeight));
                row++;
            }
            if (!tierRules.isEmpty()) {
                col++;
            }
        }

        Set<String> cycleSet = new HashSet<>(cyclePathToHighlight != null ? cyclePathToHighlight : Collections.emptyList());

        // 1. Draw directed edges first so they sit underneath nodes
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            String fromId = entry.getKey();
            Double fromX = nodeX.get(fromId);
            Double fromY = nodeY.get(fromId);
            if (fromX == null || fromY == null) continue;

            for (String toId : entry.getValue()) {
                Double toX = nodeX.get(toId);
                Double toY = nodeY.get(toId);
                if (toX == null || toY == null) continue;

                boolean isCycleEdge = cycleSet.contains(fromId) && cycleSet.contains(toId);
                drawArrow(fromX + 80, fromY + 30, toX + 10, toY + 30, isCycleEdge);
            }
        }

        // 2. Draw nodes (rule cards)
        for (Rule r : enabledRules) {
            Double x = nodeX.get(r.getId());
            Double y = nodeY.get(r.getId());
            if (x == null || y == null) continue;

            boolean isCycleNode = cycleSet.contains(r.getId());
            Pane nodeBox = createNodeBox(r, isCycleNode);
            nodeBox.setLayoutX(x);
            nodeBox.setLayoutY(y);
            graphCanvas.getChildren().add(nodeBox);
        }
    }

    private Pane createNodeBox(Rule rule, boolean highlightConflict) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setPrefWidth(160);
        box.setAlignment(Pos.CENTER_LEFT);

        Rectangle bg = new Rectangle(160, 60);
        bg.setArcWidth(10);
        bg.setArcHeight(10);

        if (highlightConflict) {
            bg.setFill(Color.web("#ffebee"));
            bg.setStroke(Color.web("#d32f2f"));
            bg.setStrokeWidth(2.5);
        } else {
            bg.setFill(Color.web("#ffffff"));
            Color strokeColor = switch (rule.getTier()) {
                case SAFETY -> Color.web("#e53935");
                case COMFORT -> Color.web("#fb8c00");
                case ENERGY -> Color.web("#43a047");
            };
            bg.setStroke(strokeColor);
            bg.setStrokeWidth(1.8);
        }

        Text nameText = new Text(truncate(rule.getName(), 18));
        nameText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        Text tierText = new Text(rule.getTier().name() + " (Order " + rule.getTieBreakOrder() + ")");
        tierText.setFont(Font.font("Segoe UI", 10));
        tierText.setFill(Color.web("#666666"));

        box.getChildren().addAll(nameText, tierText);

        StackPane stack = new StackPane(bg, box);
        stack.setAlignment(Pos.CENTER);
        return stack;
    }

    private void drawArrow(double startX, double startY, double endX, double endY, boolean isCycle) {
        Line line = new Line(startX, startY, endX, endY);
        Color color = isCycle ? Color.web("#d32f2f") : Color.web("#1976d2");
        line.setStroke(color);
        line.setStrokeWidth(isCycle ? 2.5 : 1.5);

        // Arrow head
        double arrowLength = 8.0;
        double arrowWidth = 5.0;
        double dx = endX - startX;
        double dy = endY - startY;
        double angle = Math.atan2(dy, dx);

        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        double x1 = endX - arrowLength * cos + arrowWidth * sin;
        double y1 = endY - arrowLength * sin - arrowWidth * cos;

        double x2 = endX - arrowLength * cos - arrowWidth * sin;
        double y2 = endY - arrowLength * sin + arrowWidth * cos;

        Polygon arrowHead = new Polygon();
        arrowHead.getPoints().addAll(
                endX, endY,
                x1, y1,
                x2, y2
        );
        arrowHead.setFill(color);

        graphCanvas.getChildren().addAll(line, arrowHead);
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 2) + "..";
    }
}

