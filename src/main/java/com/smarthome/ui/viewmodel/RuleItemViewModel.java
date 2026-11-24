package com.smarthome.ui.viewmodel;

import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ViewModel encapsulating a rule for the Rules TableView with editable bindings.
 */
public class RuleItemViewModel {

    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<PriorityTier> tier = new SimpleObjectProperty<>();
    private final IntegerProperty tieBreakOrder = new SimpleIntegerProperty();
    private final BooleanProperty enabled = new SimpleBooleanProperty();
    private final StringProperty lastFired = new SimpleStringProperty("Never");
    private final StringProperty readSetSummary = new SimpleStringProperty();
    private final StringProperty writeSetSummary = new SimpleStringProperty();

    private Rule rule;

    public RuleItemViewModel(Rule rule) {
        setRule(rule);
    }

    public void setRule(Rule rule) {
        this.rule = Objects.requireNonNull(rule, "rule cannot be null");
        this.id.set(rule.getId());
        this.name.set(rule.getName());
        this.tier.set(rule.getTier());
        this.tieBreakOrder.set(rule.getTieBreakOrder());
        this.enabled.set(rule.isEnabled());

        Set<DeviceStateKey> readSet = rule.getReadSet();
        this.readSetSummary.set(readSet.stream()
                .map(DeviceStateKey::toString)
                .collect(Collectors.joining(", ")));

        Set<DeviceStateKey> writeSet = rule.getWriteSet();
        this.writeSetSummary.set(writeSet.stream()
                .map(DeviceStateKey::toString)
                .collect(Collectors.joining(", ")));
    }

    public void updateLastFired(String timestamp) {
        this.lastFired.set(timestamp);
    }

    public Rule getRule() {
        return rule;
    }

    public String getId() {
        return id.get();
    }

    public StringProperty idProperty() {
        return id;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public PriorityTier getTier() {
        return tier.get();
    }

    public ObjectProperty<PriorityTier> tierProperty() {
        return tier;
    }

    public int getTieBreakOrder() {
        return tieBreakOrder.get();
    }

    public IntegerProperty tieBreakOrderProperty() {
        return tieBreakOrder;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public String getLastFired() {
        return lastFired.get();
    }

    public StringProperty lastFiredProperty() {
        return lastFired;
    }

    public String getReadSetSummary() {
        return readSetSummary.get();
    }

    public StringProperty readSetSummaryProperty() {
        return readSetSummary;
    }

    public String getWriteSetSummary() {
        return writeSetSummary.get();
    }

    public StringProperty writeSetSummaryProperty() {
        return writeSetSummary;
    }
}

