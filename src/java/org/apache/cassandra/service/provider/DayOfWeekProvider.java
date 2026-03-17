package org.apache.cassandra.service.provider;

import java.time.LocalDate;

import org.apache.cassandra.service.EnvironmentAttributeProvider;

public class DayOfWeekProvider implements EnvironmentAttributeProvider
{
    @Override
    public String getAttributeName()
    {
        return "day_of_the_week";
    }

    @Override
    public String getValue()
    {
        return LocalDate.now().getDayOfWeek().toString();
    }
}
