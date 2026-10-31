package com.smarthome.dao;

import com.smarthome.domain.Device;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for managing IoT device entities.
 */
public interface DeviceDao {

    /**
     * Persists a device entity. If the device exists, updates its metadata.
     *
     * @param device the device to save
     * @return the persisted device
     */
    Device save(Device device);

    /**
     * Retrieves a device by its unique identifier, including its current state map.
     *
     * @param id unique identifier of the device
     * @return Optional containing the Device if found, or empty Optional
     */
    Optional<Device> findById(String id);

    /**
     * Retrieves all devices registered in the system.
     *
     * @return list of all devices
     */
    List<Device> findAll();

    /**
     * Retrieves all devices located within a specified room.
     *
     * @param room room or zone name
     * @return list of matching devices
     */
    List<Device> findByRoom(String room);

    /**
     * Deletes a device by its identifier. Associated state and cascades are removed.
     *
     * @param id identifier of the device to delete
     * @return true if the device was deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Checks if a device with the given identifier exists.
     *
     * @param id device identifier
     * @return true if exists, false otherwise
     */
    boolean existsById(String id);
}

