package com.smarthome.ui;

import com.smarthome.domain.LogicalOperator;
import com.smarthome.domain.Operator;


/**
 * Node representation used in the Condition TreeView within the Rule Builder.
 */
public class ConditionTreeNode {

    public enum NodeType {
        GROUP,
        LEAF
    }

    private final NodeType nodeType;

    // Group properties
    private LogicalOperator logicalOperator;

    // Leaf properties
    private String deviceId;
    private String stateKey;
    private Operator operator;
    private String compareValue;

    public static ConditionTreeNode createGroup(LogicalOperator op) {
        ConditionTreeNode node = new ConditionTreeNode(NodeType.GROUP);
        node.logicalOperator = op != null ? op : LogicalOperator.AND;
        return node;
    }

    public static ConditionTreeNode createLeaf(String deviceId, String stateKey, Operator operator, String value) {
        ConditionTreeNode node = new ConditionTreeNode(NodeType.LEAF);
        node.deviceId = deviceId != null ? deviceId : "";
        node.stateKey = stateKey != null ? stateKey : "";
        node.operator = operator != null ? operator : Operator.EQ;
        node.compareValue = value != null ? value : "";
        return node;
    }

    private ConditionTreeNode(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean isGroup() {
        return nodeType == NodeType.GROUP;
    }

    public boolean isLeaf() {
        return nodeType == NodeType.LEAF;
    }

    public LogicalOperator getLogicalOperator() {
        return logicalOperator;
    }

    public void setLogicalOperator(LogicalOperator logicalOperator) {
        this.logicalOperator = logicalOperator;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getStateKey() {
        return stateKey;
    }

    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getCompareValue() {
        return compareValue;
    }

    public void setCompareValue(String compareValue) {
        this.compareValue = compareValue;
    }

    @Override
    public String toString() {
        if (isGroup()) {
            return "GROUP (" + logicalOperator + ")";
        } else {
            return (deviceId.isBlank() ? "<device>" : deviceId)
                    + "." + (stateKey.isBlank() ? "<key>" : stateKey)
                    + " " + operator
                    + " '" + compareValue + "'";
        }
    }
}

