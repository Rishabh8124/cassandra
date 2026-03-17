package org.apache.cassandra.service;

/**
 * An interface for providing dynamic environment attributes, which can be used for,
 * among other things, attribute-based authorization decisions.
 * Implementations of this interface can be discovered via {@link java.util.ServiceLoader}.
 */
public interface EnvironmentAttributeProvider
{
    /**
     * Returns the unique name of the attribute.
     * This name is used for configuration and retrieval.
     * @return The name of the attribute (e.g., "day_of_the_week").
     */
    String getAttributeName();

    /**
     * Retrieves the current value of the attribute.
     * This method may be called frequently, so implementations should be efficient.
     * @return The current value of the attribute as a String.
     */
    String getValue();
}
