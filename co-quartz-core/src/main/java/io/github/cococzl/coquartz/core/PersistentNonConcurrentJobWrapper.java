package io.github.cococzl.coquartz.core;

import org.quartz.PersistJobDataAfterExecution;

/** Selected only when the business Job explicitly requests persistent JobDataMap updates. */
@PersistJobDataAfterExecution
public class PersistentNonConcurrentJobWrapper extends NonConcurrentJobWrapper { }
