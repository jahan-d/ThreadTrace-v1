package com.threadtrace.event;

public enum LotEventType {

    RECEIVED,

    CUT,
    SEW,
    WASH,
    DYE,
    FINISH,

    QUALITY_CHECK_PASSED,
    QUALITY_CHECK_FAILED,

    PACK,
    SHIP,

    TRANSFORMATION_INPUT,
    TRANSFORMATION_OUTPUT,

    ARCHIVE
}
