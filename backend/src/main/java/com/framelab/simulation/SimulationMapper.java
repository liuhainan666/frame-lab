package com.framelab.simulation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SimulationMapper {

    SimulationRow find();

    SimulationRow lock();

    int consume(@Param("clearRemaining") boolean clearRemaining);

    int update(SimulationRow row);
}
