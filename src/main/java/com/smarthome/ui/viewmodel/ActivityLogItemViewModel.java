package com.smarthome.ui.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ViewModel for presenting live engine execution, priority overrides, and error events.
 * Implements {@link Comparable} to provide a deterministic, ascending chronological order
 * based on timestamp and sequence arrival.
 */
public class ActivityLogItemViewModel implements Comparable<ActivityLogItemViewModel> {

    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final Instant rawTimestamp;
    private final long sequenceNumber;

    private final StringProperty timestamp = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final StringProperty summary = new SimpleStringProperty();
    private final StringProperty details = new SimpleStringProperty();

    public ActivityLogItemViewModel(Instant time, String type, String summary, String details) {
        this.rawTimestamp = time != null ? time : Instant.now();
        this.sequenceNumber = SEQUENCE_GENERATOR.incrementAndGet();
        this.timestamp.set(TIME_FORMATTER.format(this.rawTimestamp));
        this.type.set(type != null ? type : "INFO");
        this.summary.set(summary != null ? summary : "");
        this.details.set(details != null ? details : "");
    }

    public Instant getRawTimestamp() {
        return rawTimestamp;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getTimestamp() {
        return timestamp.get();
    }

    public StringProperty timestampProperty() {
        return timestamp;
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

    public String getSummary() {
        return summary.get();
    }

    public StringProperty summaryProperty() {
        return summary;
    }

    public String getDetails() {
        return details.get();
    }

    public StringProperty detailsProperty() {
        return details;
    }

    @Override
    public int compareTo(ActivityLogItemViewModel other) {
        if (other == null) return 1;
        int cmp = this.rawTimestamp.compareTo(other.rawTimestamp);
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(this.sequenceNumber, other.sequenceNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActivityLogItemViewModel that = (ActivityLogItemViewModel) o;
        return sequenceNumber == that.sequenceNumber && Objects.equals(rawTimestamp, that.rawTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawTimestamp, sequenceNumber);
    }
}
