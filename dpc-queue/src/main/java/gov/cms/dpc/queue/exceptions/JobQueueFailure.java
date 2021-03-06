package gov.cms.dpc.queue.exceptions;

import java.util.UUID;

public class JobQueueFailure extends RuntimeException {

    public static final long serialVersionUID = 42L;

    public JobQueueFailure(UUID jobID, String message) {
        super(String.format("Operation on Job(%s) failed for reason: %s", jobID, message));
    }

    public JobQueueFailure(UUID jobId, Throwable t) {
        super(String.format("Operation on Job(%s) failed.", jobId), t);
    }

    public JobQueueFailure(UUID jobId, String message, Throwable throwable) {
        super(String.format("Operation on Job(%s) failed for reason: %s", jobId, message), throwable);
    }
}
