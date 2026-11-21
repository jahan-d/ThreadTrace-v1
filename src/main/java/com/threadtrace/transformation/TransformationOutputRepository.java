package com.threadtrace.transformation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransformationOutputRepository
        extends JpaRepository<TransformationOutput, Long> {

    List<TransformationOutput> findByTransformation_Id(Long transformationId);

    List<TransformationOutput> findByOutputLot_Id(Long lotId);
}
