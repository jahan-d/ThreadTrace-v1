package com.threadtrace.transformation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransformationInputRepository
        extends JpaRepository<TransformationInput, Long> {

    List<TransformationInput> findByTransformation_Id(Long transformationId);

    List<TransformationInput> findByInputLot_Id(Long lotId);
}
