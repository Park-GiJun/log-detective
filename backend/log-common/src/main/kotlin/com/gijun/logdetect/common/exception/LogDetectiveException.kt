package com.gijun.logdetect.common.exception

open class LogDetectiveException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class LogIngestException(
    message: String,
    cause: Throwable? = null,
) : LogDetectiveException(message, cause)

class DetectionException(
    message: String,
    cause: Throwable? = null,
) : LogDetectiveException(message, cause)

class AlertDispatchException(
    message: String,
    cause: Throwable? = null,
) : LogDetectiveException(message, cause)

class RuleNotFoundException(
    ruleId: String,
) : LogDetectiveException("Detection rule not found: $ruleId")
